package com.conduit.chainservice.service

import com.conduit.chainservice.config.StateAwareCacheConfig.Companion.CONTRACT_INFO_IMMUTABLE_CACHE
import com.conduit.chainservice.config.StateAwareCacheConfig.Companion.CONTRACT_INFO_MUTABLE_CACHE
import com.conduit.chainservice.config.StateAwareCacheConfig.Companion.CONTRACT_STATE_IMMUTABLE_CACHE
import com.conduit.chainservice.config.StateAwareCacheConfig.Companion.CONTRACT_STATE_MUTABLE_CACHE
import com.conduit.chainservice.config.StateAwareCacheConfig.Companion.TRANSACTION_DATA_CACHE
import com.conduit.chainservice.escrow.models.ContractInfo
import com.conduit.chainservice.escrow.models.ContractStatus
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import java.math.BigInteger

class CacheInvalidationServiceTest {

    private lateinit var cacheManager: CacheManager
    private lateinit var level3ImmutableCache: Cache  // CONTRACT_INFO_IMMUTABLE_CACHE
    private lateinit var level3MutableCache: Cache    // CONTRACT_INFO_MUTABLE_CACHE
    private lateinit var level1ImmutableCache: Cache  // CONTRACT_STATE_IMMUTABLE_CACHE
    private lateinit var level1MutableCache: Cache    // CONTRACT_STATE_MUTABLE_CACHE
    private lateinit var level2Cache: Cache           // TRANSACTION_DATA_CACHE
    private lateinit var cacheInvalidationService: CacheInvalidationService

    @BeforeEach
    fun setUp() {
        cacheManager = mock()
        level3ImmutableCache = mock()
        level3MutableCache = mock()
        level1ImmutableCache = mock()
        level1MutableCache = mock()
        level2Cache = mock()
        cacheInvalidationService = CacheInvalidationService(cacheManager)
        
        // Setup default cache manager behavior for all cache levels
        whenever(cacheManager.getCache(CONTRACT_INFO_IMMUTABLE_CACHE)).thenReturn(level3ImmutableCache)
        whenever(cacheManager.getCache(CONTRACT_INFO_MUTABLE_CACHE)).thenReturn(level3MutableCache)
        whenever(cacheManager.getCache(CONTRACT_STATE_IMMUTABLE_CACHE)).thenReturn(level1ImmutableCache)
        whenever(cacheManager.getCache(CONTRACT_STATE_MUTABLE_CACHE)).thenReturn(level1MutableCache)
        whenever(cacheManager.getCache(TRANSACTION_DATA_CACHE)).thenReturn(level2Cache)
    }

    @Test
    fun `SELECTIVE INVALIDATION - invalidateContractCache should evict all cache levels for specific contract only`() {
        // Given
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val operationType = "createContract"
        val transactionHash = "0xabcdef123456"
        
        // Setup cache entries at all levels for this specific contract
        val contractInfo = createTestContractInfo(contractAddress)
        whenever(level3ImmutableCache.get(contractAddress)).thenReturn(Cache.ValueWrapper { contractInfo })
        whenever(level3ImmutableCache.get("status:$contractAddress")).thenReturn(Cache.ValueWrapper { ContractStatus.CLAIMED })
        whenever(level3MutableCache.get(contractAddress)).thenReturn(Cache.ValueWrapper { contractInfo })
        whenever(level3MutableCache.get("status:$contractAddress")).thenReturn(Cache.ValueWrapper { ContractStatus.ACTIVE })
        whenever(level1ImmutableCache.get(contractAddress)).thenReturn(Cache.ValueWrapper { mapOf("funded" to true) })
        whenever(level1MutableCache.get(contractAddress)).thenReturn(Cache.ValueWrapper { mapOf("funded" to true) })
        whenever(level2Cache.get(contractAddress)).thenReturn(Cache.ValueWrapper { "transactionData" })

        // When - SELECTIVE INVALIDATION: Only invalidate this specific contract
        cacheInvalidationService.invalidateContractCache(contractAddress, operationType, transactionHash)

        // Then - VERIFY SELECTIVE INVALIDATION: Only this contract's cache entries are evicted
        // Level 3 - Contract info and status entries (both immutable and mutable)
        verify(level3ImmutableCache).evict(contractAddress)
        verify(level3ImmutableCache).evict("status:$contractAddress")
        verify(level3MutableCache).evict(contractAddress)
        verify(level3MutableCache).evict("status:$contractAddress")
        
        // Level 1 - State data (both immutable and mutable)
        verify(level1ImmutableCache).evict(contractAddress)
        verify(level1MutableCache).evict(contractAddress)
        
        // Level 2 - Transaction data
        verify(level2Cache).evict(contractAddress)
        
        // CRITICAL: Verify no cache.clear() calls that would invalidate ALL contracts
        verify(level3ImmutableCache, never()).clear()
        verify(level3MutableCache, never()).clear()
        verify(level1ImmutableCache, never()).clear()
        verify(level1MutableCache, never()).clear()
        verify(level2Cache, never()).clear()
    }

