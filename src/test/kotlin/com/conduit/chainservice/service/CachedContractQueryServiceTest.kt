package com.conduit.chainservice.service

import com.conduit.chainservice.escrow.models.ContractInfo
import com.conduit.chainservice.escrow.models.ContractInfoResult
import com.conduit.chainservice.escrow.models.ContractStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.springframework.cache.CacheManager
import org.springframework.cache.support.SimpleValueWrapper
import java.math.BigInteger
import java.time.Instant

class CachedContractQueryServiceTest {

    @Mock
    private lateinit var originalService: ContractQueryService
    
    @Mock
    private lateinit var cacheMetricsService: CacheMetricsService
    
    @Mock
    private lateinit var cacheManager: CacheManager
    
    @Mock
    private lateinit var cache: org.springframework.cache.Cache

    private lateinit var cachedService: CachedContractQueryService

    private val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
    private val contractInfo = ContractInfo(
        contractAddress = contractAddress,
        buyer = "0xbuyer",
        seller = "0xseller", 
        amount = BigInteger.valueOf(1000),
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
        whenever(cacheManager.getCache("contractInfo")).thenReturn(cache)
        cachedService = CachedContractQueryService(originalService, cacheMetricsService, cacheManager)
    }

    @Test
    fun `getContractInfo - cache miss triggers original service call`() = runBlocking {
        // Mock cache miss
        whenever(cache.get(contractAddress, ContractInfo::class.java)).thenReturn(null)
        whenever(originalService.getContractInfo(contractAddress)).thenReturn(contractInfo)

        val result = cachedService.getContractInfo(contractAddress)

        assertEquals(contractInfo, result)
        verify(originalService).getContractInfo(contractAddress)
        verify(cacheMetricsService).recordCacheMiss("contractInfo")
    }

    @Test
    fun `getBatchContractInfo - combines cached and fresh results`() = runBlocking {
        val contract1 = "0xcontract1"
        val contract2 = "0xcontract2"
        val contract3 = "0xcontract3"
        
        val contractInfo1 = contractInfo.copy(contractAddress = contract1)
        val contractInfo2 = contractInfo.copy(contractAddress = contract2)
        val contractInfo3 = contractInfo.copy(contractAddress = contract3)
        
        val contractAddresses = listOf(contract1, contract2, contract3)

        // Mock cache hits for contract1, miss for contract2 and contract3
        whenever(cache.get(contract1, ContractInfo::class.java)).thenReturn(contractInfo1)
        whenever(cache.get(contract2, ContractInfo::class.java)).thenReturn(null)
        whenever(cache.get(contract3, ContractInfo::class.java)).thenReturn(null)

        // Mock original service batch call for uncached contracts
        val batchResult = mapOf(
            contract2 to ContractInfoResult(true, contractInfo2, null),
            contract3 to ContractInfoResult(true, contractInfo3, null)
        )
        whenever(originalService.getBatchContractInfo(listOf(contract2, contract3)))
            .thenReturn(batchResult)

        val result = cachedService.getBatchContractInfo(contractAddresses)

        assertEquals(3, result.size)
        assertTrue(result[contract1]?.success == true)
        assertTrue(result[contract2]?.success == true)
        assertTrue(result[contract3]?.success == true)
        assertEquals(contractInfo1, result[contract1]?.contractInfo)
        assertEquals(contractInfo2, result[contract2]?.contractInfo)
        assertEquals(contractInfo3, result[contract3]?.contractInfo)

        // Verify cache interactions
        verify(cache).get(contract1, ContractInfo::class.java)
        verify(cache).get(contract2, ContractInfo::class.java)
        verify(cache).get(contract3, ContractInfo::class.java)
        verify(cache).put(contract2, contractInfo2)
        verify(cache).put(contract3, contractInfo3)

        // Verify original service only called for uncached contracts
        verify(originalService).getBatchContractInfo(listOf(contract2, contract3))
        verify(originalService, never()).getBatchContractInfo(contractAddresses)

        // Verify metrics
        verify(cacheMetricsService).recordCacheRequest("getBatchContractInfo", 3)
        verify(cacheMetricsService).recordCacheHit("contractInfo")
        verify(cacheMetricsService).recordBatchQueryStats(3, 1, 2)
    }

    @Test
    fun `getBatchContractInfo - handles all cache hits`() = runBlocking {
        val contract1 = "0xcontract1" 
        val contract2 = "0xcontract2"
        
        val contractInfo1 = contractInfo.copy(contractAddress = contract1)
        val contractInfo2 = contractInfo.copy(contractAddress = contract2)
        
        val contractAddresses = listOf(contract1, contract2)

        // Mock cache hits for both contracts
        whenever(cache.get(contract1, ContractInfo::class.java)).thenReturn(contractInfo1)
        whenever(cache.get(contract2, ContractInfo::class.java)).thenReturn(contractInfo2)

        val result = cachedService.getBatchContractInfo(contractAddresses)

        assertEquals(2, result.size)
        assertTrue(result[contract1]?.success == true)
        assertTrue(result[contract2]?.success == true)

        // Verify no original service calls
        verify(originalService, never()).getBatchContractInfo(any())
        
        // Verify cache metrics for 100% hit rate
        verify(cacheMetricsService).recordBatchQueryStats(2, 2, 0)
    }

    @Test
    fun `getBatchContractInfo - handles empty input`() = runBlocking {
        val result = cachedService.getBatchContractInfo(emptyList())

        assertEquals(0, result.size)
        verify(originalService, never()).getBatchContractInfo(any())
        verify(cacheMetricsService).recordCacheRequest("getBatchContractInfo", 0)
    }

    @Test
    fun `getContractStatus - uses caching`() = runBlocking {
        val status = ContractStatus.ACTIVE
        whenever(originalService.getContractStatus(contractAddress)).thenReturn(status)

        val result = cachedService.getContractStatus(contractAddress)

        assertEquals(status, result)
        verify(originalService).getContractStatus(contractAddress)
        verify(cacheMetricsService).recordCacheMiss("contractStatus")
    }
}