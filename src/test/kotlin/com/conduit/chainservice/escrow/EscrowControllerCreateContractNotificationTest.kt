package com.conduit.chainservice.escrow

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.escrow.models.CreateContractRequest
import com.conduit.chainservice.escrow.models.CreateContractResponse
import com.conduit.chainservice.escrow.models.ContractCreationResult
import com.conduit.chainservice.service.ContractServiceClient
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.EmailServiceClient
import com.conduit.chainservice.service.GasPayerServiceClient
import com.fasterxml.jackson.databind.ObjectMapper
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
import java.math.BigInteger

/**
 * Unit tests for contract creation with contractservice notification functionality
 */
class EscrowControllerCreateContractNotificationTest {

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
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        escrowController = EscrowController(
            escrowTransactionService,
            contractQueryService,
            contractServiceClient,
            emailServiceClient,
            gasPayerServiceClient,
            escrowProperties
        )
        mockMvc = MockMvcBuilders.standaloneSetup(escrowController).build()
    }

    @Test
    fun `should notify contractservice when contractserviceId is provided and contract creation succeeds`() {
        // Arrange
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val contractserviceId = "507f1f77bcf86cd799439011"
        val request = CreateContractRequest(
            tokenAddress = "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
            buyer = "0x1111111111111111111111111111111111111111",
            seller = "0x2222222222222222222222222222222222222222",
            amount = BigInteger("1000000"),
            expiryTimestamp = 1735689600L,
            description = "Test contract",
            contractserviceId = contractserviceId
        )

        val creationResult = ContractCreationResult(
            success = true,
            transactionHash = "0xabc123",
            contractAddress = contractAddress
        )

        // Mock escrow transaction service
        runBlocking {
            whenever(escrowTransactionService.createContract(any(), any(), any(), any(), any(), any()))
                .thenReturn(creationResult)
        }

        // Mock contract service client notification
        whenever(contractServiceClient.notifyContractCreation(eq(contractserviceId), eq(contractAddress), any()))
            .thenReturn(Mono.just(mapOf("status" to "success")))

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/create-contract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.contractAddress").value(contractAddress))
            .andExpect(jsonPath("$.transactionHash").value("0xabc123"))

        // Verify notification was called
        verify(contractServiceClient).notifyContractCreation(
            eq(contractserviceId),
            eq(contractAddress),
            any()
        )
    }

    @Test
    fun `should always notify contractservice since contractserviceId is required`() {
        // Arrange
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val contractserviceId = "507f1f77bcf86cd799439012"
        val request = CreateContractRequest(
            tokenAddress = "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
            buyer = "0x1111111111111111111111111111111111111111",
            seller = "0x2222222222222222222222222222222222222222",
            amount = BigInteger("1000000"),
            expiryTimestamp = 1735689600L,
            description = "Test contract",
            contractserviceId = contractserviceId
        )

        val creationResult = ContractCreationResult(
            success = true,
            transactionHash = "0xabc123",
            contractAddress = contractAddress
        )

        // Mock escrow transaction service
        runBlocking {
            whenever(escrowTransactionService.createContract(any(), any(), any(), any(), any(), any()))
                .thenReturn(creationResult)
        }

        // Mock contract service client notification
        whenever(contractServiceClient.notifyContractCreation(eq(contractserviceId), eq(contractAddress), any()))
            .thenReturn(Mono.just(mapOf("status" to "success")))

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/create-contract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.contractAddress").value(contractAddress))

        // Verify notification was called
        verify(contractServiceClient).notifyContractCreation(
            eq(contractserviceId),
            eq(contractAddress),
            any()
        )
    }

    @Test
    fun `should not notify contractservice when contract creation fails`() {
        // Arrange
        val contractserviceId = "507f1f77bcf86cd799439011"
        val request = CreateContractRequest(
            tokenAddress = "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
            buyer = "0x1111111111111111111111111111111111111111",
            seller = "0x2222222222222222222222222222222222222222",
            amount = BigInteger("1000000"),
            expiryTimestamp = 1735689600L,
            description = "Test contract",
            contractserviceId = contractserviceId
        )

        val creationResult = ContractCreationResult(
            success = false,
            transactionHash = null,
            contractAddress = null,
            error = "Contract creation failed"
        )

        // Mock escrow transaction service
        runBlocking {
            whenever(escrowTransactionService.createContract(any(), any(), any(), any(), any(), any()))
                .thenReturn(creationResult)
        }

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/create-contract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Contract creation failed"))

        // Verify notification was NOT called due to creation failure
        verify(contractServiceClient, never()).notifyContractCreation(any(), any(), any())
    }

    @Test
    fun `should continue successfully even if contractservice notification fails`() {
        // Arrange
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val contractserviceId = "507f1f77bcf86cd799439011"
        val request = CreateContractRequest(
            tokenAddress = "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
            buyer = "0x1111111111111111111111111111111111111111",
            seller = "0x2222222222222222222222222222222222222222",
            amount = BigInteger("1000000"),
            expiryTimestamp = 1735689600L,
            description = "Test contract",
            contractserviceId = contractserviceId
        )

        val creationResult = ContractCreationResult(
            success = true,
            transactionHash = "0xabc123",
            contractAddress = contractAddress
        )

        // Mock escrow transaction service
        runBlocking {
            whenever(escrowTransactionService.createContract(any(), any(), any(), any(), any(), any()))
                .thenReturn(creationResult)
        }

        // Mock contract service client notification failure
        whenever(contractServiceClient.notifyContractCreation(eq(contractserviceId), eq(contractAddress), any()))
            .thenReturn(Mono.error(RuntimeException("Notification failed")))

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/create-contract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk) // Should still return success
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.contractAddress").value(contractAddress))
            .andExpect(jsonPath("$.transactionHash").value("0xabc123"))

        // Verify notification was attempted
        verify(contractServiceClient).notifyContractCreation(
            eq(contractserviceId),
            eq(contractAddress),
            any()
        )
    }

    @Test
    fun `should fail validation when contractserviceId is missing`() {
        // Arrange - request without contractserviceId should fail validation
        val request = """
            {
                "tokenAddress": "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
                "buyer": "0x1111111111111111111111111111111111111111",
                "seller": "0x2222222222222222222222222222222222222222",
                "amount": "1000000",
                "expiryTimestamp": 1735689600,
                "description": "Test contract"
            }
        """.trimIndent()

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/create-contract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
            .andExpect(status().isBadRequest)

        // Verify no methods were called due to validation failure
        verifyNoInteractions(escrowTransactionService)
        verifyNoInteractions(contractServiceClient)
    }
}