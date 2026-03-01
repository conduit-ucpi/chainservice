package com.conduit.chainservice.escrow

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.escrow.models.CheckAndActivateRequest
import com.conduit.chainservice.escrow.models.CheckAndActivateResponse
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.ContractServiceClient
import com.conduit.chainservice.service.EmailServiceClient
import com.conduit.chainservice.service.GasPayerServiceClient
import com.conduit.chainservice.service.WebhookService
import com.conduit.chainservice.model.TransactionResult
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import reactor.core.publisher.Mono
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Function
import org.web3j.crypto.Hash

/**
 * Tests for the checkAndActivate endpoint and related service method.
 */
@DisplayName("Check and Activate Tests")
class CheckAndActivateTest {

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
    private lateinit var webhookService: WebhookService

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
            webhookService,
            escrowProperties
        )

        // Use reflection to set @Value properties
        val chainIdField = EscrowController::class.java.getDeclaredField("chainId")
        chainIdField.isAccessible = true
        chainIdField.set(escrowController, "8453")

        val serviceLinkField = EscrowController::class.java.getDeclaredField("serviceLink")
        serviceLinkField.isAccessible = true
        serviceLinkField.set(escrowController, "https://test.conduit.com")

        mockMvc = MockMvcBuilders.standaloneSetup(escrowController).build()
    }

    @Test
    @DisplayName("Valid check-and-activate request returns success response")
    fun `check and activate with valid request returns success`() {
        // Arrange
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val request = CheckAndActivateRequest(contractAddress = contractAddress)
        val txHash = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"

        runBlocking {
            whenever(escrowTransactionService.checkAndActivateAsGasPayer(contractAddress))
                .thenReturn(TransactionResult(
                    success = true,
                    transactionHash = txHash
                ))
        }

        whenever(contractServiceClient.notifyDeposit(eq(contractAddress), any()))
            .thenReturn(Mono.just(mapOf("status" to "notified")))

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/check-and-activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.transactionHash").value(txHash))
            .andExpect(jsonPath("$.error").doesNotExist())

        // Verify service call was made
        runBlocking {
            verify(escrowTransactionService).checkAndActivateAsGasPayer(contractAddress)
        }

        // Verify contract service was notified
        verify(contractServiceClient).notifyDeposit(eq(contractAddress), any())
    }

    @Test
    @DisplayName("Failed check-and-activate returns bad request with error")
    fun `check and activate with service failure returns bad request`() {
        // Arrange
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val request = CheckAndActivateRequest(contractAddress = contractAddress)

        runBlocking {
            whenever(escrowTransactionService.checkAndActivateAsGasPayer(contractAddress))
                .thenReturn(TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = "Insufficient balance in contract"
                ))
        }

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/check-and-activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.transactionHash").doesNotExist())
            .andExpect(jsonPath("$.error").value("Insufficient balance in contract"))

        // Verify contract service was NOT notified on failure
        verify(contractServiceClient, never()).notifyDeposit(any(), any())
    }

    @Test
    @DisplayName("Exception in service returns internal server error")
    fun `check and activate with exception returns internal server error`() {
        // Arrange
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val request = CheckAndActivateRequest(contractAddress = contractAddress)

        runBlocking {
            whenever(escrowTransactionService.checkAndActivateAsGasPayer(contractAddress))
                .thenThrow(RuntimeException("Unexpected blockchain error"))
        }

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/check-and-activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Unexpected blockchain error"))
    }

    @Test
    @DisplayName("Contract service notification failure does not fail the response")
    fun `check and activate succeeds even when contract service notification fails`() {
        // Arrange
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val request = CheckAndActivateRequest(contractAddress = contractAddress)
        val txHash = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"

        runBlocking {
            whenever(escrowTransactionService.checkAndActivateAsGasPayer(contractAddress))
                .thenReturn(TransactionResult(
                    success = true,
                    transactionHash = txHash
                ))
        }

        whenever(contractServiceClient.notifyDeposit(eq(contractAddress), any()))
            .thenThrow(RuntimeException("Contract service unavailable"))

        // Act & Assert - Should still return OK since blockchain tx succeeded
        mockMvc.perform(
            post("/api/chain/check-and-activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.transactionHash").value(txHash))
    }

    @Test
    @DisplayName("Invalid contract address format returns validation error")
    fun `check and activate with invalid contract address returns validation error`() {
        // Arrange - invalid contract address (not 0x prefixed hex)
        val invalidRequest = mapOf("contractAddress" to "not-a-valid-address")

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/check-and-activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("Missing contract address returns validation error")
    fun `check and activate with missing contract address returns validation error`() {
        // Arrange - empty request body
        val emptyRequest = mapOf<String, String>()

        // Act & Assert
        mockMvc.perform(
            post("/api/chain/check-and-activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyRequest))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("checkAndActivate function builds correct function call with empty inputs")
    fun `checkAndActivate function encoding has correct selector and no parameters`() {
        // Build the same function that EscrowTransactionService would build
        val function = Function(
            "checkAndActivate",
            emptyList(),
            emptyList()
        )

        val encodedFunction = FunctionEncoder.encode(function)

        // Calculate expected selector: keccak256("checkAndActivate()")
        val expectedSelector = "0x" + Hash.sha3String("checkAndActivate()").substring(2, 10)
        val actualSelector = encodedFunction.substring(0, 10)

        // Verify function selector matches expected
        assert(expectedSelector == actualSelector) {
            "Function selector mismatch: expected=$expectedSelector, actual=$actualSelector"
        }

        // checkAndActivate takes no parameters, so encoding should be just the selector
        assert(encodedFunction.length == 10) {
            "checkAndActivate() encoding should only be selector (0x + 8 hex chars), got length: ${encodedFunction.length}"
        }
    }
}
