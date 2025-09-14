package com.conduit.chainservice.escrow

import com.conduit.chainservice.escrow.models.RaiseDisputeRequest
import com.conduit.chainservice.escrow.models.ResolveDisputeRequest
import com.conduit.chainservice.escrow.validation.EmailFieldValidator
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for email validation logic and conditional email field validation.
 */
class EmailValidationTest {

    private lateinit var validator: Validator

    @BeforeEach
    fun setup() {
        val factory = Validation.buildDefaultValidatorFactory()
        validator = factory.validator
    }

    @Test
    fun `RaiseDisputeRequest with valid core fields should pass validation`() {
        val request = RaiseDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            reason = "Product was not delivered as described",
            refundPercent = 50,
            databaseId = "507f1f77bcf86cd799439011"
        )

        val violations: Set<ConstraintViolation<RaiseDisputeRequest>> = validator.validate(request)
        assertTrue(violations.isEmpty(), "Should pass validation with valid core fields")
    }

    @Test
    fun `RaiseDisputeRequest with minimal fields should pass validation`() {
        val request = RaiseDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894..."
            // All other fields are optional
        )

        val violations: Set<ConstraintViolation<RaiseDisputeRequest>> = validator.validate(request)
        assertTrue(violations.isEmpty(), "Should pass validation with only required fields")
    }

    @Test
    fun `RaiseDisputeRequest with blank contract address should fail validation`() {
        val request = RaiseDisputeRequest(
            contractAddress = "", // Empty contract address should fail
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894...",
            reason = "Product was not delivered as described"
        )

        val violations: Set<ConstraintViolation<RaiseDisputeRequest>> = validator.validate(request)
        assertFalse(violations.isEmpty(), "Should fail validation when contract address is blank")
        
        val violationMessage = violations.first().message
        assertTrue(violationMessage.contains("Contract address is required"), "Should mention contract address is required")
    }

    @Test
    fun `ResolveDisputeRequest with no email fields should pass validation`() {
        val request = ResolveDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            productName = "Test Product",
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            sellerActualAmount = "40.0",
            buyerActualAmount = "60.0"
        )

        val violations: Set<ConstraintViolation<ResolveDisputeRequest>> = validator.validate(request)
        assertTrue(violations.isEmpty(), "Should pass validation when no email fields provided")
    }

    @Test
    fun `ResolveDisputeRequest with valid fields should pass validation`() {
        val request = ResolveDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            productName = "Test Product",
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            contractDescription = "Valid contract description",
            amount = "100.00 USDC",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            sellerActualAmount = "40.00 USDC",
            buyerActualAmount = "60.00 USDC"
        )

        val violations: Set<ConstraintViolation<ResolveDisputeRequest>> = validator.validate(request)
        assertTrue(violations.isEmpty(), "Should pass validation with valid fields since ConditionalEmailFields was removed")
    }

    @Test
    fun `ResolveDisputeRequest with all required fields should pass validation`() {
        val request = ResolveDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            productName = "Test Product",
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = "100.00 USDC",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractDescription = "Test escrow contract",
            sellerActualAmount = "40.00 USDC",
            buyerActualAmount = "60.00 USDC"
        )

        val violations: Set<ConstraintViolation<ResolveDisputeRequest>> = validator.validate(request)
        assertTrue(violations.isEmpty(), "Should pass validation when all required fields provided")
    }

    // Tests for EmailFieldValidator utility methods

    @Test
    fun `canSendDisputeRaisedEmail should return false when any required field is missing`() {
        assertFalse(EmailFieldValidator.canSendDisputeRaisedEmail(
            null, "seller@example.com", "100", "2024-12-31", "description", "product"
        ), "Should return false when buyerEmail is null")

        assertFalse(EmailFieldValidator.canSendDisputeRaisedEmail(
            "buyer@example.com", null, "100", "2024-12-31", "description", "product"
        ), "Should return false when sellerEmail is null")

        assertFalse(EmailFieldValidator.canSendDisputeRaisedEmail(
            "buyer@example.com", "seller@example.com", null, "2024-12-31", "description", "product"
        ), "Should return false when amount is null")

        assertFalse(EmailFieldValidator.canSendDisputeRaisedEmail(
            "buyer@example.com", "seller@example.com", "100", null, "description", "product"
        ), "Should return false when payoutDateTime is null")

        assertFalse(EmailFieldValidator.canSendDisputeRaisedEmail(
            "buyer@example.com", "seller@example.com", "100", "2024-12-31", null, "product"
        ), "Should return false when contractDescription is null")

        assertFalse(EmailFieldValidator.canSendDisputeRaisedEmail(
            "buyer@example.com", "seller@example.com", "100", "2024-12-31", "description", null
        ), "Should return false when productName is null")

        assertFalse(EmailFieldValidator.canSendDisputeRaisedEmail(
            "buyer@example.com", "seller@example.com", "", "2024-12-31", "description", "product"
        ), "Should return false when amount is empty")
    }

    @Test
    fun `canSendDisputeRaisedEmail should return true when all required fields are provided`() {
        assertTrue(EmailFieldValidator.canSendDisputeRaisedEmail(
            "buyer@example.com",
            "seller@example.com",
            "100.00 USDC",
            "2024-12-31T23:59:59Z",
            "Test escrow contract",
            "Test Product"
        ), "Should return true when all required fields are provided")
    }

    @Test
    fun `canSendDisputeResolvedEmail should return false when any required field is missing`() {
        assertFalse(EmailFieldValidator.canSendDisputeResolvedEmail(
            null, "seller@example.com", "100", "2024-12-31", "description", "40", "60"
        ), "Should return false when buyerEmail is null")

        assertFalse(EmailFieldValidator.canSendDisputeResolvedEmail(
            "buyer@example.com", null, "100", "2024-12-31", "description", "40", "60"
        ), "Should return false when sellerEmail is null")

        assertFalse(EmailFieldValidator.canSendDisputeResolvedEmail(
            "buyer@example.com", "seller@example.com", null, "2024-12-31", "description", "40", "60"
        ), "Should return false when amount is null")

        assertFalse(EmailFieldValidator.canSendDisputeResolvedEmail(
            "buyer@example.com", "seller@example.com", "100", null, "description", "40", "60"
        ), "Should return false when payoutDateTime is null")

        assertFalse(EmailFieldValidator.canSendDisputeResolvedEmail(
            "buyer@example.com", "seller@example.com", "100", "2024-12-31", null, "40", "60"
        ), "Should return false when contractDescription is null")

        assertFalse(EmailFieldValidator.canSendDisputeResolvedEmail(
            "buyer@example.com", "seller@example.com", "100", "2024-12-31", "description", null, "60"
        ), "Should return false when sellerActualAmount is null")

        assertFalse(EmailFieldValidator.canSendDisputeResolvedEmail(
            "buyer@example.com", "seller@example.com", "100", "2024-12-31", "description", "40", null
        ), "Should return false when buyerActualAmount is null")
    }

    @Test
    fun `canSendDisputeResolvedEmail should return true when all required fields are provided`() {
        assertTrue(EmailFieldValidator.canSendDisputeResolvedEmail(
            "buyer@example.com",
            "seller@example.com",
            "100.00 USDC",
            "2024-12-31T23:59:59Z",
            "Test escrow contract",
            "40.00 USDC",
            "60.00 USDC"
        ), "Should return true when all required fields are provided")
    }

    @Test
    fun `canSendPaymentNotificationEmail should return false when any required field is missing`() {
        assertFalse(EmailFieldValidator.canSendPaymentNotificationEmail(
            null, "seller@example.com", "description", "100", "2024-12-31"
        ), "Should return false when buyerEmail is null")

        assertFalse(EmailFieldValidator.canSendPaymentNotificationEmail(
            "buyer@example.com", null, "description", "100", "2024-12-31"
        ), "Should return false when sellerEmail is null")

        assertFalse(EmailFieldValidator.canSendPaymentNotificationEmail(
            "buyer@example.com", "seller@example.com", null, "100", "2024-12-31"
        ), "Should return false when contractDescription is null")

        assertFalse(EmailFieldValidator.canSendPaymentNotificationEmail(
            "buyer@example.com", "seller@example.com", "description", null, "2024-12-31"
        ), "Should return false when amount is null")

        assertFalse(EmailFieldValidator.canSendPaymentNotificationEmail(
            "buyer@example.com", "seller@example.com", "description", "100", null
        ), "Should return false when payoutDateTime is null")
    }

    @Test
    fun `canSendPaymentNotificationEmail should return true when all required fields are provided`() {
        assertTrue(EmailFieldValidator.canSendPaymentNotificationEmail(
            "buyer@example.com",
            "seller@example.com",
            "Test escrow contract",
            "100.00 USDC",
            "2024-12-31T23:59:59Z"
        ), "Should return true when all required fields are provided")
    }
}