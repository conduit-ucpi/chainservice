package com.conduit.chainservice.escrow

import com.conduit.chainservice.escrow.models.DepositFundsRequest
import com.conduit.chainservice.service.ContractServiceClient
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.EmailServiceClient
import com.fasterxml.jackson.databind.ObjectMapper
import com.utility.chainservice.models.TransactionResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import reactor.core.publisher.Mono

/**
 * Integration test for deposit-funds state transitions.
 * Tests the complete state flow: OK -> IN-PROCESS -> OK (success) or remains IN-PROCESS (failure).
 */
class DepositFundsStateTransitionIntegrationTest {

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    @Mock
    private lateinit var escrowTransactionService: EscrowTransactionService

    @Mock
    private lateinit var contractQueryService: ContractQueryService

    @Mock
    private lateinit var contractServiceClient: ContractServiceClient

    @Mock
    private lateinit var escrowServicePlugin: EscrowServicePlugin

    @Mock
    private lateinit var emailServiceClient: EmailServiceClient

    private lateinit var escrowController: EscrowController

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        escrowController = EscrowController(
            escrowTransactionService,
            contractQueryService,
            contractServiceClient,
            escrowServicePlugin,
            emailServiceClient
        )
        
        // Use reflection to set chainId
        val chainIdField = EscrowController::class.java.getDeclaredField("chainId")
        chainIdField.isAccessible = true
        chainIdField.set(escrowController, "43113")
        
