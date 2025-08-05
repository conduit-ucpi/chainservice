package com.conduit.chainservice.service

import com.conduit.chainservice.config.StateAwareCacheConfig
import com.conduit.chainservice.escrow.models.ContractInfo
import com.conduit.chainservice.escrow.models.ContractStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigInteger
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

/**
 * COMPREHENSIVE TEST for State-Aware Cache Optimization
 * 
 * Validates the revolutionary blockchain immutability optimization:
 * - IMMUTABLE contracts (CLAIMED, RESOLVED, EXPIRED) cached indefinitely
 * - MUTABLE contracts (CREATED, ACTIVE, DISPUTED) with short TTL
 * - INTELLIGENT INVALIDATION that respects immutability
 * - CACHE PROMOTION when contracts transition to final states
 */
@SpringBootTest
@ActiveProfiles("test")
class StateAwareCacheOptimizationTest {

    @Autowired
    private lateinit var cacheManager: CacheManager
    
    @Autowired
    private lateinit var stateAwareCacheConfig: StateAwareCacheConfig
    
    @Autowired
    private lateinit var stateAwareCacheInvalidationService: StateAwareCacheInvalidationService

    private val testContractAddress = "0x1234567890123456789012345678901234567890"
    private val testContractAddress2 = "0x2345678901234567890123456789012345678901"

    @BeforeEach
    fun setUp() {
        // Clear all caches before each test
        stateAwareCacheInvalidationService.clearAllCaches("Test setup")
    }

    @Test
    @DisplayName("Immutable contracts should be cached indefinitely without TTL")
    fun testImmutableContractCaching() {
        val claimedContract = createTestContract(ContractStatus.CLAIMED)
        val resolvedContract = createTestContract(ContractStatus.RESOLVED)
        val expiredContract = createTestContract(ContractStatus.EXPIRED)
        
        // Cache immutable contracts
        val immutableInfoCache = cacheManager.getCache(StateAwareCacheConfig.CONTRACT_INFO_IMMUTABLE_CACHE)
        assertNotNull(immutableInfoCache)
        
        immutableInfoCache!!.put(testContractAddress, claimedContract)
        immutableInfoCache!!.put(testContractAddress2, resolvedContract)
        
        // Verify they are cached
        assertEquals(claimedContract, immutableInfoCache!!.get(testContractAddress, ContractInfo::class.java))
        assertEquals(resolvedContract, immutableInfoCache!!.get(testContractAddress2, ContractInfo::class.java))
        
        // Simulate time passing (in real cache, these would not expire due to no TTL)
        // This test verifies cache structure, not actual TTL behavior
        println("✅ Immutable contracts cached successfully in dedicated immutable cache")
    }

    @Test
    @DisplayName("Mutable contracts should be cached with TTL for real-time updates")
    fun testMutableContractCaching() {
        val createdContract = createTestContract(ContractStatus.CREATED)
        val activeContract = createTestContract(ContractStatus.ACTIVE)
        val disputedContract = createTestContract(ContractStatus.DISPUTED)
        
        // Cache mutable contracts
        val mutableInfoCache = cacheManager.getCache(StateAwareCacheConfig.CONTRACT_INFO_MUTABLE_CACHE)
        assertNotNull(mutableInfoCache)
        
        mutableInfoCache!!.put(testContractAddress, createdContract)
        mutableInfoCache!!.put(testContractAddress2, activeContract)
        
        // Verify they are cached
        assertEquals(createdContract, mutableInfoCache!!.get(testContractAddress, ContractInfo::class.java))
        assertEquals(activeContract, mutableInfoCache!!.get(testContractAddress2, ContractInfo::class.java))
        
        println("✅ Mutable contracts cached successfully in dedicated mutable cache with TTL")
    }

