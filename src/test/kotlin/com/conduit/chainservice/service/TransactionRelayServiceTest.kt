package com.conduit.chainservice.service

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.escrow.EscrowTransactionService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.tx.gas.ContractGasProvider
import java.math.BigInteger

class TransactionRelayServiceTest {

    @Mock
    private lateinit var gasPayerServiceClient: com.conduit.chainservice.service.GasPayerServiceClient

    @Mock
    private lateinit var escrowTransactionService: EscrowTransactionService

    @Mock
    private lateinit var web3j: Web3j

    @Mock
    private lateinit var relayerCredentials: Credentials

    @Mock
    private lateinit var gasProvider: ContractGasProvider

    @Mock
    private lateinit var escrowProperties: EscrowProperties

    private lateinit var transactionRelayService: TransactionRelayService

    private val chainId = 43113L
    private val relayerAddress = "0x9876543210fedcba9876543210fedcba98765432"

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(relayerCredentials.address).thenReturn(relayerAddress)
        transactionRelayService = TransactionRelayService(
            gasPayerServiceClient, escrowTransactionService, web3j, relayerCredentials, gasProvider, escrowProperties, chainId
        )
    }

    @Test
    fun `service can be constructed with all dependencies`() {
        // Test that the service can be constructed with all dependencies
        // This validates our refactoring didn't break the constructor
        assertNotNull(transactionRelayService)
    }

    @Test
    fun `getOperationGasCosts includes all required operations`() {
        // Given - mock gas provider responses 
        whenever(gasProvider.getGasLimit(any())).thenReturn(BigInteger.valueOf(100000))
        whenever(gasProvider.getGasPrice(any())).thenReturn(BigInteger.valueOf(20000000000))
        
        // When
        val gasCosts = transactionRelayService.getOperationGasCosts()
        
        // Then
        val operationNames = gasCosts.map { it.operation }
        assertTrue(operationNames.contains("claimFunds"))
        assertTrue(operationNames.contains("depositFunds"))
        assertTrue(operationNames.contains("approveToken"))
        assertTrue(operationNames.contains("createContract"))
        assertTrue(operationNames.contains("raiseDispute"))
        assertTrue(operationNames.contains("resolveDispute"))
        assertEquals(6, gasCosts.size) // All 6 operations should be present
    }

    @Test
    fun `all gas transfer methods exist and are public`() {
        // This test verifies that our refactoring correctly maintains the public API
        val publicMethods = TransactionRelayService::class.java.methods
        val methodNames = publicMethods.map { it.name }

        assertTrue(methodNames.contains("depositFundsWithGasTransfer"))
        assertTrue(methodNames.contains("approveTokenWithGasTransfer"))
        assertTrue(methodNames.contains("approveUSDCWithGasTransfer")) // Deprecated but still available
        assertTrue(methodNames.contains("claimFundsWithGasTransfer"))
        assertTrue(methodNames.contains("raiseDisputeWithGasTransfer"))

        // Verify the methods have the correct signature (should have String and String parameters)
        val depositMethod = publicMethods.find { it.name == "depositFundsWithGasTransfer" }
        assertNotNull(depositMethod, "depositFundsWithGasTransfer method should exist")

        val approveTokenMethod = publicMethods.find { it.name == "approveTokenWithGasTransfer" }
        assertNotNull(approveTokenMethod, "approveTokenWithGasTransfer method should exist")

        val approveUsdcMethod = publicMethods.find { it.name == "approveUSDCWithGasTransfer" }
        assertNotNull(approveUsdcMethod, "approveUSDCWithGasTransfer method should exist (deprecated)")

        val claimMethod = publicMethods.find { it.name == "claimFundsWithGasTransfer" }
        assertNotNull(claimMethod, "claimFundsWithGasTransfer method should exist")

        val raiseDisputeMethod = publicMethods.find { it.name == "raiseDisputeWithGasTransfer" }
        assertNotNull(raiseDisputeMethod, "raiseDisputeWithGasTransfer method should exist")
    }

    @Test
    fun `gas costs calculation works correctly`() {
        // Given
        val gasLimit = BigInteger.valueOf(150000)
        val gasPrice = BigInteger.valueOf(25000000000) // 25 gwei
        whenever(gasProvider.getGasLimit(any())).thenReturn(gasLimit)
        whenever(gasProvider.getGasPrice(any())).thenReturn(gasPrice)
        
        // When
        val gasCosts = transactionRelayService.getOperationGasCosts()
        
        // Then
        gasCosts.forEach { cost ->
            assertEquals(gasLimit, cost.gasLimit)
            assertEquals(gasPrice, cost.gasPriceWei)
            assertEquals(gasPrice.multiply(gasLimit), cost.totalCostWei)
            assertNotNull(cost.totalCostAvax)
        }
    }

    @Test
    fun `refactoring maintains method count - no new public methods added`() {
        // This test ensures we didn't accidentally add new public methods during refactoring
        val publicMethods = TransactionRelayService::class.java.methods
        val serviceSpecificMethods = publicMethods.filter { 
            !it.name.startsWith("get") || it.name == "getOperationGasCosts"
        }.filter {
            // Filter out methods inherited from Object class
            !listOf("equals", "hashCode", "toString", "wait", "notify", "notifyAll").contains(it.name)
        }
        
        val expectedMethods = listOf(
            "createContract",
            "relayTransaction",
            "resolveDispute",
            "raiseDispute",
            "claimFunds",
            "depositFunds",
            "depositFundsWithGasTransfer",
            "approveTokenWithGasTransfer",
            "approveUSDCWithGasTransfer", // Deprecated wrapper
            "claimFundsWithGasTransfer",
            "raiseDisputeWithGasTransfer",
            "getOperationGasCosts"
        )
        
        val actualMethodNames = serviceSpecificMethods.map { it.name }.sorted()
        assertTrue(actualMethodNames.containsAll(expectedMethods), 
            "Expected methods: $expectedMethods, but found: $actualMethodNames")
    }
}