        mockMvc = MockMvcBuilders.standaloneSetup(escrowController).build()
    }

    @Test
    fun `successful deposit should transition state OK to IN-PROCESS to OK`() {
        // Arrange
        val contractId = "507f1f77bcf86cd799439011"
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            contractId = contractId,
            amount = "100.00 USDC",
            payoutDateTime = "2024-12-31T23:59:59Z"
        )

        // Step 1: Initial state is OK
        val initialContractData = mapOf(
            "id" to contractId,
            "state" to "OK",  // Initial state
            "buyerAddress" to request.userWalletAddress,
            "amount" to "1000000"
        )

        whenever(contractServiceClient.getContract(eq(contractId), any()))
            .thenReturn(Mono.just(initialContractData))

        // Step 2: State updated to IN-PROCESS
        whenever(contractServiceClient.updateContractStatus(eq(contractId), eq("IN-PROCESS"), any()))
            .thenReturn(Mono.just(mapOf("state" to "IN-PROCESS")))

        // Step 3: Blockchain transaction succeeds
        runBlocking {
            whenever(escrowTransactionService.depositFundsWithGasTransfer(
                eq(request.userWalletAddress),
                eq(request.signedTransaction)
            )).thenReturn(TransactionResult(
                success = true,
                transactionHash = "0xabcdef123456789"
            ))
        }

        // Step 4: State updated back to OK via updateContractWithDeployment
        whenever(contractServiceClient.updateContractWithDeployment(
            contractId = eq(contractId),
            chainAddress = eq(request.contractAddress),
            chainId = eq("43113"),
            buyerAddress = eq(request.userWalletAddress),
            request = any()
        )).thenReturn(Mono.just(mock()))

        // Act
        val result = mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )

        // Assert - Successful response
        result
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.transactionHash").value("0xabcdef123456789"))

        // Verify the complete state transition flow
        inOrder(contractServiceClient, escrowTransactionService) {
            // 1. Check initial state is OK
            verify(contractServiceClient).getContract(eq(contractId), any())
            
            // 2. Update state to IN-PROCESS before blockchain operation
            verify(contractServiceClient).updateContractStatus(eq(contractId), eq("IN-PROCESS"), any())
            
            // 3. Execute blockchain transaction
            runBlocking {
                verify(escrowTransactionService).depositFundsWithGasTransfer(
                    eq(request.userWalletAddress),
                    eq(request.signedTransaction)
                )
            }
            
            // 4. Update contract with deployment details (includes state -> OK)
            verify(contractServiceClient).updateContractWithDeployment(
                contractId = eq(contractId),
                chainAddress = eq(request.contractAddress),
                chainId = eq("43113"),
                buyerAddress = eq(request.userWalletAddress),
                request = any()
            )
        }
    }

    @Test
    fun `failed deposit should leave state as IN-PROCESS`() {
        // Arrange
        val contractId = "507f1f77bcf86cd799439011"
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            contractId = contractId,
            amount = "100.00 USDC",
            payoutDateTime = "2024-12-31T23:59:59Z"
        )

        // Step 1: Initial state is OK
        val initialContractData = mapOf(
            "id" to contractId,
            "state" to "OK",
            "buyerAddress" to request.userWalletAddress,
            "amount" to "1000000"
        )

        whenever(contractServiceClient.getContract(eq(contractId), any()))
            .thenReturn(Mono.just(initialContractData))

        // Step 2: State updated to IN-PROCESS
        whenever(contractServiceClient.updateContractStatus(eq(contractId), eq("IN-PROCESS"), any()))
            .thenReturn(Mono.just(mapOf("state" to "IN-PROCESS")))

        // Step 3: Blockchain transaction fails
        runBlocking {
            whenever(escrowTransactionService.depositFundsWithGasTransfer(
                eq(request.userWalletAddress),
                eq(request.signedTransaction)
            )).thenReturn(TransactionResult(
                success = false,
                transactionHash = null,
                error = "Transaction failed: insufficient gas"
            ))
        }

        // Act
        val result = mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )

        // Assert - Failed response
        result
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Transaction failed: insufficient gas"))

        // Verify state transition stops at IN-PROCESS (no deployment update)
        inOrder(contractServiceClient, escrowTransactionService) {
            // 1. Check initial state is OK
            verify(contractServiceClient).getContract(eq(contractId), any())
            
            // 2. Update state to IN-PROCESS
            verify(contractServiceClient).updateContractStatus(eq(contractId), eq("IN-PROCESS"), any())
            
            // 3. Blockchain transaction attempted
            runBlocking {
                verify(escrowTransactionService).depositFundsWithGasTransfer(
                    eq(request.userWalletAddress),
                    eq(request.signedTransaction)
                )
            }
        }

        // Verify NO deployment update occurred (state remains IN-PROCESS)
        verify(contractServiceClient, never()).updateContractWithDeployment(any(), any(), any(), any(), any())
    }

    @Test
    fun `contract service failure during IN-PROCESS update should prevent deposit`() {
        // Arrange
        val contractId = "507f1f77bcf86cd799439011"
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            contractId = contractId,
            amount = "100.00 USDC",
            payoutDateTime = "2024-12-31T23:59:59Z"
        )

        // Step 1: Initial state is OK
        val initialContractData = mapOf(
            "id" to contractId,
            "state" to "OK",
            "buyerAddress" to request.userWalletAddress,
            "amount" to "1000000"
        )

        whenever(contractServiceClient.getContract(eq(contractId), any()))
            .thenReturn(Mono.just(initialContractData))

        // Step 2: State update to IN-PROCESS fails
        whenever(contractServiceClient.updateContractStatus(eq(contractId), eq("IN-PROCESS"), any()))
            .thenReturn(Mono.error(RuntimeException("Contract service unavailable")))

        // Act
        val result = mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )

        // Assert - Should fail due to state update failure
        result
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Failed to validate contract: Contract service unavailable"))

        // Verify no blockchain transaction was attempted
        runBlocking {
            verify(escrowTransactionService, never()).depositFundsWithGasTransfer(any(), any())
        }

        // Verify no deployment update attempted
        verify(contractServiceClient, never()).updateContractWithDeployment(any(), any(), any(), any(), any())
    }

    @Test
    fun `deployment update failure should not affect successful blockchain transaction response`() {
        // Arrange
        val contractId = "507f1f77bcf86cd799439011"
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            contractId = contractId,
            amount = "100.00 USDC",
            payoutDateTime = "2024-12-31T23:59:59Z"
        )

        val initialContractData = mapOf(
            "id" to contractId,
            "state" to "OK",
            "buyerAddress" to request.userWalletAddress,
            "amount" to "1000000"
        )

        whenever(contractServiceClient.getContract(eq(contractId), any()))
            .thenReturn(Mono.just(initialContractData))

        whenever(contractServiceClient.updateContractStatus(eq(contractId), eq("IN-PROCESS"), any()))
            .thenReturn(Mono.just(mapOf("state" to "IN-PROCESS")))

        // Blockchain transaction succeeds
        runBlocking {
            whenever(escrowTransactionService.depositFundsWithGasTransfer(
                eq(request.userWalletAddress),
                eq(request.signedTransaction)
            )).thenReturn(TransactionResult(
                success = true,
                transactionHash = "0xabcdef123456789"
            ))
        }

        // But deployment update fails
        whenever(contractServiceClient.updateContractWithDeployment(any(), any(), any(), any(), any()))
            .thenReturn(Mono.error(RuntimeException("Contract service update failed")))

        // Act
        val result = mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )

        // Assert - Should still return success since blockchain transaction succeeded
        // (deployment update failure is logged but doesn't fail the response)
        result
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.transactionHash").value("0xabcdef123456789"))

        // Verify all steps were attempted
        verify(contractServiceClient).getContract(eq(contractId), any())
        verify(contractServiceClient).updateContractStatus(eq(contractId), eq("IN-PROCESS"), any())
        verify(contractServiceClient).updateContractWithDeployment(any(), any(), any(), any(), any())
        runBlocking {
            verify(escrowTransactionService).depositFundsWithGasTransfer(any(), any())
        }
    }
}