    @Test 
    fun `CRITICAL BUG FIX - selective invalidation should NOT affect other contracts in cache`() {
        // Given - Setup scenario with multiple contracts in cache (simulating batch query)
        val targetContract = "0x1111111111111111111111111111111111111111"
        val otherContract1 = "0x2222222222222222222222222222222222222222"
        val otherContract2 = "0x3333333333333333333333333333333333333333"
        
        // All contracts are cached at all levels
        listOf(targetContract, otherContract1, otherContract2).forEach { address ->
            whenever(level3ImmutableCache.get(address)).thenReturn(Cache.ValueWrapper { createTestContractInfo(address) })
            whenever(level3ImmutableCache.get("status:$address")).thenReturn(Cache.ValueWrapper { ContractStatus.CLAIMED })
            whenever(level3MutableCache.get(address)).thenReturn(Cache.ValueWrapper { createTestContractInfo(address) })
            whenever(level3MutableCache.get("status:$address")).thenReturn(Cache.ValueWrapper { ContractStatus.ACTIVE })
            whenever(level1ImmutableCache.get(address)).thenReturn(Cache.ValueWrapper { mapOf("funded" to true) })
            whenever(level1MutableCache.get(address)).thenReturn(Cache.ValueWrapper { mapOf("funded" to true) })
            whenever(level2Cache.get(address)).thenReturn(Cache.ValueWrapper { "transactionData" })
        }

        // When - Only one contract has a state change (dispute raised)
        cacheInvalidationService.invalidateContractCache(targetContract, "raiseDispute", "0xtxhash")

        // Then - CRITICAL: Only target contract should be invalidated
        verify(level3ImmutableCache).evict(targetContract)
        verify(level3ImmutableCache).evict("status:$targetContract")
        verify(level3MutableCache).evict(targetContract)
        verify(level3MutableCache).evict("status:$targetContract")
        verify(level1ImmutableCache).evict(targetContract)
        verify(level1MutableCache).evict(targetContract)
        verify(level2Cache).evict(targetContract)
        
        // CRITICAL: Other contracts should remain untouched (no eviction calls for them)
        verify(level3ImmutableCache, never()).evict(otherContract1)
        verify(level3ImmutableCache, never()).evict("status:$otherContract1")
        verify(level3MutableCache, never()).evict(otherContract1)
        verify(level3MutableCache, never()).evict("status:$otherContract1")
        verify(level1ImmutableCache, never()).evict(otherContract1)
        verify(level1MutableCache, never()).evict(otherContract1)
        verify(level2Cache, never()).evict(otherContract1)
        
        verify(level3ImmutableCache, never()).evict(otherContract2)
        verify(level3ImmutableCache, never()).evict("status:$otherContract2")
        verify(level3MutableCache, never()).evict(otherContract2)
        verify(level3MutableCache, never()).evict("status:$otherContract2")
        verify(level1ImmutableCache, never()).evict(otherContract2)
        verify(level1MutableCache, never()).evict(otherContract2)
        verify(level2Cache, never()).evict(otherContract2)
        
        // CRITICAL: No global cache clearing that would affect all contracts
        verify(level3ImmutableCache, never()).clear()
        verify(level3MutableCache, never()).clear()
        verify(level1ImmutableCache, never()).clear()
        verify(level1MutableCache, never()).clear()
        verify(level2Cache, never()).clear()
    }

