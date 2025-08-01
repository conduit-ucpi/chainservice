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
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

/**
 * Comprehensive error scenario tests for the deposit-funds endpoint.
 * Tests edge cases, validation failures, and error handling.
 */
class DepositFundsErrorScenariosTest {

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
    fun `deposit funds with invalid JSON should return 400`() {
        // Act & Assert
        mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}")
        )
            .andExpect(status().isBadRequest())
    }

    @Test
    fun `deposit funds with missing required fields should return 400`() {
        // Arrange - Missing contractAddress
        val invalidRequest = """
        {
            "userWalletAddress": "0x9876543210fedcba9876543210fedcba98765432",
            "signedTransaction": "0xf86c8082520894..."
        }
        """.trimIndent()

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest)
        )
            .andExpect(status().isBadRequest())
    }

    @Test
    fun `deposit funds with invalid address format should return 500 due to web3j validation`() {
        // Arrange
        val requestWithInvalidAddress = DepositFundsRequest(
            contractAddress = "invalid-address",  // Invalid format
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            contractId = null,
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        // Mock the service to throw an exception due to invalid address
        runBlocking {
            whenever(escrowTransactionService.depositFundsWithGasTransfer(any(), any()))
                .thenThrow(IllegalArgumentException("Invalid address format"))
        }

        // Act & Assert - The controller doesn't validate addresses, so it will fail at service level
        mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestWithInvalidAddress))
        )
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Invalid address format"))
    }

    @Test
    fun `deposit funds with contract service 404 should return appropriate error`() {
        // Arrange
        val contractId = "nonexistent-contract-id"
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            contractId = contractId,
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        whenever(contractServiceClient.getContract(eq(contractId), any()))
            .thenReturn(Mono.error(WebClientResponseException.create(
                404, 
                "Not Found", 
                HttpHeaders.EMPTY, 
                ByteArray(0), 
                null
            )))

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Failed to validate contract: 404 Not Found"))

        verify(contractServiceClient).getContract(eq(contractId), any())
        verify(contractServiceClient, never()).updateContractStatus(any(), any(), any())
    }

    @Test
    fun `deposit funds with contract in DISPUTED state should fail`() {
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
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        val contractData = mapOf(
            "id" to contractId,
            "state" to "DISPUTED",  // Contract is in dispute
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
            .andExpect(jsonPath("$.error").value("Contract state is 'DISPUTED', expected 'OK'"))

        verify(contractServiceClient).getContract(eq(contractId), any())
        verify(contractServiceClient, never()).updateContractStatus(any(), any(), any())
    }

    @Test
    fun `deposit funds with contract in EXPIRED state should fail`() {
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
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        val contractData = mapOf(
            "id" to contractId,
            "state" to "EXPIRED",  // Contract has expired
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
            .andExpect(jsonPath("$.error").value("Contract state is 'EXPIRED', expected 'OK'"))
    }

    @Test
    fun `deposit funds with empty contract state should fail`() {
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
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        val contractData = mapOf(
            "id" to contractId,
            "state" to "",  // Empty state
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
            .andExpect(jsonPath("$.error").value("Contract state is '', expected 'OK'"))
    }

    @Test
    fun `deposit funds with malformed contract data should handle gracefully`() {
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
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        // Contract data with unexpected structure
        val malformedContractData = mapOf(
            "id" to contractId,
            "state" to mapOf("nested" to "OK"),  // State as nested object instead of string
            "buyerAddress" to request.userWalletAddress
        )

        whenever(contractServiceClient.getContract(eq(contractId), any()))
            .thenReturn(Mono.just(malformedContractData))

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Contract state is 'null', expected 'OK'"))  // Casting fails, becomes null
    }

    @Test
    fun `deposit funds with blockchain service completely unavailable should return 500`() {
        // Arrange
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            contractId = null,  // Skip contract validation
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        // Blockchain service throws unexpected exception
        runBlocking {
            whenever(escrowTransactionService.depositFundsWithGasTransfer(any(), any()))
                .thenThrow(RuntimeException("Blockchain node unreachable"))
        }

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Blockchain node unreachable"))
    }

    @Test
    fun `deposit funds with contract service timeout should handle gracefully`() {
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
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        whenever(contractServiceClient.getContract(eq(contractId), any()))
            .thenReturn(Mono.error(java.util.concurrent.TimeoutException("Contract service timeout")))

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/deposit-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Failed to validate contract: java.util.concurrent.TimeoutException: Contract service timeout"))

        verify(contractServiceClient).getContract(eq(contractId), any())
    }

    @Test
    fun `deposit funds with very long transaction hash should be handled correctly`() {
        // Arrange
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0x" + "f".repeat(1000),  // Very long transaction
            contractId = null,
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
        )

        runBlocking {
            whenever(escrowTransactionService.depositFundsWithGasTransfer(any(), any()))
                .thenReturn(TransactionResult(
                    success = true,
                    transactionHash = "0x" + "a".repeat(64)  // Standard length hash
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
            .andExpect(jsonPath("$.transactionHash").value("0x" + "a".repeat(64)))
    }
}