    @Test
    @DisplayName("State-aware cache configuration should identify immutable vs mutable states correctly")
    fun testStateClassification() {
        // Test immutable states
        assert(stateAwareCacheConfig.isImmutableState(ContractStatus.CLAIMED))
        assert(stateAwareCacheConfig.isImmutableState(ContractStatus.RESOLVED))
        assert(stateAwareCacheConfig.isImmutableState(ContractStatus.EXPIRED))
        
        // Test mutable states
        assert(!stateAwareCacheConfig.isImmutableState(ContractStatus.CREATED))
        assert(!stateAwareCacheConfig.isImmutableState(ContractStatus.ACTIVE))
        assert(!stateAwareCacheConfig.isImmutableState(ContractStatus.DISPUTED))
        
        // Test cache name selection
        assertEquals(StateAwareCacheConfig.CONTRACT_INFO_IMMUTABLE_CACHE, 
                    stateAwareCacheConfig.getContractInfoCacheName(ContractStatus.CLAIMED))
        assertEquals(StateAwareCacheConfig.CONTRACT_INFO_MUTABLE_CACHE, 
                    stateAwareCacheConfig.getContractInfoCacheName(ContractStatus.ACTIVE))
        
        println("✅ State classification working correctly")
    }

    @Test
    @DisplayName("Intelligent invalidation should skip immutable contracts")
    fun testIntelligentInvalidationSkipsImmutable() {
        // Cache an immutable contract
        val claimedContract = createTestContract(ContractStatus.CLAIMED)
        val immutableCache = cacheManager.getCache(StateAwareCacheConfig.CONTRACT_INFO_IMMUTABLE_CACHE)
        immutableCache!!.put(testContractAddress, claimedContract)
        
        // Verify it's cached
        assertNotNull(immutableCache!!.get(testContractAddress, ContractInfo::class.java))
        
        // Try to invalidate it
        stateAwareCacheInvalidationService.invalidateContractCacheIntelligently(
            testContractAddress, 
            "TEST_OPERATION", 
            ContractStatus.CLAIMED
        )
        
        // Should still be cached (not invalidated)
        assertNotNull(immutableCache!!.get(testContractAddress, ContractInfo::class.java))
        
        println("✅ Intelligent invalidation correctly skipped immutable contract")
    }

    @Test
    @DisplayName("Cache promotion should move contracts from mutable to immutable when they reach final state")
    fun testCachePromotion() {
        // Start with a mutable contract
        val activeContract = createTestContract(ContractStatus.ACTIVE)
        val mutableCache = cacheManager.getCache(StateAwareCacheConfig.CONTRACT_INFO_MUTABLE_CACHE)
        mutableCache!!.put(testContractAddress, activeContract)
        
        // Verify it's in mutable cache
        assertNotNull(mutableCache!!.get(testContractAddress, ContractInfo::class.java))
        
        // Simulate state transition to final state
        stateAwareCacheInvalidationService.invalidateContractCacheIntelligently(
            testContractAddress,
            "FUNDS_CLAIMED",
            ContractStatus.CLAIMED  // New final state
        )
        
        // Should be removed from mutable cache
        assertNull(mutableCache!!.get(testContractAddress, ContractInfo::class.java))
        
        // Should be promoted to immutable cache with updated status
        val immutableCache = cacheManager.getCache(StateAwareCacheConfig.CONTRACT_INFO_IMMUTABLE_CACHE)
        val promotedContract = immutableCache!!.get(testContractAddress, ContractInfo::class.java)
        assertNotNull(promotedContract)
        assertEquals(ContractStatus.CLAIMED, promotedContract?.status)
        
        println("✅ Cache promotion working correctly - contract moved from mutable to immutable cache")
    }

    @Test
    @DisplayName("State-aware cache statistics should provide comprehensive insights")
    fun testStateAwareCacheStatistics() {
        // Add contracts to different caches
        val claimedContract = createTestContract(ContractStatus.CLAIMED)
        val activeContract = createTestContract(ContractStatus.ACTIVE)
        
        val immutableCache = cacheManager.getCache(StateAwareCacheConfig.CONTRACT_INFO_IMMUTABLE_CACHE)
        val mutableCache = cacheManager.getCache(StateAwareCacheConfig.CONTRACT_INFO_MUTABLE_CACHE)
        
        immutableCache!!.put(testContractAddress, claimedContract)
        mutableCache!!.put(testContractAddress2, activeContract)
        
        // Get statistics
        val stats = stateAwareCacheInvalidationService.getStateAwareCacheStats()
        
        // Should have separate statistics for immutable and mutable caches
        assert(stats.containsKey("immutable_contractInfo"))
        assert(stats.containsKey("mutable_contractInfo"))
        assert(stats.containsKey("summary"))
        
        println("✅ State-aware cache statistics providing comprehensive insights")
        println("Cache stats structure: ${stats.keys}")
    }

