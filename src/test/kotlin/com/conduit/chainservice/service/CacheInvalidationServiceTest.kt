package com.conduit.chainservice.service

import com.conduit.chainservice.config.CacheConfig.Companion.CONTRACT_INFO_CACHE
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
    private lateinit var cache: Cache
    private lateinit var cacheInvalidationService: CacheInvalidationService

    @BeforeEach
    fun setUp() {
        cacheManager = mock()
        cache = mock()
        cacheInvalidationService = CacheInvalidationService(cacheManager)
        
        // Setup default cache manager behavior
        whenever(cacheManager.getCache(CONTRACT_INFO_CACHE)).thenReturn(cache)
    }

    @Test
    fun `invalidateContractCache should evict contract info and status entries when present`() {
        // Given
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val operationType = "createContract"
        val transactionHash = "0xabcdef123456"
        
        val contractInfo = createTestContractInfo(contractAddress)
        whenever(cache.get(contractAddress)).thenReturn(Cache.ValueWrapper { contractInfo })
        whenever(cache.get("status:$contractAddress")).thenReturn(Cache.ValueWrapper { ContractStatus.ACTIVE })

        // When
        cacheInvalidationService.invalidateContractCache(contractAddress, operationType, transactionHash)

        // Then
        verify(cache).evict(contractAddress)
        verify(cache).evict("status:$contractAddress")
    }

    @Test
    fun `invalidateContractCache should handle missing cache entries gracefully`() {
        // Given
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val operationType = "depositFunds"
        
        whenever(cache.get(contractAddress)).thenReturn(null)
        whenever(cache.get("status:$contractAddress")).thenReturn(null)

        // When
        cacheInvalidationService.invalidateContractCache(contractAddress, operationType)

        // Then
        verify(cache, never()).evict(any())
    }

    @Test
    fun `invalidateContractCache should handle cache manager returning null cache`() {
        // Given
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val operationType = "raiseDispute"
        
        whenever(cacheManager.getCache(CONTRACT_INFO_CACHE)).thenReturn(null)

        // When - should not throw exception
        cacheInvalidationService.invalidateContractCache(contractAddress, operationType)

        // Then - no cache operations should be attempted
        verifyNoInteractions(cache)
    }

    @Test
    fun `invalidateContractCache should handle cache exceptions gracefully`() {
        // Given
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val operationType = "claimFunds"
        
        whenever(cache.get(contractAddress)).thenThrow(RuntimeException("Cache error"))

        // When - should not throw exception
        cacheInvalidationService.invalidateContractCache(contractAddress, operationType)

        // Then - method completes without throwing
        verify(cache).get(contractAddress)
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
            whenever(cache.get(address)).thenReturn(Cache.ValueWrapper { createTestContractInfo(address) })
            whenever(cache.get("status:$address")).thenReturn(Cache.ValueWrapper { ContractStatus.ACTIVE })
        }

        // When
        cacheInvalidationService.invalidateMultipleContractCache(contractAddresses, operationType, transactionHash)

        // Then
        contractAddresses.forEach { address ->
            verify(cache).evict(address)
            verify(cache).evict("status:$address")
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
        verifyNoInteractions(cache)
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
        whenever(cache.get(contractAddresses[0])).thenReturn(Cache.ValueWrapper { createTestContractInfo(contractAddresses[0]) })
        whenever(cache.get("status:${contractAddresses[0]}")).thenReturn(Cache.ValueWrapper { ContractStatus.ACTIVE })
        
        // Setup second contract to fail
        whenever(cache.get(contractAddresses[1])).thenThrow(RuntimeException("Cache error"))

        // When - should not throw exception
        cacheInvalidationService.invalidateMultipleContractCache(contractAddresses, operationType)

        // Then - first contract should be processed successfully
        verify(cache).evict(contractAddresses[0])
        verify(cache).evict("status:${contractAddresses[0]}")
        
        // Second contract get operation should be attempted
        verify(cache).get(contractAddresses[1])
    }

    @Test
    fun `invalidateAllContractCache should clear all cache entries`() {
        // Given
        val reason = "Emergency cache clear"

        // When
        cacheInvalidationService.invalidateAllContractCache(reason)

        // Then
        verify(cache).clear()
    }

    @Test
    fun `invalidateAllContractCache should handle null cache gracefully`() {
        // Given
        val reason = "Cache not available"
        whenever(cacheManager.getCache(CONTRACT_INFO_CACHE)).thenReturn(null)

        // When - should not throw exception
        cacheInvalidationService.invalidateAllContractCache(reason)

        // Then - no cache operations should be attempted
        verifyNoInteractions(cache)
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
        whenever(cacheManager.getCache(CONTRACT_INFO_CACHE)).thenReturn(null)

        // When
        val result = cacheInvalidationService.isCacheAvailable()

        // Then
        assertFalse(result)
    }

    @Test
    fun `isCacheAvailable should return false when exception occurs`() {
        // Given
        whenever(cacheManager.getCache(CONTRACT_INFO_CACHE)).thenThrow(RuntimeException("Cache error"))

        // When
        val result = cacheInvalidationService.isCacheAvailable()

        // Then
        assertFalse(result)
    }

    @Test
    fun `getCacheStats should return stats for Caffeine cache`() {
        // Given
        val caffeineCache = mock<com.github.benmanes.caffeine.cache.Cache<Any, Any>>()
        val stats = mock<com.github.benmanes.caffeine.cache.stats.CacheStats>()
        
        whenever(cache.nativeCache).thenReturn(caffeineCache)
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

        // Then
        assertEquals(100L, result["requestCount"])
        assertEquals(80L, result["hitCount"])
        assertEquals(0.8, result["hitRate"])
        assertEquals(20L, result["missCount"])
        assertEquals(0.2, result["missRate"])
        assertEquals(20L, result["loadCount"])
        assertEquals(5L, result["evictionCount"])
        assertEquals(50L, result["estimatedSize"])
    }

    @Test
    fun `getCacheStats should handle non-Caffeine cache`() {
        // Given
        val nonCaffeineCache = "Not a Caffeine cache"
        whenever(cache.nativeCache).thenReturn(nonCaffeineCache)

        // When
        val result = cacheInvalidationService.getCacheStats()

        // Then
        assertEquals("cache available but stats not supported", result["status"])
    }

    @Test
    fun `getCacheStats should handle null cache`() {
        // Given
        whenever(cacheManager.getCache(CONTRACT_INFO_CACHE)).thenReturn(null)

        // When
        val result = cacheInvalidationService.getCacheStats()

        // Then
        assertEquals("cache not available", result["status"])
    }

    @Test
    fun `getCacheStats should handle exceptions`() {
        // Given
        whenever(cacheManager.getCache(CONTRACT_INFO_CACHE)).thenThrow(RuntimeException("Stats error"))

        // When
        val result = cacheInvalidationService.getCacheStats()

        // Then
        assertEquals("Stats error", result["error"])
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
            fundedAt = Instant.now()
        )
    }
}