    @Test
    fun `invalidateContractCache should handle missing cache entries gracefully`() {
        // Given
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val operationType = "depositFunds"
        
        whenever(level3ImmutableCache.get(contractAddress)).thenReturn(null)
        whenever(level3ImmutableCache.get("status:$contractAddress")).thenReturn(null)
        whenever(level1ImmutableCache.get(contractAddress)).thenReturn(null)
        whenever(level2Cache.get(contractAddress)).thenReturn(null)

        // When
        cacheInvalidationService.invalidateContractCache(contractAddress, operationType)

        // Then - No evictions should occur since no entries exist
        verify(level3ImmutableCache, never()).evict(any())
        verify(level1ImmutableCache, never()).evict(any())
        verify(level2Cache, never()).evict(any())
    }

    @Test
    fun `invalidateContractCache should handle cache manager returning null cache`() {
        // Given
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val operationType = "raiseDispute"
        
        whenever(cacheManager.getCache(CONTRACT_INFO_IMMUTABLE_CACHE)).thenReturn(null)
        whenever(cacheManager.getCache(CONTRACT_STATE_IMMUTABLE_CACHE)).thenReturn(null)
        whenever(cacheManager.getCache(TRANSACTION_DATA_CACHE)).thenReturn(null)

        // When - should not throw exception
        cacheInvalidationService.invalidateContractCache(contractAddress, operationType)

        // Then - no cache operations should be attempted on any level
        verifyNoInteractions(level3ImmutableCache)
        verifyNoInteractions(level1ImmutableCache)
        verifyNoInteractions(level2Cache)
    }

    @Test
    fun `invalidateContractCache should handle cache exceptions gracefully`() {
        // Given
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val operationType = "claimFunds"
        
        whenever(level3ImmutableCache.get(contractAddress)).thenThrow(RuntimeException("Cache error"))

        // When - should not throw exception
        cacheInvalidationService.invalidateContractCache(contractAddress, operationType)

        // Then - method completes without throwing, and Level 3 cache get was attempted
        verify(level3ImmutableCache).get(contractAddress)
    }

    @Test
    fun `invalidateMultipleContractCache should process all contracts`() {
        // Given
        val contractAddresses = listOf(
            "0x1234567890abcdef1234567890abcdef12345678",
            "0x9876543210fedcba9876543210fedcba98765432",
            "0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef"
        )
        val operationType = "batchOperation"
        val transactionHash = "0xbatch123"
        
        // Setup cache entries
        contractAddresses.forEach { address ->
            whenever(level3ImmutableCache.get(address)).thenReturn(Cache.ValueWrapper { createTestContractInfo(address) })
            whenever(level3ImmutableCache.get("status:$address")).thenReturn(Cache.ValueWrapper { ContractStatus.ACTIVE })
        }

        // When
        cacheInvalidationService.invalidateMultipleContractCache(contractAddresses, operationType, transactionHash)

        // Then
        contractAddresses.forEach { address ->
            verify(level3ImmutableCache).evict(address)
            verify(level3ImmutableCache).evict("status:$address")
        }
    }

    @Test
    fun `invalidateMultipleContractCache should handle empty list`() {
        // Given
        val emptyList = emptyList<String>()
        val operationType = "emptyBatch"

        // When
        cacheInvalidationService.invalidateMultipleContractCache(emptyList, operationType)

        // Then - no cache operations should occur
        verifyNoInteractions(level3ImmutableCache)
    }

