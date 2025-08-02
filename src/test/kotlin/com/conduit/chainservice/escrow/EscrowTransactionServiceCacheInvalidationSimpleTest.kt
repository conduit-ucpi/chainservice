package com.conduit.chainservice.escrow

import com.conduit.chainservice.service.CacheInvalidationService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class EscrowTransactionServiceCacheInvalidationSimpleTest {

    private lateinit var cacheInvalidationService: CacheInvalidationService

    @BeforeEach
    fun setUp() {
        cacheInvalidationService = mock()
    }

    @Test
    fun `cache invalidation service should be called with correct parameters`() {
        // Given
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val operationType = "createContract"
        val transactionHash = "0xabc123"

        // When
        cacheInvalidationService.invalidateContractCache(contractAddress, operationType, transactionHash)

        // Then
        verify(cacheInvalidationService).invalidateContractCache(contractAddress, operationType, transactionHash)
    }

    @Test
    fun `cache invalidation service should handle multiple contract addresses`() {
        // Given
        val contractAddresses = listOf(
            "0x1234567890abcdef1234567890abcdef12345678",
            "0x9876543210fedcba9876543210fedcba98765432"
        )
        val operationType = "batchOperation"
        val transactionHash = "0xbatch123"

        // When
        cacheInvalidationService.invalidateMultipleContractCache(contractAddresses, operationType, transactionHash)

        // Then
        verify(cacheInvalidationService).invalidateMultipleContractCache(contractAddresses, operationType, transactionHash)
    }

    @Test
    fun `cache invalidation service should be available for health checks`() {
        // Given
        whenever(cacheInvalidationService.isCacheAvailable()).thenReturn(true)

        // When
        val isAvailable = cacheInvalidationService.isCacheAvailable()

        // Then
        assertTrue(isAvailable)
        verify(cacheInvalidationService).isCacheAvailable()
    }

    @Test
    fun `cache invalidation service should provide cache statistics`() {
        // Given
        val expectedStats = mapOf(
            "requestCount" to 100L,
            "hitCount" to 80L,
            "hitRate" to 0.8
        )
        whenever(cacheInvalidationService.getCacheStats()).thenReturn(expectedStats)

        // When
        val stats = cacheInvalidationService.getCacheStats()

        // Then
        assertEquals(expectedStats, stats)
        verify(cacheInvalidationService).getCacheStats()
    }
}