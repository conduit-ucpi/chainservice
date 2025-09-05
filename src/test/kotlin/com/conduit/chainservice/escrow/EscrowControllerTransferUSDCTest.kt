package com.conduit.chainservice.escrow

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.escrow.models.TransferUSDCRequest
import com.conduit.chainservice.escrow.models.TransferUSDCResponse
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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Unit tests for the transfer-usdc endpoint
 */
class EscrowControllerTransferUSDCTest {

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
    fun `transfer USDC with valid request and authentication should succeed`() {
        // Arrange
        val request = TransferUSDCRequest(
            recipientAddress = "0x1234567890abcdef1234567890abcdef12345678",
            amount = "100.50",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894..."
        )
        
        val mockHttpRequest = MockHttpServletRequest()
        mockHttpRequest.setAttribute("userWallet", "0x9876543210fedcba9876543210fedcba98765432")
        mockHttpRequest.setAttribute("userType", "user")
        mockHttpRequest.setAttribute("userId", "user123")
        
        val expectedResult = TransactionResult(
            success = true,
            transactionHash = "0xabc123def456",
            error = null
        )
        
        runBlocking {
            whenever(escrowTransactionService.transferUSDCWithGasTransfer(
                eq("0x9876543210fedcba9876543210fedcba98765432"),
                eq("0xf86c8082520894...")
            )).thenReturn(expectedResult)
        }
        
        // Act
        val response = escrowController.transferUSDC(request, mockHttpRequest)
        
        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val responseBody = response.body as TransferUSDCResponse
        assertTrue(responseBody.success)
        assertEquals("0xabc123def456", responseBody.transactionHash)
        assertEquals("USDC transfer successful", responseBody.message)
        assertEquals(null, responseBody.error)
    }

    @Test
    fun `transfer USDC with mismatched wallet address should return unauthorized`() {
        // Arrange
        val request = TransferUSDCRequest(
            recipientAddress = "0x1234567890abcdef1234567890abcdef12345678",
            amount = "100.50",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894..."
        )
        
        val mockHttpRequest = MockHttpServletRequest()
        mockHttpRequest.setAttribute("userWallet", "0xDIFFERENT567890abcdef1234567890abcdef1234") // Different wallet
        mockHttpRequest.setAttribute("userType", "user")
        mockHttpRequest.setAttribute("userId", "user123")
        
        // Act
        val response = escrowController.transferUSDC(request, mockHttpRequest)
        
        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        val responseBody = response.body as TransferUSDCResponse
        assertFalse(responseBody.success)
        assertEquals(null, responseBody.transactionHash)
        assertEquals("Authentication failed", responseBody.message)
        assertEquals("User wallet address does not match authenticated user", responseBody.error)
    }

    @Test
    fun `transfer USDC with no authenticated wallet should return unauthorized`() {
        // Arrange
        val request = TransferUSDCRequest(
            recipientAddress = "0x1234567890abcdef1234567890abcdef12345678",
            amount = "100.50",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894..."
        )
        
        val mockHttpRequest = MockHttpServletRequest()
        // No userWallet attribute set
        mockHttpRequest.setAttribute("userType", "user")
        mockHttpRequest.setAttribute("userId", "user123")
        
        // Act
        val response = escrowController.transferUSDC(request, mockHttpRequest)
        
        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        val responseBody = response.body as TransferUSDCResponse
        assertFalse(responseBody.success)
        assertEquals(null, responseBody.transactionHash)
        assertEquals("Authentication required", responseBody.message)
        assertEquals("No authenticated user wallet found", responseBody.error)
    }

    @Test
    fun `transfer USDC when transaction fails should return bad request`() {
        // Arrange
        val request = TransferUSDCRequest(
            recipientAddress = "0x1234567890abcdef1234567890abcdef12345678",
            amount = "100.50",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894..."
        )
        
        val mockHttpRequest = MockHttpServletRequest()
        mockHttpRequest.setAttribute("userWallet", "0x9876543210fedcba9876543210fedcba98765432")
        mockHttpRequest.setAttribute("userType", "user")
        mockHttpRequest.setAttribute("userId", "user123")
        
        val expectedResult = TransactionResult(
            success = false,
            transactionHash = null,
            error = "Insufficient balance"
        )
        
        runBlocking {
            whenever(escrowTransactionService.transferUSDCWithGasTransfer(
                eq("0x9876543210fedcba9876543210fedcba98765432"),
                eq("0xf86c8082520894...")
            )).thenReturn(expectedResult)
        }
        
        // Act
        val response = escrowController.transferUSDC(request, mockHttpRequest)
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val responseBody = response.body as TransferUSDCResponse
        assertFalse(responseBody.success)
        assertEquals(null, responseBody.transactionHash)
        assertEquals("USDC transfer failed", responseBody.message)
        assertEquals("Insufficient balance", responseBody.error)
    }

    @Test
    fun `transfer USDC when service throws exception should return internal server error`() {
        // Arrange
        val request = TransferUSDCRequest(
            recipientAddress = "0x1234567890abcdef1234567890abcdef12345678",
            amount = "100.50",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894..."
        )
        
        val mockHttpRequest = MockHttpServletRequest()
        mockHttpRequest.setAttribute("userWallet", "0x9876543210fedcba9876543210fedcba98765432")
        mockHttpRequest.setAttribute("userType", "user")
        mockHttpRequest.setAttribute("userId", "user123")
        
        runBlocking {
            whenever(escrowTransactionService.transferUSDCWithGasTransfer(
                any(),
                any()
            )).thenThrow(RuntimeException("Network error"))
        }
        
        // Act
        val response = escrowController.transferUSDC(request, mockHttpRequest)
        
        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        val responseBody = response.body as TransferUSDCResponse
        assertFalse(responseBody.success)
        assertEquals(null, responseBody.transactionHash)
        assertEquals("Internal server error", responseBody.message)
        assertEquals("Network error", responseBody.error)
    }

    @Test
    fun `transfer USDC via MockMvc should work with proper authentication`() {
        // Arrange
        val request = TransferUSDCRequest(
            recipientAddress = "0x1234567890abcdef1234567890abcdef12345678",
            amount = "100.50",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894..."
        )
        
        val expectedResult = TransactionResult(
            success = true,
            transactionHash = "0xabc123def456",
            error = null
        )
        
        runBlocking {
            whenever(escrowTransactionService.transferUSDCWithGasTransfer(
                eq("0x9876543210fedcba9876543210fedcba98765432"),
                eq("0xf86c8082520894...")
            )).thenReturn(expectedResult)
        }
        
        // Act & Assert
        mockMvc.perform(post("/api/chain/transfer-usdc")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
            .requestAttr("userWallet", "0x9876543210fedcba9876543210fedcba98765432")
            .requestAttr("userType", "user")
            .requestAttr("userId", "user123"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.transactionHash").value("0xabc123def456"))
            .andExpect(jsonPath("$.message").value("USDC transfer successful"))
            .andExpect(jsonPath("$.error").doesNotExist())
    }
}