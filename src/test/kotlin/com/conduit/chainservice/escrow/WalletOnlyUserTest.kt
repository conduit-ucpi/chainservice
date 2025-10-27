package com.conduit.chainservice.escrow

import com.conduit.chainservice.escrow.models.AdminResolveContractRequest
import com.conduit.chainservice.escrow.models.ResolveDisputeRequest
import com.conduit.chainservice.escrow.validation.EmailFieldValidator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

/**
 * Tests for wallet-only user support in dispute resolution
 * Ensures that email fields can be null/blank for users without email addresses
 */
class WalletOnlyUserTest {

    @Test
    @DisplayName("AdminResolveContractRequest should accept null buyer email for wallet-only buyer")
    fun `AdminResolveContractRequest should support wallet-only buyer with null email`() {
        val request = AdminResolveContractRequest(
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            resolutionNote = "Auto-resolved: Both parties agreed on 60% refund to buyer",
            buyerEmail = null, // Wallet-only user
            sellerEmail = "seller@example.com",
            contractDescription = "Web development services",
            productName = "Premium Web Development Package",
            amount = "100000000",
            currency = "USDC",
            payoutDateTime = "1735689600",
            chainAddress = "0x1234567890abcdef1234567890abcdef12345678",
            buyerActualAmount = "60000000",
            sellerActualAmount = "40000000"
        )

        assertNull(request.buyerEmail)
        assertEquals("seller@example.com", request.sellerEmail)
        assertEquals(60.0, request.buyerPercentage)
    }

    @Test
    @DisplayName("AdminResolveContractRequest should accept null seller email for wallet-only seller")
    fun `AdminResolveContractRequest should support wallet-only seller with null email`() {
        val request = AdminResolveContractRequest(
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            resolutionNote = "Auto-resolved: Both parties agreed on 60% refund to buyer",
            buyerEmail = "buyer@example.com",
            sellerEmail = null, // Wallet-only user
            contractDescription = "Web development services",
            productName = "Premium Web Development Package",
            amount = "100000000",
            currency = "USDC",
            payoutDateTime = "1735689600",
            chainAddress = "0x1234567890abcdef1234567890abcdef12345678",
            buyerActualAmount = "60000000",
            sellerActualAmount = "40000000"
        )

        assertEquals("buyer@example.com", request.buyerEmail)
        assertNull(request.sellerEmail)
        assertEquals(40.0, request.sellerPercentage)
    }

    @Test
    @DisplayName("AdminResolveContractRequest should accept both users as wallet-only")
    fun `AdminResolveContractRequest should support both wallet-only users with null emails`() {
        val request = AdminResolveContractRequest(
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            resolutionNote = "Auto-resolved: Both parties agreed on 60% refund to buyer",
            buyerEmail = null, // Wallet-only user
            sellerEmail = null, // Wallet-only user
            contractDescription = "Web development services",
            productName = "Premium Web Development Package",
            amount = "100000000",
            currency = "USDC",
            payoutDateTime = "1735689600",
            chainAddress = "0x1234567890abcdef1234567890abcdef12345678",
            buyerActualAmount = "60000000",
            sellerActualAmount = "40000000"
        )

        assertNull(request.buyerEmail)
        assertNull(request.sellerEmail)
        assertEquals(60.0, request.buyerPercentage)
        assertEquals(40.0, request.sellerPercentage)
    }

    @Test
    @DisplayName("AdminResolveContractRequest should accept empty string buyer email for wallet-only buyer")
    fun `AdminResolveContractRequest should support wallet-only buyer with empty string email`() {
        val request = AdminResolveContractRequest(
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            resolutionNote = "Auto-resolved: Both parties agreed on 60% refund to buyer",
            buyerEmail = "", // Wallet-only user (empty string)
            sellerEmail = "seller@example.com",
            contractDescription = "Web development services",
            productName = "Premium Web Development Package",
            amount = "100000000",
            currency = "USDC",
            payoutDateTime = "1735689600",
            chainAddress = "0x1234567890abcdef1234567890abcdef12345678",
            buyerActualAmount = "60000000",
            sellerActualAmount = "40000000"
        )

        assertEquals("", request.buyerEmail)
        assertEquals("seller@example.com", request.sellerEmail)
    }

    @Test
    @DisplayName("AdminResolveContractRequest should accept empty string for both emails")
    fun `AdminResolveContractRequest should support both wallet-only users with empty string emails`() {
        val request = AdminResolveContractRequest(
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            resolutionNote = "Auto-resolved: Both parties agreed on 60% refund to buyer",
            buyerEmail = "", // Wallet-only user (empty string)
            sellerEmail = "", // Wallet-only user (empty string)
            contractDescription = "Web development services",
            productName = "Premium Web Development Package",
            amount = "100000000",
            currency = "USDC",
            payoutDateTime = "1735689600",
            chainAddress = "0x1234567890abcdef1234567890abcdef12345678",
            buyerActualAmount = "60000000",
            sellerActualAmount = "40000000"
        )

        assertEquals("", request.buyerEmail)
        assertEquals("", request.sellerEmail)
    }

    @Test
    @DisplayName("ResolveDisputeRequest should support wallet-only users")
    fun `ResolveDisputeRequest should support null emails for wallet-only users`() {
        val request = ResolveDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            productName = "Test Product",
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            resolutionNote = "Test resolution",
            buyerEmail = null, // Wallet-only buyer
            sellerEmail = null, // Wallet-only seller
            contractDescription = "Test contract description",
            amount = "100.0",
            currency = "USDC",
            payoutDateTime = "1735689600"
        )