    @Test
    fun `invalidateMultipleContractCache should continue processing even if some fail`() {
        // Given
        val contractAddresses = listOf(
            "0x1234567890abcdef1234567890abcdef12345678",
            "0x9876543210fedcba9876543210fedcba98765432"
        )
        val operationType = "partialFailure"
        
        // Setup first contract to succeed
        whenever(level3ImmutableCache.get(contractAddresses[0])).thenReturn(Cache.ValueWrapper { createTestContractInfo(contractAddresses[0]) })
        whenever(level3ImmutableCache.get("status:${contractAddresses[0]}")).thenReturn(Cache.ValueWrapper { ContractStatus.ACTIVE })
        
        // Setup second contract to fail
        whenever(level3ImmutableCache.get(contractAddresses[1])).thenThrow(RuntimeException("Cache error"))

        // When - should not throw exception
        cacheInvalidationService.invalidateMultipleContractCache(contractAddresses, operationType)

        // Then - first contract should be processed successfully
        verify(level3ImmutableCache).evict(contractAddresses[0])
        verify(level3ImmutableCache).evict("status:${contractAddresses[0]}")
        
        // Second contract get operation should be attempted
        verify(level3ImmutableCache).get(contractAddresses[1])
    }

    @Test
    fun `invalidateAllContractCache should clear all cache entries`() {
        // Given
        val reason = "Emergency cache clear"

        // When
        cacheInvalidationService.invalidateAllContractCache(reason)

        // Then
        verify(level3ImmutableCache).clear()
    }

    @Test
    fun `invalidateAllContractCache should handle null cache gracefully`() {
        // Given
        val reason = "Cache not available"
        whenever(cacheManager.getCache(CONTRACT_INFO_IMMUTABLE_CACHE)).thenReturn(null)

        // When - should not throw exception
        cacheInvalidationService.invalidateAllContractCache(reason)

        // Then - no cache operations should be attempted
        verifyNoInteractions(level3ImmutableCache)
    }

