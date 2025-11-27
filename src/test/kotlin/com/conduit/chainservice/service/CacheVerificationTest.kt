package com.conduit.chainservice.service

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.escrow.models.ContractInfo
import com.conduit.chainservice.escrow.models.ContractStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.cache.support.SimpleCacheManager
import java.math.BigInteger
import java.time.Instant

/**
 * Test to verify that CachedContractQueryService is properly handling cache hits and misses
 */
class CacheVerificationTest {

    @Mock
    private lateinit var originalService: ContractQueryService
    
    @Mock
    private lateinit var cacheMetricsService: CacheMetricsService
    
    private lateinit var cacheManager: CacheManager
    private lateinit var cachedService: CachedContractQueryService
    
    private val testContractAddress = "0x1234567890abcdef1234567890abcdef12345678"
    private val testContractInfo = ContractInfo(
        contractAddress = testContractAddress,
        buyer = "0x1111111111111111111111111111111111111111",
        seller = "0x2222222222222222222222222222222222222222",
        amount = BigInteger.valueOf(1000000),
        expiryTimestamp = Instant.now().epochSecond + 3600,
        description = "Test contract",
        funded = true,
        status = ContractStatus.ACTIVE,
        createdAt = Instant.now(),
        tokenAddress = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
        fundedAt = Instant.now(),
        disputedAt = null,
        resolvedAt = null,
        claimedAt = null
    )

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        // Setup real cache manager
        val simpleCacheManager = SimpleCacheManager()
        simpleCacheManager.setCaches(listOf(
            ConcurrentMapCache("contractInfo"),
            ConcurrentMapCache("contractState"), 
            ConcurrentMapCache("transactionData")
        ))
        simpleCacheManager.afterPropertiesSet()
        cacheManager = simpleCacheManager
        
        cachedService = CachedContractQueryService(originalService, cacheMetricsService, cacheManager)
    }

    @Test
    fun `getContractInfo should show cache miss then cache hit behavior`() = runBlocking {
        // Setup mock to return test data
        whenever(originalService.getContractInfo(testContractAddress))
            .thenReturn(testContractInfo)
        
        // First call - should be cache miss and call original service
        val result1 = cachedService.getContractInfo(testContractAddress)
        
        // Verify original service was called
        verify(originalService, times(1)).getContractInfo(testContractAddress)
        verify(cacheMetricsService).recordCacheMiss("contractInfo")
        assertEquals(testContractInfo, result1)
        
        // Second call - should be cache hit and NOT call original service
        val result2 = cachedService.getContractInfo(testContractAddress)
        
        // Verify original service was NOT called again
        verify(originalService, times(1)).getContractInfo(testContractAddress) // Still just 1 call
        verify(cacheMetricsService).recordCacheHit("contractInfo")
        assertEquals(testContractInfo, result2)
        
        println("✅ Cache verification test passed - cache miss followed by cache hit")
    }
    
    @Test
    fun `cached service should be marked as Primary and used by Spring`() {
        // This test verifies the Spring configuration is correct
        val stateAwareCachedServiceClass = StateAwareCachedContractQueryService::class.java
        
        // Check that StateAwareCachedContractQueryService has @Primary annotation
        val primaryAnnotation = stateAwareCachedServiceClass.getAnnotation(org.springframework.context.annotation.Primary::class.java)
        assertNotNull(primaryAnnotation, "StateAwareCachedContractQueryService should have @Primary annotation")
        
        // Check that it implements the interface
        val interfaces = stateAwareCachedServiceClass.interfaces
        assertTrue(
            interfaces.contains(ContractQueryServiceInterface::class.java),
            "StateAwareCachedContractQueryService should implement ContractQueryServiceInterface"
        )
        
        println("✅ Spring configuration verification passed - @Primary annotation found on StateAwareCachedContractQueryService")
    }
}