package com.conduit.chainservice.escrow

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.escrow.models.DepositFundsRequest
import com.conduit.chainservice.escrow.models.DepositFundsResponse
import com.conduit.chainservice.service.ContractServiceClient
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.EmailServiceClient
import com.conduit.chainservice.service.GasPayerServiceClient
import com.fasterxml.jackson.databind.ObjectMapper
import com.conduit.chainservice.model.TransactionResult
import jakarta.servlet.http.HttpServletRequest
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
 * Unit tests for the deposit-funds endpoint, focusing on contract state validation
 * and the fix for the state vs status field bug.
 */
class EscrowControllerDepositFundsTest {

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    @Mock
    private lateinit var escrowTransactionService: EscrowTransactionService

    @Mock
    private lateinit var contractQueryService: ContractQueryService

    @Mock
    private lateinit var contractServiceClient: ContractServiceClient

    @Mock
    private lateinit var emailServiceClient: EmailServiceClient

    @Mock
    private lateinit var gasPayerServiceClient: GasPayerServiceClient

    @Mock
    private lateinit var escrowProperties: EscrowProperties

    private lateinit var escrowController: EscrowController

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        escrowController = EscrowController(
            escrowTransactionService, 
            contractQueryService,
            contractServiceClient,
            emailServiceClient,
            gasPayerServiceClient,
            escrowProperties
        )
        
        // Use reflection to set chainId since it's a @Value property
        val chainIdField = EscrowController::class.java.getDeclaredField("chainId")
        chainIdField.isAccessible = true
        chainIdField.set(escrowController, "43113")
        