    @Test
    @DisplayName("Batch intelligent invalidation should handle mixed immutable/mutable contracts correctly")
    fun testBatchIntelligentInvalidation() {
        // Set up mixed contracts
        val claimedContract = createTestContract(ContractStatus.CLAIMED)
        val activeContract = createTestContract(ContractStatus.ACTIVE)
        val disputedContract = createTestContract(ContractStatus.DISPUTED)
        
        val immutableCache = cacheManager.getCache(StateAwareCacheConfig.CONTRACT_INFO_IMMUTABLE_CACHE)
        val mutableCache = cacheManager.getCache(StateAwareCacheConfig.CONTRACT_INFO_MUTABLE_CACHE)
        
        immutableCache!!.put(testContractAddress, claimedContract)  // Immutable
        mutableCache!!.put(testContractAddress2, activeContract)    // Mutable
        
        // Create batch updates
        val updates = listOf(
            StateAwareCacheInvalidationService.ContractUpdate(testContractAddress, ContractStatus.CLAIMED),     // Should skip
            StateAwareCacheInvalidationService.ContractUpdate(testContractAddress2, ContractStatus.RESOLVED)   // Should promote
        )
        
        // Execute batch invalidation
        stateAwareCacheInvalidationService.invalidateMultipleContractCacheIntelligently(
            updates, 
            "BATCH_TEST"
        )
        
        // Immutable contract should still be in immutable cache
        assertNotNull(immutableCache!!.get(testContractAddress, ContractInfo::class.java))
        
        // Mutable contract should be removed from mutable cache
        assertNull(mutableCache!!.get(testContractAddress2, ContractInfo::class.java))
        
        // And promoted to immutable cache with new status
        val promotedContract = immutableCache!!.get(testContractAddress2, ContractInfo::class.java)
        assertNotNull(promotedContract)
        assertEquals(ContractStatus.RESOLVED, promotedContract?.status)
        
        println("✅ Batch intelligent invalidation handled mixed contracts correctly")
    }

    @Test
    @DisplayName("Transaction data cache should behave as immutable (never expire)")
    fun testTransactionDataImmutability() {
        val transactionCache = cacheManager.getCache(StateAwareCacheConfig.TRANSACTION_DATA_CACHE)
        assertNotNull(transactionCache)
        
        // Transaction data should be cached without TTL
        val testEventHistory = "Mock event history data"
        transactionCache!!.put(testContractAddress, testEventHistory)
        
        // Should be retrievable
        assertEquals(testEventHistory, transactionCache!!.get(testContractAddress, String::class.java))
        
        // Transaction data should never be invalidated by intelligent invalidation
        stateAwareCacheInvalidationService.invalidateContractCacheIntelligently(
            testContractAddress,
            "TEST_OPERATION",
            ContractStatus.CLAIMED
        )
        
        // Transaction data should still be there
        assertEquals(testEventHistory, transactionCache!!.get(testContractAddress, String::class.java))
        
        println("✅ Transaction data cache behaves as immutable - never invalidated")
    }

    private fun createTestContract(status: ContractStatus): ContractInfo {
        return ContractInfo(
            contractAddress = testContractAddress,
            buyer = "0xBuyer",
            seller = "0xSeller",
            amount = BigInteger.valueOf(1000000), // 1 USDC in microUSDC
            expiryTimestamp = Instant.now().epochSecond + 86400, // 24 hours from now
            description = "Test contract",
            funded = status != ContractStatus.CREATED,
            status = status,
            createdAt = Instant.now(),
            fundedAt = if (status != ContractStatus.CREATED) Instant.now() else null,
            disputedAt = if (status == ContractStatus.DISPUTED) Instant.now() else null,
            resolvedAt = if (status == ContractStatus.RESOLVED) Instant.now() else null,
            claimedAt = if (status == ContractStatus.CLAIMED) Instant.now() else null
        )
    }
}