        assertNull(request.buyerEmail)
        assertNull(request.sellerEmail)
        assertEquals(60.0, request.buyerPercentage)
        assertEquals(40.0, request.sellerPercentage)
    }

    @Test
    @DisplayName("EmailFieldValidator should return false for dispute resolved email with null buyer email")
    fun `EmailFieldValidator should reject dispute resolved email when buyer email is null`() {
        val canSend = EmailFieldValidator.canSendDisputeResolvedEmail(
            buyerEmail = null,
            sellerEmail = "seller@example.com",
            amount = "100000000",
            payoutDateTime = "1735689600",
            contractDescription = "Web development services",
            sellerActualAmount = "40000000",
            buyerActualAmount = "60000000"
        )

        assertFalse(canSend, "Should not send email when buyer email is null")
    }

    @Test
    @DisplayName("EmailFieldValidator should return false for dispute resolved email with empty buyer email")
    fun `EmailFieldValidator should reject dispute resolved email when buyer email is empty`() {
        val canSend = EmailFieldValidator.canSendDisputeResolvedEmail(
            buyerEmail = "",
            sellerEmail = "seller@example.com",
            amount = "100000000",
            payoutDateTime = "1735689600",
            contractDescription = "Web development services",
            sellerActualAmount = "40000000",
            buyerActualAmount = "60000000"
        )

        assertFalse(canSend, "Should not send email when buyer email is empty")
    }

    @Test
    @DisplayName("EmailFieldValidator should return false for dispute resolved email with null seller email")
    fun `EmailFieldValidator should reject dispute resolved email when seller email is null`() {
        val canSend = EmailFieldValidator.canSendDisputeResolvedEmail(
            buyerEmail = "buyer@example.com",
            sellerEmail = null,
            amount = "100000000",
            payoutDateTime = "1735689600",
            contractDescription = "Web development services",
            sellerActualAmount = "40000000",
            buyerActualAmount = "60000000"
        )

        assertFalse(canSend, "Should not send email when seller email is null")
    }

    @Test
    @DisplayName("EmailFieldValidator should return false for dispute resolved email when both emails are null")
    fun `EmailFieldValidator should reject dispute resolved email when both emails are null`() {
        val canSend = EmailFieldValidator.canSendDisputeResolvedEmail(
            buyerEmail = null,
            sellerEmail = null,
            amount = "100000000",
            payoutDateTime = "1735689600",
            contractDescription = "Web development services",
            sellerActualAmount = "40000000",
            buyerActualAmount = "60000000"
        )

        assertFalse(canSend, "Should not send email when both emails are null")
    }

    @Test
    @DisplayName("EmailFieldValidator should return false for dispute resolved email when both emails are empty")
    fun `EmailFieldValidator should reject dispute resolved email when both emails are empty`() {
        val canSend = EmailFieldValidator.canSendDisputeResolvedEmail(
            buyerEmail = "",
            sellerEmail = "",
            amount = "100000000",
            payoutDateTime = "1735689600",
            contractDescription = "Web development services",
            sellerActualAmount = "40000000",
            buyerActualAmount = "60000000"
        )

        assertFalse(canSend, "Should not send email when both emails are empty")
    }

    @Test
    @DisplayName("EmailFieldValidator should return true when all required fields are present")
    fun `EmailFieldValidator should accept dispute resolved email when all fields are valid`() {
        val canSend = EmailFieldValidator.canSendDisputeResolvedEmail(
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = "100000000",
            payoutDateTime = "1735689600",
            contractDescription = "Web development services",
            sellerActualAmount = "40000000",
            buyerActualAmount = "60000000"
        )

        assertTrue(canSend, "Should send email when all required fields are valid")
    }

    @Test
    @DisplayName("EmailFieldValidator should return false for payment notification with null buyer email")
    fun `EmailFieldValidator should reject payment notification when buyer email is null`() {
        val canSend = EmailFieldValidator.canSendPaymentNotificationEmail(
            buyerEmail = null,
            sellerEmail = "seller@example.com",
            contractDescription = "Web development services",
            amount = "100000000",
            payoutDateTime = "1735689600"
        )

        assertFalse(canSend, "Should not send payment notification when buyer email is null")
    }

    @Test
    @DisplayName("EmailFieldValidator should return false for payment notification with empty seller email")
    fun `EmailFieldValidator should reject payment notification when seller email is empty`() {
        val canSend = EmailFieldValidator.canSendPaymentNotificationEmail(
            buyerEmail = "buyer@example.com",
            sellerEmail = "",
            contractDescription = "Web development services",
            amount = "100000000",
            payoutDateTime = "1735689600"
        )

        assertFalse(canSend, "Should not send payment notification when seller email is empty")
    }

    @Test
    @DisplayName("AdminResolveContractRequest with mixed user types should be valid")
    fun `AdminResolveContractRequest should support mixed wallet-only and email users`() {
        // Buyer has email, seller is wallet-only
        val request1 = AdminResolveContractRequest(
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            buyerEmail = "buyer@example.com",
            sellerEmail = null,
            contractDescription = "Web development services",
            productName = "Premium Web Development Package",
            amount = "100000000",
            currency = "USDC",
            payoutDateTime = "1735689600",
            chainAddress = "0x1234567890abcdef1234567890abcdef12345678"
        )

        assertEquals("buyer@example.com", request1.buyerEmail)
        assertNull(request1.sellerEmail)

        // Buyer is wallet-only, seller has email
        val request2 = AdminResolveContractRequest(
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            buyerEmail = null,
            sellerEmail = "seller@example.com",
            contractDescription = "Web development services",
            productName = "Premium Web Development Package",
            amount = "100000000",
            currency = "USDC",
            payoutDateTime = "1735689600",
            chainAddress = "0x1234567890abcdef1234567890abcdef12345678"
        )

        assertNull(request2.buyerEmail)
        assertEquals("seller@example.com", request2.sellerEmail)
    }
}
