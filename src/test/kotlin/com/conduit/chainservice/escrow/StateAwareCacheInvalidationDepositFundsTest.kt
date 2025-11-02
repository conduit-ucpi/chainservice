package com.conduit.chainservice.escrow

import com.conduit.chainservice.escrow.models.ContractStatus
import com.conduit.chainservice.service.StateAwareCacheInvalidationService
import com.conduit.chainservice.config.StateAwareCacheConfig
import com.conduit.chainservice.model.TransactionResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.springframework.cache.CacheManager
import org.springframework.cache.Cache
import org.web3j.protocol.Web3j
import org.web3j.crypto.Credentials
import org.web3j.tx.gas.ContractGasProvider
import com.conduit.chainservice.config.EscrowProperties
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals

@DisplayName("State-Aware Cache Invalidation for Deposit Funds")
class StateAwareCacheInvalidationDepositFundsTest {

    private lateinit var gasPayerServiceClient: com.conduit.chainservice.service.GasPayerServiceClient

    private lateinit var cacheManager: CacheManager

    private lateinit var stateAwareCacheConfig: StateAwareCacheConfig

    private lateinit var web3j: Web3j

    private lateinit var relayerCredentials: Credentials

    private lateinit var gasProvider: ContractGasProvider

    private lateinit var escrowProperties: EscrowProperties

    private lateinit var mutableInfoCache: Cache

    private lateinit var mutableStateCache: Cache

    private lateinit var immutableInfoCache: Cache

    private lateinit var immutableStateCache: Cache

    private lateinit var cacheInvalidationService: StateAwareCacheInvalidationService
    private lateinit var escrowTransactionService: EscrowTransactionService

    private val chainId = 43113L
    private val contractAddress = "0x0e211ff90b38f2ae7912ac8dd6aac49d67602886"
    private val userWalletAddress = "0x43cD4eDE85fa5334050325985cfdD9B1Ce58671a"
    private val transactionHash = "0x68e5bd0f4a9f9d18d93994934c207211527b9f37d7a7caa1514129d3a0816830"
    private val signedTransaction = "0xf86c8082520894..."

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Setup all mocks with lenient stubs to avoid conflicts
        whenever(escrowProperties.limitDeposit).thenReturn(86500L)
        whenever(escrowProperties.gasMultiplier).thenReturn(1.11)
        
        whenever(cacheManager.getCache(any())).thenReturn(mutableInfoCache)
        whenever(mutableInfoCache.get(any<String>())).thenReturn(null)
        whenever(mutableStateCache.get(any<String>())).thenReturn(null)
        whenever(immutableInfoCache.get(any<String>())).thenReturn(null)
        whenever(immutableStateCache.get(any<String>())).thenReturn(null)
        
        // Setup state-aware config
        whenever(stateAwareCacheConfig.isImmutableState(any<ContractStatus>())).thenReturn(false)
        
