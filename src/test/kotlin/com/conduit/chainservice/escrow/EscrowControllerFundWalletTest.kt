package com.conduit.chainservice.escrow

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.escrow.models.FundWalletRequest
import com.conduit.chainservice.escrow.models.FundWalletResponse
import com.conduit.chainservice.service.ContractServiceClient
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.EmailServiceClient
import com.conduit.chainservice.service.GasPayerServiceClient
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.math.BigInteger

/**
 * Unit tests for the fund-wallet endpoint
 */
class EscrowControllerFundWalletTest {

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
    fun `fund wallet with valid request should succeed`() {
        // Arrange
        val request = FundWalletRequest(
            walletAddress = "0x1234567890abcdef1234567890abcdef12345678",
            totalAmountNeededWei = BigInteger("1000000000000000000") // 1 ETH in wei
        )
        
        val expectedResult = FundWalletResponse(
            success = true,
            message = "Wallet funded successfully",
            error = null
        )
        
        runBlocking {
            whenever(gasPayerServiceClient.fundWallet(
                eq("0x1234567890abcdef1234567890abcdef12345678"),
                eq(BigInteger("1000000000000000000"))
            )).thenReturn(expectedResult)
        }
        
        // Act
        val response = escrowController.fundWallet(request)
        
        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val responseBody = response.body as FundWalletResponse
        assertTrue(responseBody.success)
        assertEquals("Wallet funded successfully", responseBody.message)
        assertEquals(null, responseBody.error)
    }

    @Test
    fun `fund wallet with invalid wallet address should return bad request`() {
        // Arrange
        val request = FundWalletRequest(
            walletAddress = "invalid-address", // Invalid format
            totalAmountNeededWei = BigInteger("1000000000000000000")
        )
        
        // Act
        val response = escrowController.fundWallet(request)
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val responseBody = response.body as FundWalletResponse
        assertFalse(responseBody.success)
        assertEquals("Invalid wallet address format", responseBody.message)
        assertEquals("Wallet address must be a 42 character hex string starting with 0x", responseBody.error)
    }

    @Test
    fun `fund wallet with short wallet address should return bad request`() {
        // Arrange
        val request = FundWalletRequest(
            walletAddress = "0x1234", // Too short
            totalAmountNeededWei = BigInteger("1000000000000000000")
        )
        
        // Act
        val response = escrowController.fundWallet(request)
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val responseBody = response.body as FundWalletResponse
        assertFalse(responseBody.success)
        assertEquals("Invalid wallet address format", responseBody.message)
        assertEquals("Wallet address must be a 42 character hex string starting with 0x", responseBody.error)
    }

    @Test
    fun `fund wallet when gas payer service fails should return bad request`() {
        // Arrange
        val request = FundWalletRequest(
            walletAddress = "0x1234567890abcdef1234567890abcdef12345678",
            totalAmountNeededWei = BigInteger("1000000000000000000")
        )
        
        val expectedResult = FundWalletResponse(
            success = false,
            message = "Funding failed",
            error = "Insufficient balance in gas payer service"
        )
        
        runBlocking {
            whenever(gasPayerServiceClient.fundWallet(
                eq("0x1234567890abcdef1234567890abcdef12345678"),
                eq(BigInteger("1000000000000000000"))
            )).thenReturn(expectedResult)
        }
        
        // Act
        val response = escrowController.fundWallet(request)
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val responseBody = response.body as FundWalletResponse
        assertFalse(responseBody.success)
        assertEquals("Funding failed", responseBody.message)
        assertEquals("Insufficient balance in gas payer service", responseBody.error)
    }

    @Test
    fun `fund wallet when gas payer service throws exception should return internal server error`() {
        // Arrange
        val request = FundWalletRequest(
            walletAddress = "0x1234567890abcdef1234567890abcdef12345678",
            totalAmountNeededWei = BigInteger("1000000000000000000")
        )
        
        runBlocking {
            whenever(gasPayerServiceClient.fundWallet(
                any(),
                any()
            )).thenThrow(RuntimeException("Network error"))
        }
        
        // Act
        val response = escrowController.fundWallet(request)
        
        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        val responseBody = response.body as FundWalletResponse
        assertFalse(responseBody.success)
        assertEquals("Internal server error", responseBody.message)
        assertEquals("Network error", responseBody.error)
    }

    @Test
    fun `fund wallet via MockMvc should work with valid request`() {
        // Arrange
        val request = FundWalletRequest(
            walletAddress = "0x1234567890abcdef1234567890abcdef12345678",
            totalAmountNeededWei = BigInteger("1000000000000000000")
        )
        
        val expectedResult = FundWalletResponse(
            success = true,
            message = "Wallet funded successfully",
            error = null
        )
        
        runBlocking {
            whenever(gasPayerServiceClient.fundWallet(
                eq("0x1234567890abcdef1234567890abcdef12345678"),
                eq(BigInteger("1000000000000000000"))
            )).thenReturn(expectedResult)
        }
        
        // Act & Assert
        mockMvc.perform(post("/api/chain/fund-wallet")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Wallet funded successfully"))
            .andExpect(jsonPath("$.error").doesNotExist())
    }

    @Test
    fun `fund wallet via MockMvc with invalid address should return bad request`() {
        // Arrange
        val request = FundWalletRequest(
            walletAddress = "invalid-address",
            totalAmountNeededWei = BigInteger("1000000000000000000")
        )
        
        // Act & Assert
        mockMvc.perform(post("/api/chain/fund-wallet")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Invalid wallet address format"))
            .andExpect(jsonPath("$.error").value("Wallet address must be a 42 character hex string starting with 0x"))
    }
}