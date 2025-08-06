package com.conduit.chainservice.escrow

import com.conduit.chainservice.escrow.models.ContractStatus
import com.conduit.chainservice.service.StateAwareCacheInvalidationService
import com.conduit.chainservice.config.StateAwareCacheConfig
import com.utility.chainservice.BlockchainRelayService
import com.utility.chainservice.models.TransactionResult
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

    @Mock
    private lateinit var blockchainRelayService: BlockchainRelayService

    @Mock
    private lateinit var cacheManager: CacheManager

    @Mock
    private lateinit var stateAwareCacheConfig: StateAwareCacheConfig

    @Mock
    private lateinit var web3j: Web3j

    @Mock
    private lateinit var relayerCredentials: Credentials

    @Mock
    private lateinit var gasProvider: ContractGasProvider

    @Mock
    private lateinit var escrowProperties: EscrowProperties

    @Mock
    private lateinit var mutableInfoCache: Cache

    @Mock
    private lateinit var mutableStateCache: Cache

    @Mock
    private lateinit var immutableInfoCache: Cache

    @Mock
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

        // Setup cache mocks
        whenever(cacheManager.getCache(StateAwareCacheConfig.CONTRACT_INFO_MUTABLE_CACHE))
            .thenReturn(mutableInfoCache)
        whenever(cacheManager.getCache(StateAwareCacheConfig.CONTRACT_STATE_MUTABLE_CACHE))
            .thenReturn(mutableStateCache)
        whenever(cacheManager.getCache(StateAwareCacheConfig.CONTRACT_INFO_IMMUTABLE_CACHE))
            .thenReturn(immutableInfoCache)
        whenever(cacheManager.getCache(StateAwareCacheConfig.CONTRACT_STATE_IMMUTABLE_CACHE))
            .thenReturn(immutableStateCache)

        // Setup state-aware config
        whenever(stateAwareCacheConfig.isImmutableState(any())).thenAnswer { invocation ->
            val status = invocation.getArgument<ContractStatus>(0)
            status in setOf(ContractStatus.CLAIMED, ContractStatus.RESOLVED, ContractStatus.EXPIRED)
        }

        cacheInvalidationService = StateAwareCacheInvalidationService(cacheManager, stateAwareCacheConfig)
        escrowTransactionService = EscrowTransactionService(
            blockchainRelayService,
            cacheInvalidationService,
            web3j,
            relayerCredentials,
            gasProvider,
            escrowProperties,
            chainId
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

        whenever(blockchainRelayService.processTransactionWithGasTransfer(
            userWalletAddress,
            signedTransaction,
            "depositFunds"
        )).thenReturn(successResult)

        // When no cached data exists (new contract)
        whenever(mutableInfoCache.get(contractAddress)).thenReturn(null)
        whenever(immutableInfoCache.get(contractAddress)).thenReturn(null)
        whenever(mutableStateCache.get(contractAddress)).thenReturn(null)
        whenever(immutableStateCache.get(contractAddress)).thenReturn(null)

        // When: Deposit funds is called
        val result = escrowTransactionService.depositFundsWithGasTransfer(
            userWalletAddress,
            signedTransaction
        )

        // Then: Transaction should succeed
        assertEquals(true, result.success)
        assertEquals(transactionHash, result.transactionHash)
        assertEquals(contractAddress, result.contractAddress)

        // And: Cache invalidation should be attempted (even though caches are empty)
        verify(mutableInfoCache, times(1)).get(contractAddress)
        verify(mutableStateCache, times(1)).get(contractAddress)
        
        // No eviction should happen since caches were empty
        verify(mutableInfoCache, never()).evict(contractAddress)
        verify(mutableStateCache, never()).evict(contractAddress)
        
        // Immutable caches should not be touched for ACTIVE status
        verify(immutableInfoCache, never()).evict(contractAddress)
        verify(immutableStateCache, never()).evict(contractAddress)
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

        whenever(blockchainRelayService.processTransactionWithGasTransfer(
            userWalletAddress,
            signedTransaction,
            "depositFunds"
        )).thenReturn(successResult)

        // When cached data exists in mutable caches (contract was CREATED status)
        val mockInfoWrapper = mock<Cache.ValueWrapper> {
            on { get() } doReturn "cached_info"
        }
        val mockStateWrapper = mock<Cache.ValueWrapper> {
            on { get() } doReturn "cached_state"
        }
        whenever(mutableInfoCache.get(contractAddress)).thenReturn(mockInfoWrapper)
        whenever(mutableStateCache.get(contractAddress)).thenReturn(mockStateWrapper)
        whenever(immutableInfoCache.get(contractAddress)).thenReturn(null)
        whenever(immutableStateCache.get(contractAddress)).thenReturn(null)

        // When: Deposit funds is called
        val result = escrowTransactionService.depositFundsWithGasTransfer(
            userWalletAddress,
            signedTransaction
        )

        // Then: Transaction should succeed
        assertEquals(true, result.success)

        // And: Mutable caches should be invalidated
        verify(mutableInfoCache, times(1)).evict(contractAddress)
        verify(mutableStateCache, times(1)).evict(contractAddress)
        
        // Immutable caches should not be touched
        verify(immutableInfoCache, never()).evict(contractAddress)
        verify(immutableStateCache, never()).evict(contractAddress)
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

        whenever(blockchainRelayService.processTransactionWithGasTransfer(
            userWalletAddress,
            signedTransaction,
            "depositFunds"
        )).thenReturn(failedResult)

        // When: Deposit funds is called and fails
        val result = escrowTransactionService.depositFundsWithGasTransfer(
            userWalletAddress,
            signedTransaction
        )

        // Then: Transaction should fail
        assertEquals(false, result.success)
        assertEquals("Transaction failed", result.error)

        // And: No cache invalidation should occur
        verify(mutableInfoCache, never()).get(any())
        verify(mutableInfoCache, never()).evict(any())
        verify(mutableStateCache, never()).get(any())
        verify(mutableStateCache, never()).evict(any())
        verify(immutableInfoCache, never()).get(any())
        verify(immutableInfoCache, never()).evict(any())
        verify(immutableStateCache, never()).get(any())
        verify(immutableStateCache, never()).evict(any())
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

        whenever(blockchainRelayService.processTransactionWithGasTransfer(
            userWalletAddress,
            signedTransaction,
            "depositFunds"
        )).thenReturn(resultWithoutAddress)

        // When: Deposit funds is called
        val result = escrowTransactionService.depositFundsWithGasTransfer(
            userWalletAddress,
            signedTransaction
        )

        // Then: Transaction should succeed
        assertEquals(true, result.success)
        assertEquals(transactionHash, result.transactionHash)

        // And: No cache invalidation should occur since we don't have the contract address
        verify(mutableInfoCache, never()).get(any())
        verify(mutableInfoCache, never()).evict(any())
        verify(mutableStateCache, never()).get(any())
        verify(mutableStateCache, never()).evict(any())
    }

    @Test
    @DisplayName("Should not invalidate immutable contracts")
    fun testDoesNotInvalidateImmutableContracts() = runBlocking {
        // Given: Contract in CLAIMED state (immutable)
        val claimedContract = mock<com.conduit.chainservice.escrow.models.ContractInfo> {
            on { status } doReturn ContractStatus.CLAIMED
        }
        
        whenever(immutableInfoCache.get(contractAddress, com.conduit.chainservice.escrow.models.ContractInfo::class.java))
            .thenReturn(claimedContract)
        whenever(mutableInfoCache.get(contractAddress, com.conduit.chainservice.escrow.models.ContractInfo::class.java))
            .thenReturn(null)

        // When: Cache invalidation is attempted
        cacheInvalidationService.invalidateContractCacheIntelligently(
            contractAddress = contractAddress,
            operationType = "depositFunds",
            newStatus = ContractStatus.ACTIVE,
            transactionHash = transactionHash
        )

        // Then: No caches should be evicted (contract is immutable)
        verify(mutableInfoCache, never()).evict(any())
        verify(mutableStateCache, never()).evict(any())
        verify(immutableInfoCache, never()).evict(any())
        verify(immutableStateCache, never()).evict(any())
    }
}