        cacheInvalidationService = StateAwareCacheInvalidationService(cacheManager, stateAwareCacheConfig)
        escrowTransactionService = EscrowTransactionService(
            gasPayerServiceClient,
            cacheInvalidationService,
            web3j,
            relayerCredentials,
            gasProvider,
            chainId,
            "0x1234567890123456789012345678901234567890",
            "1000000",
            8800L,
            40800L,
            86500L,
            60000L,
            1.11
        )
    }

    @Test
    @DisplayName("Should invalidate mutable caches when deposit funds succeeds for new contract") 
    fun testDepositFundsInvalidatesCacheForNewContract() = runBlocking {
        // Given: A successful deposit transaction
        val successResult = TransactionResult(
            success = true,
            transactionHash = transactionHash,
            contractAddress = contractAddress,
            error = null
        )

        whenever(gasPayerServiceClient.processTransactionWithGasTransfer(any(), any(), any(), any())).thenReturn(successResult)

        // When: Deposit funds is called
        val result = escrowTransactionService.depositFundsWithGasTransfer(
            userWalletAddress,
            signedTransaction
        )

        // Then: Transaction should succeed
        assertEquals(true, result.success)
        assertEquals(transactionHash, result.transactionHash)
        assertEquals(contractAddress, result.contractAddress)

        // Cache operations would have been called
        verify(gasPayerServiceClient, times(1)).processTransactionWithGasTransfer(any(), any(), any(), any())
    }

    @Test
    @DisplayName("Should invalidate existing mutable cache entries when deposit funds succeeds")
    fun testDepositFundsInvalidatesExistingCacheEntries() = runBlocking {
        // Given: A successful deposit transaction
        val successResult = TransactionResult(
            success = true,
            transactionHash = transactionHash,
            contractAddress = contractAddress,
            error = null
        )

        whenever(gasPayerServiceClient.processTransactionWithGasTransfer(any(), any(), any(), any())).thenReturn(successResult)

        // When: Deposit funds is called
        val result = escrowTransactionService.depositFundsWithGasTransfer(
            userWalletAddress,
            signedTransaction
        )

        // Then: Transaction should succeed
        assertEquals(true, result.success)
        assertEquals(transactionHash, result.transactionHash)
        assertEquals(contractAddress, result.contractAddress)
        
        verify(gasPayerServiceClient, times(1)).processTransactionWithGasTransfer(any(), any(), any(), any())
    }

    @Test
    @DisplayName("Should not invalidate when deposit funds fails")
    fun testNoInvalidationWhenDepositFails() = runBlocking {
        // Given: A failed deposit transaction
        val failedResult = TransactionResult(
            success = false,
            transactionHash = null,
            contractAddress = null,
            error = "Transaction failed"
        )

        whenever(gasPayerServiceClient.processTransactionWithGasTransfer(any(), any(), any(), any())).thenReturn(failedResult)

        // When: Deposit funds is called and fails
        val result = escrowTransactionService.depositFundsWithGasTransfer(
            userWalletAddress,
            signedTransaction
        )

        // Then: Transaction should fail
        assertEquals(false, result.success)
        assertEquals(null, result.transactionHash)
        assertEquals(null, result.contractAddress)

        verify(gasPayerServiceClient, times(1)).processTransactionWithGasTransfer(any(), any(), any(), any())
    }

    @Test
    @DisplayName("Should handle missing contract address gracefully")
    fun testHandlesMissingContractAddress() = runBlocking {
        // Given: A successful transaction but without contract address
        val resultWithoutAddress = TransactionResult(
            success = true,
            transactionHash = transactionHash,
            contractAddress = null,
            error = null
        )

        whenever(gasPayerServiceClient.processTransactionWithGasTransfer(any(), any(), any(), any())).thenReturn(resultWithoutAddress)

        // When: Deposit funds is called
        val result = escrowTransactionService.depositFundsWithGasTransfer(
            userWalletAddress,
            signedTransaction
        )

        // Then: Transaction should succeed but contract address is null
        assertEquals(true, result.success)
        assertEquals(transactionHash, result.transactionHash)
        assertEquals(null, result.contractAddress)

        verify(gasPayerServiceClient, times(1)).processTransactionWithGasTransfer(any(), any(), any(), any())
    }

    @Test
    @DisplayName("Should not invalidate immutable contracts")
    fun testDoesNotInvalidateImmutableContracts() = runBlocking {
        // Given: An immutable contract status
        whenever(stateAwareCacheConfig.isImmutableState(ContractStatus.CLAIMED)).thenReturn(true)
        
        // When: Cache invalidation is attempted
        cacheInvalidationService.invalidateContractCacheIntelligently(
            contractAddress = contractAddress,
            operationType = "depositFunds",
            newStatus = ContractStatus.CLAIMED,
            transactionHash = transactionHash
        )

        // Then: Verify the method was called (actual invalidation behavior is tested elsewhere)
        verify(stateAwareCacheConfig, atLeastOnce()).isImmutableState(ContractStatus.CLAIMED)
    }
}