    @Test
    fun `isCacheAvailable should return true when cache is available`() {
        // Given - cache is already mocked to return cache instance

        // When
        val result = cacheInvalidationService.isCacheAvailable()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isCacheAvailable should return false when cache is null`() {
        // Given
        whenever(cacheManager.getCache(CONTRACT_INFO_IMMUTABLE_CACHE)).thenReturn(null)

        // When
        val result = cacheInvalidationService.isCacheAvailable()

        // Then
        assertFalse(result)
    }

    @Test
    fun `isCacheAvailable should return false when exception occurs`() {
        // Given
        whenever(cacheManager.getCache(CONTRACT_INFO_IMMUTABLE_CACHE)).thenThrow(RuntimeException("Cache error"))

        // When
        val result = cacheInvalidationService.isCacheAvailable()

        // Then
        assertFalse(result)
    }

    @Test
    fun `getCacheStats should return stats for Caffeine cache with multi-level format`() {
        // Given - Only setup Level 3 cache for this test
        val caffeineCache = mock<com.github.benmanes.caffeine.cache.Cache<Any, Any>>()
        val stats = mock<com.github.benmanes.caffeine.cache.stats.CacheStats>()
        
        whenever(level3ImmutableCache.nativeCache).thenReturn(caffeineCache)
        whenever(caffeineCache.stats()).thenReturn(stats)
        whenever(stats.requestCount()).thenReturn(100L)
        whenever(stats.hitCount()).thenReturn(80L)
        whenever(stats.hitRate()).thenReturn(0.8)
        whenever(stats.missCount()).thenReturn(20L)
        whenever(stats.missRate()).thenReturn(0.2)
        whenever(stats.loadCount()).thenReturn(20L)
        whenever(stats.evictionCount()).thenReturn(5L)
        whenever(caffeineCache.estimatedSize()).thenReturn(50L)

        // When
        val result = cacheInvalidationService.getCacheStats()

        // Then - Should return multi-level stats format
        assertTrue(result.containsKey("level3_contractInfo_immutable"))
        
        @Suppress("UNCHECKED_CAST")
        val level3Stats = result["level3_contractInfo_immutable"] as Map<String, Any>
        assertEquals(100L, level3Stats["requestCount"])
        assertEquals(80L, level3Stats["hitCount"])
        assertEquals("80.0%", level3Stats["hitRate"])
        assertEquals(20L, level3Stats["missCount"])
        assertEquals(5L, level3Stats["evictionCount"])
        assertEquals(50L, level3Stats["estimatedSize"])
    }

    @Test
    fun `getCacheStats should handle non-Caffeine cache`() {
        // Given
        val nonCaffeineCache = "Not a Caffeine cache"
        whenever(level3ImmutableCache.nativeCache).thenReturn(nonCaffeineCache)

        // When
        val result = cacheInvalidationService.getCacheStats()

        // Then - Multi-level format should return status when cache stats not available
        assertEquals("No cache statistics available", result["status"])
    }

    @Test
    fun `getCacheStats should handle null cache`() {
        // Given
        whenever(cacheManager.getCache(CONTRACT_INFO_IMMUTABLE_CACHE)).thenReturn(null)
        whenever(cacheManager.getCache(CONTRACT_STATE_IMMUTABLE_CACHE)).thenReturn(null)
        whenever(cacheManager.getCache(TRANSACTION_DATA_CACHE)).thenReturn(null)

        // When
        val result = cacheInvalidationService.getCacheStats()

        // Then - Multi-level format should return status when no caches available
        assertEquals("No cache statistics available", result["status"])
    }

    @Test
    fun `getCacheStats should handle exceptions`() {
        // Given
        whenever(cacheManager.getCache(CONTRACT_INFO_IMMUTABLE_CACHE)).thenThrow(RuntimeException("Stats error"))

        // When
        val result = cacheInvalidationService.getCacheStats()

        // Then
        assertEquals("Stats error", result["error"])
    }

    @Test
    fun `getCacheStats should return multi-level cache statistics for selective invalidation monitoring`() {
        // Given - Setup Caffeine cache mocks for all three levels
        val level3CaffeineCache = mock<com.github.benmanes.caffeine.cache.Cache<Any, Any>>()
        val level1CaffeineCache = mock<com.github.benmanes.caffeine.cache.Cache<Any, Any>>()
        val level2CaffeineCache = mock<com.github.benmanes.caffeine.cache.Cache<Any, Any>>()
        
        val level3Stats = mock<com.github.benmanes.caffeine.cache.stats.CacheStats>()
        val level1Stats = mock<com.github.benmanes.caffeine.cache.stats.CacheStats>()
        val level2Stats = mock<com.github.benmanes.caffeine.cache.stats.CacheStats>()
        
        // Setup Level 3 (CONTRACT_INFO_IMMUTABLE_CACHE)
        whenever(level3ImmutableCache.nativeCache).thenReturn(level3CaffeineCache)
        whenever(level3CaffeineCache.stats()).thenReturn(level3Stats)
        whenever(level3Stats.requestCount()).thenReturn(100L)
        whenever(level3Stats.hitCount()).thenReturn(90L)
        whenever(level3Stats.hitRate()).thenReturn(0.9)
        whenever(level3Stats.missCount()).thenReturn(10L)
        whenever(level3Stats.evictionCount()).thenReturn(2L)
        whenever(level3CaffeineCache.estimatedSize()).thenReturn(30L)
        
        // Setup Level 1 (CONTRACT_STATE_IMMUTABLE_CACHE)
        whenever(level1ImmutableCache.nativeCache).thenReturn(level1CaffeineCache)
        whenever(level1CaffeineCache.stats()).thenReturn(level1Stats)
        whenever(level1Stats.requestCount()).thenReturn(80L)
        whenever(level1Stats.hitCount()).thenReturn(75L)
        whenever(level1Stats.hitRate()).thenReturn(0.9375)
        whenever(level1Stats.missCount()).thenReturn(5L)
        whenever(level1Stats.evictionCount()).thenReturn(1L)
        whenever(level1CaffeineCache.estimatedSize()).thenReturn(28L)
        
        // Setup Level 2 (TRANSACTION_DATA_CACHE)
        whenever(level2Cache.nativeCache).thenReturn(level2CaffeineCache)
        whenever(level2CaffeineCache.stats()).thenReturn(level2Stats)
        whenever(level2Stats.requestCount()).thenReturn(60L)
        whenever(level2Stats.hitCount()).thenReturn(58L)
        whenever(level2Stats.hitRate()).thenReturn(0.9667)
        whenever(level2Stats.missCount()).thenReturn(2L)
        whenever(level2Stats.evictionCount()).thenReturn(1L)
        whenever(level2CaffeineCache.estimatedSize()).thenReturn(29L)

        // When
        val result = cacheInvalidationService.getCacheStats()

        // Then - Verify multi-level cache statistics are returned
        assertTrue(result.containsKey("level3_contractInfo_immutable"))
        assertTrue(result.containsKey("level1_contractState_immutable"))
        assertTrue(result.containsKey("level2_transactionData"))
        
        @Suppress("UNCHECKED_CAST")
        val level3StatsResult = result["level3_contractInfo_immutable"] as Map<String, Any>
        assertEquals(100L, level3StatsResult["requestCount"])
        assertEquals(90L, level3StatsResult["hitCount"])
        assertEquals("90.0%", level3StatsResult["hitRate"])
        assertEquals(2L, level3StatsResult["evictionCount"])  // Key for selective invalidation monitoring
        assertEquals(30L, level3StatsResult["estimatedSize"])
        
        @Suppress("UNCHECKED_CAST")
        val level1StatsResult = result["level1_contractState_immutable"] as Map<String, Any>
        assertEquals("93.8%", level1StatsResult["hitRate"])
        assertEquals(1L, level1StatsResult["evictionCount"])
    }

    @Test
    fun `analyzeCacheEffectiveness should calculate selective invalidation effectiveness score`() {
        // Given - Mock high hit rate cache stats to simulate selective invalidation working
        val caffeineCache = mock<com.github.benmanes.caffeine.cache.Cache<Any, Any>>()
        val stats = mock<com.github.benmanes.caffeine.cache.stats.CacheStats>()
        
        whenever(level3ImmutableCache.nativeCache).thenReturn(caffeineCache)
        whenever(caffeineCache.stats()).thenReturn(stats)
        whenever(stats.requestCount()).thenReturn(100L)
        whenever(stats.hitCount()).thenReturn(90L)
        whenever(stats.hitRate()).thenReturn(0.9)
        whenever(stats.missCount()).thenReturn(10L)
        whenever(stats.evictionCount()).thenReturn(5L)  // Some selective evictions occurred
        whenever(caffeineCache.estimatedSize()).thenReturn(25L)  // Cache still has entries
        
        // When
        val result = cacheInvalidationService.analyzeCacheEffectiveness()
        
        // Then - Should indicate selective invalidation is working
        assertTrue(result.containsKey("level3_immutable_analysis"))
        assertTrue(result.containsKey("overallEffectivenessScore"))
        assertTrue(result.containsKey("selectiveInvalidationStatus"))
        
        @Suppress("UNCHECKED_CAST")
        val level3Analysis = result["level3_immutable_analysis"] as Map<String, Any>
        assertEquals("90.0%", level3Analysis["hitRate"])
        assertEquals(true, level3Analysis["selectiveInvalidationWorking"])
        assertEquals("Size: 25, Evictions: 5", level3Analysis["cacheUtilization"])
        
        val effectivenessScore = result["overallEffectivenessScore"] as Int
        assertTrue(effectivenessScore > 70, "Effectiveness score should be high with good hit rates")
        assertEquals("WORKING", result["selectiveInvalidationStatus"])
    }

    private fun createTestContractInfo(contractAddress: String): ContractInfo {
        return ContractInfo(
            contractAddress = contractAddress,
            buyer = "0xbuyer123",
            seller = "0xseller456",
            amount = BigInteger.valueOf(1000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract",
            funded = true,
            status = ContractStatus.ACTIVE,
            createdAt = Instant.now(),
                tokenAddress = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
            fundedAt = Instant.now()
        )
    }
}