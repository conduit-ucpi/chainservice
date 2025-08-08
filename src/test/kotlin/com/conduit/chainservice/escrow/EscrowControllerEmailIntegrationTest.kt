package com.conduit.chainservice.escrow

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.escrow.models.RaiseDisputeRequest
import com.conduit.chainservice.escrow.models.ResolveDisputeRequest
import com.conduit.chainservice.escrow.models.DepositFundsRequest
import com.conduit.chainservice.escrow.models.AdminResolveContractRequest
import com.conduit.chainservice.controller.AdminController
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.ContractServiceClient
import com.conduit.chainservice.service.EmailServiceClient
import com.conduit.chainservice.service.SendEmailResponse
import com.conduit.chainservice.service.CacheInvalidationService
import com.fasterxml.jackson.databind.ObjectMapper
import com.utility.chainservice.models.TransactionResult
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
 * Integration tests for email notification functionality with conditional validation.
 * Tests that emails are only sent when all required fields are provided.
 */
class EscrowControllerEmailIntegrationTest {

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

    @Mock
    private lateinit var escrowProperties: EscrowProperties
    
    @Mock
    private lateinit var cacheInvalidationService: CacheInvalidationService

    private lateinit var escrowController: EscrowController
    private lateinit var adminController: AdminController

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        escrowController = EscrowController(
            escrowTransactionService,
            contractQueryService,
            contractServiceClient,
            escrowServicePlugin,
            emailServiceClient,
            escrowProperties
        )

        // Use reflection to set serviceLink and chainId since they are @Value properties
        val serviceLinkField = EscrowController::class.java.getDeclaredField("serviceLink")
        serviceLinkField.isAccessible = true
        serviceLinkField.set(escrowController, "https://test.example.com")

        val chainIdField = EscrowController::class.java.getDeclaredField("chainId")
        chainIdField.isAccessible = true
        chainIdField.set(escrowController, "43113")
        
        // Create AdminController with the escrowController
        adminController = AdminController(
            escrowController,
            contractQueryService,
            cacheInvalidationService
        )