        mockMvc = MockMvcBuilders.standaloneSetup(escrowController).build()
    }

    @Test
    fun `deposit funds with valid contract state should succeed`() {
        // Arrange
        val contractId = "507f1f77bcf86cd799439011"
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            contractId = contractId,
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        // Mock contract service returning state field (not status)
        val contractData = mapOf(
            "id" to contractId,
            "state" to "OK",  // Using 'state' field, not 'status'
            "buyerAddress" to request.userWalletAddress,
            "amount" to "1000000"
        )

        whenever(contractServiceClient.getContract(eq(contractId), any()))
            .thenReturn(Mono.just(contractData))

        whenever(contractServiceClient.updateContractStatus(eq(contractId), eq("IN-PROCESS"), any()))
            .thenReturn(Mono.just(mapOf("state" to "IN-PROCESS")))

        runBlocking {
            whenever(escrowTransactionService.depositFundsWithGasTransfer(
                eq(request.userWalletAddress),
                eq(request.signedTransaction)
            )).thenReturn(TransactionResult(
                success = true,
                transactionHash = "0xabcdef123456789"
            ))
        }

        whenever(contractServiceClient.updateContractWithDeployment(
            contractId = eq(contractId),
            chainAddress = eq(request.contractAddress),
            chainId = eq("43113"),
            buyerAddress = eq(request.userWalletAddress),
            request = any()
        )).thenReturn(Mono.just(mock()))

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.transactionHash").value("0xabcdef123456789"))
            .andExpect(jsonPath("$.error").doesNotExist())

        // Verify interactions
        verify(contractServiceClient).getContract(eq(contractId), any())
        verify(contractServiceClient).updateContractStatus(eq(contractId), eq("IN-PROCESS"), any())
        runBlocking {
            verify(escrowTransactionService).depositFundsWithGasTransfer(
                eq(request.userWalletAddress), 
                eq(request.signedTransaction)
            )
        }
        verify(contractServiceClient).updateContractWithDeployment(
            contractId = eq(contractId),
            chainAddress = eq(request.contractAddress),
            chainId = eq("43113"),
            buyerAddress = eq(request.userWalletAddress),
            request = any()
        )
    }

    @Test
    fun `deposit funds with invalid contract state should fail`() {
        // Arrange
        val contractId = "507f1f77bcf86cd799439011"
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            contractId = contractId,
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        // Mock contract service returning non-OK state
        val contractData = mapOf(
            "id" to contractId,
            "state" to "IN-PROCESS",  // Invalid state for deposit
            "buyerAddress" to request.userWalletAddress,
            "amount" to "1000000"
        )

        whenever(contractServiceClient.getContract(eq(contractId), any()))
            .thenReturn(Mono.just(contractData))

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.transactionHash").doesNotExist())
            .andExpect(jsonPath("$.error").value("Contract state is 'IN-PROCESS', expected 'OK'"))

        // Verify only getContract was called, no further processing
        verify(contractServiceClient).getContract(eq(contractId), any())
        verify(contractServiceClient, never()).updateContractStatus(any(), any(), any())
        runBlocking {
            verify(escrowTransactionService, never()).depositFundsWithGasTransfer(any(), any())
        }
    }

    @Test
    fun `deposit funds with null contract state should fail`() {
        // Arrange
        val contractId = "507f1f77bcf86cd799439011"
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            contractId = contractId,
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        // Mock contract service returning contract without state field
        val contractData = mapOf(
            "id" to contractId,
            "buyerAddress" to request.userWalletAddress,
            "amount" to "1000000"
            // No 'state' field - this simulates the original bug
        )

        whenever(contractServiceClient.getContract(eq(contractId), any()))
            .thenReturn(Mono.just(contractData))

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.transactionHash").doesNotExist())
            .andExpect(jsonPath("$.error").value("Contract state is 'null', expected 'OK'"))

        // Verify only getContract was called
        verify(contractServiceClient).getContract(eq(contractId), any())
        verify(contractServiceClient, never()).updateContractStatus(any(), any(), any())
    }

    @Test
    fun `deposit funds with contract not found should fail`() {
        // Arrange
        val contractId = "507f1f77bcf86cd799439011"
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            contractId = contractId,
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        whenever(contractServiceClient.getContract(eq(contractId), any()))
            .thenReturn(Mono.empty())

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.transactionHash").doesNotExist())
            .andExpect(jsonPath("$.error").value("Contract not found with ID: $contractId"))

        verify(contractServiceClient).getContract(eq(contractId), any())
        verify(contractServiceClient, never()).updateContractStatus(any(), any(), any())
    }

    @Test
    fun `deposit funds without contractId should proceed without validation`() {
        // Arrange
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            contractId = null,  // No contract ID provided
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        runBlocking {
            whenever(escrowTransactionService.depositFundsWithGasTransfer(
                eq(request.userWalletAddress),
                eq(request.signedTransaction)
            )).thenReturn(TransactionResult(
                success = true,
                transactionHash = "0xabcdef123456789"
            ))
        }

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.transactionHash").value("0xabcdef123456789"))

        // Verify no contract service calls were made
        verify(contractServiceClient, never()).getContract(any(), any())
        verify(contractServiceClient, never()).updateContractStatus(any(), any(), any())
        verify(contractServiceClient, never()).updateContractWithDeployment(any(), any(), any(), any(), any())
        
        // But deposit should still be processed
        runBlocking {
            verify(escrowTransactionService).depositFundsWithGasTransfer(
                eq(request.userWalletAddress), 
                eq(request.signedTransaction)
            )
        }
    }

    @Test
    fun `deposit funds with blockchain failure should not update contract service`() {
        // Arrange
        val contractId = "507f1f77bcf86cd799439011"
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            contractId = contractId,
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        val contractData = mapOf(
            "id" to contractId,
            "state" to "OK",
            "buyerAddress" to request.userWalletAddress,
            "amount" to "1000000"
        )

        whenever(contractServiceClient.getContract(eq(contractId), any()))
            .thenReturn(Mono.just(contractData))

        whenever(contractServiceClient.updateContractStatus(eq(contractId), eq("IN-PROCESS"), any()))
            .thenReturn(Mono.just(mapOf("state" to "IN-PROCESS")))

        // Mock blockchain failure
        runBlocking {
            whenever(escrowTransactionService.depositFundsWithGasTransfer(
                eq(request.userWalletAddress),
                eq(request.signedTransaction)
            )).thenReturn(TransactionResult(
                success = false,
                transactionHash = null,
                error = "Insufficient gas"
            ))
        }

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.transactionHash").doesNotExist())
            .andExpect(jsonPath("$.error").value("Insufficient gas"))

        // Verify contract was set to IN-PROCESS but no successful deployment update
        verify(contractServiceClient).getContract(eq(contractId), any())
        verify(contractServiceClient).updateContractStatus(eq(contractId), eq("IN-PROCESS"), any())
        verify(contractServiceClient, never()).updateContractWithDeployment(any(), any(), any(), any(), any())
    }
}