        mockMvc = MockMvcBuilders.standaloneSetup(adminController, escrowController).build()
    }

    @Test
    fun `raise dispute with complete email fields should send email notifications`() {
        // Arrange
        val request = RaiseDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = "100.00 USDC",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractDescription = "Test escrow contract",
            productName = "Test Product"
        )

        runBlocking {
            whenever(escrowTransactionService.raiseDisputeWithGasTransfer(
                eq(request.userWalletAddress),
                eq(request.signedTransaction)
            )).thenReturn(TransactionResult(
                success = true,
                transactionHash = "0xabc123...",
                error = null
            ))
        }

        whenever(emailServiceClient.sendDisputeRaised(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.just(SendEmailResponse(true, "msg-123", "Email sent successfully")))

        // Act & Assert
        mockMvc.perform(post("/api/chain/raise-dispute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        // Verify email service was called twice (buyer and seller)
        verify(emailServiceClient, times(2)).sendDisputeRaised(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )
    }

    @Test
    fun `raise dispute with incomplete email fields should fail validation`() {
        // Arrange - productName is now required, so this test should check validation failure for a different reason
        val request = RaiseDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = "", // Empty amount should fail validation
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractDescription = "Test escrow contract",
            productName = "Test Product"
        )

        // Act & Assert - should fail validation
        mockMvc.perform(post("/api/chain/raise-dispute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest)

        // Verify email service was never called
        verify(emailServiceClient, never()).sendDisputeRaised(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )
        
        // Verify transaction service was never called due to validation failure
        runBlocking {
            verify(escrowTransactionService, never()).raiseDisputeWithGasTransfer(any(), any())
        }
    }

    @Test
    fun `raise dispute with no email fields should skip email notifications`() {
        // Arrange - no email fields provided
        val request = RaiseDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            productName = "Test Product"
        )

        runBlocking {
            whenever(escrowTransactionService.raiseDisputeWithGasTransfer(
                eq(request.userWalletAddress),
                eq(request.signedTransaction)
            )).thenReturn(TransactionResult(
                success = true,
                transactionHash = "0xabc123...",
                error = null
            ))
        }

        // Act & Assert
        mockMvc.perform(post("/api/chain/raise-dispute")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        // Verify email service was never called
        verify(emailServiceClient, never()).sendDisputeRaised(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )
    }

    @Test
    fun `deposit funds with complete email fields should send payment notification`() {
        // Arrange
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            contractDescription = "Test escrow contract",
            amount = "100.00 USDC",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z"
        )

        runBlocking {
            whenever(escrowTransactionService.depositFundsWithGasTransfer(
                eq(request.userWalletAddress),
                eq(request.signedTransaction)
            )).thenReturn(TransactionResult(
                success = true,
                transactionHash = "0xabc123...",
                error = null
            ))
        }

        whenever(emailServiceClient.sendPaymentNotification(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.just(SendEmailResponse(true, "msg-123", "Email sent successfully")))

        // Act & Assert
        mockMvc.perform(post("/api/chain/deposit-funds")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        // Verify email service was called once
        verify(emailServiceClient, times(1)).sendPaymentNotification(
            any(), any(), any(), any(), any(), any(), any(), any()
        )
    }

    @Test
    fun `deposit funds with incomplete email fields should skip payment notification`() {
        // Arrange - missing contractDescription
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = "100.00 USDC",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z"
            // contractDescription is missing
        )

        runBlocking {
            whenever(escrowTransactionService.depositFundsWithGasTransfer(
                eq(request.userWalletAddress),
                eq(request.signedTransaction)
            )).thenReturn(TransactionResult(
                success = true,
                transactionHash = "0xabc123...",
                error = null
            ))
        }

        // Act & Assert
        mockMvc.perform(post("/api/chain/deposit-funds")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        // Verify email service was never called
        verify(emailServiceClient, never()).sendPaymentNotification(
            any(), any(), any(), any(), any(), any(), any(), any()
        )
    }

    @Test
    fun `resolve dispute with complete email fields should send resolved notifications`() {
        // Arrange
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val adminRequest = AdminResolveContractRequest(
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = "100.00 USDC",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractDescription = "Test escrow contract",
            productName = "Test Product",
            sellerActualAmount = "40.00 USDC",
            buyerActualAmount = "60.00 USDC"
        )

        runBlocking {
            whenever(escrowTransactionService.resolveDisputeWithPercentages(
                eq(contractAddress),
                eq(60.0),
                eq(40.0)
            )).thenReturn(TransactionResult(
                success = true,
                transactionHash = "0xabc123...",
                error = null
            ))
        }

        whenever(emailServiceClient.sendDisputeResolved(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.just(SendEmailResponse(true, "msg-123", "Email sent successfully")))

        // Act & Assert
        mockMvc.perform(post("/api/admin/contracts/$contractAddress/resolve")
            .contentType(MediaType.APPLICATION_JSON)
            .requestAttr("userType", "admin")
            .content(objectMapper.writeValueAsString(adminRequest)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        // Verify email service was called twice (buyer and seller)
        verify(emailServiceClient, times(2)).sendDisputeResolved(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )
    }

    @Test
    fun `resolve dispute with custom link should use provided link in email notifications`() {
        // Arrange
        val customLink = "https://custom.example.com/contract/0x1234567890abcdef1234567890abcdef12345678"
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val adminRequest = AdminResolveContractRequest(
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = "100.00 USDC",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractDescription = "Test escrow contract",
            productName = "Test Product",
            sellerActualAmount = "40.00 USDC",
            buyerActualAmount = "60.00 USDC",
            link = customLink
        )

        runBlocking {
            whenever(escrowTransactionService.resolveDisputeWithPercentages(
                eq(contractAddress),
                eq(60.0),
                eq(40.0)
            )).thenReturn(TransactionResult(
                success = true,
                transactionHash = "0xabc123...",
                error = null
            ))
        }

        whenever(emailServiceClient.sendDisputeResolved(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(customLink), any()))
            .thenReturn(Mono.just(SendEmailResponse(true, "msg-123", "Email sent successfully")))

        // Act & Assert
        mockMvc.perform(post("/api/admin/contracts/$contractAddress/resolve")
            .contentType(MediaType.APPLICATION_JSON)
            .requestAttr("userType", "admin")
            .content(objectMapper.writeValueAsString(adminRequest)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        // Verify email service was called twice with the custom link
        verify(emailServiceClient, times(2)).sendDisputeResolved(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(customLink), any()
        )
    }

    @Test
    fun `resolve dispute with incomplete email fields should skip email notification`() {
        // Arrange - missing sellerActualAmount and buyerActualAmount (optional fields needed for email)
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val adminRequest = AdminResolveContractRequest(
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = "100.00 USDC",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractDescription = "Test escrow contract",
            productName = "Test Product"
            // sellerActualAmount and buyerActualAmount are missing (nulls)
        )

        runBlocking {
            whenever(escrowTransactionService.resolveDisputeWithPercentages(
                eq(contractAddress),
                eq(60.0),
                eq(40.0)
            )).thenReturn(com.utility.chainservice.models.TransactionResult(
                success = true,
                transactionHash = "0xabc123...",
                error = null
            ))
        }

        // Act & Assert - should succeed but skip email notification
        mockMvc.perform(post("/api/admin/contracts/$contractAddress/resolve")
            .contentType(MediaType.APPLICATION_JSON)
            .requestAttr("userType", "admin")
            .content(objectMapper.writeValueAsString(adminRequest)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        // Verify email service was never called due to missing fields
        verify(emailServiceClient, never()).sendDisputeResolved(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )
        
        // Verify transaction service was called (business logic succeeded)
        runBlocking {
            verify(escrowTransactionService, times(1)).resolveDisputeWithPercentages(
                eq(contractAddress), eq(60.0), eq(40.0)
            )
        }
    }
}