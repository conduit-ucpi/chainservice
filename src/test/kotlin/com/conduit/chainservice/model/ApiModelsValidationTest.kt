package com.conduit.chainservice.model

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigInteger
import java.time.Instant
import com.conduit.chainservice.escrow.models.*

class ApiModelsValidationTest {

    private lateinit var validator: Validator

    @BeforeEach
    fun setUp() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    @Test
    fun `CreateContractRequest should validate successfully with valid data`() {
        // Given
        val request = CreateContractRequest(
            buyer = "0x1234567890abcdef1234567890abcdef12345678",
            seller = "0x9876543210fedcba9876543210fedcba98765432",
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = Instant.now().plusSeconds(3600).epochSecond,
            description = "Valid test contract"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `CreateContractRequest should fail validation with invalid buyer address`() {
        // Given
        val request = CreateContractRequest(
            buyer = "invalid-address",
            seller = "0x9876543210fedcba9876543210fedcba98765432",
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = Instant.now().plusSeconds(3600).epochSecond,
            description = "Test contract"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertEquals(1, violations.size)
        assertTrue(violations.any { it.message.contains("Invalid buyer address format") })
    }

    @Test
    fun `CreateContractRequest should fail validation with blank buyer address`() {
        // Given
        val request = CreateContractRequest(
            buyer = "",
            seller = "0x9876543210fedcba9876543210fedcba98765432",
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = Instant.now().plusSeconds(3600).epochSecond,
            description = "Test contract"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Buyer address is required") })
    }

    @Test
    fun `CreateContractRequest should fail validation with negative amount`() {
        // Given
        val request = CreateContractRequest(
            buyer = "0x1234567890abcdef1234567890abcdef12345678",
            seller = "0x9876543210fedcba9876543210fedcba98765432",
            amount = BigInteger.valueOf(-1),
            expiryTimestamp = Instant.now().plusSeconds(3600).epochSecond,
            description = "Test contract"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Amount must be positive") })
    }

    @Test
    fun `CreateContractRequest should fail validation with zero amount`() {
        // Given
        val request = CreateContractRequest(
            buyer = "0x1234567890abcdef1234567890abcdef12345678",
            seller = "0x9876543210fedcba9876543210fedcba98765432",
            amount = BigInteger.ZERO,
            expiryTimestamp = Instant.now().plusSeconds(3600).epochSecond,
            description = "Test contract"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Amount must be positive") })
    }

    @Test
    fun `CreateContractRequest should fail validation with too long description`() {
        // Given
        val longDescription = "a".repeat(161) // 161 characters
        val request = CreateContractRequest(
            buyer = "0x1234567890abcdef1234567890abcdef12345678",
            seller = "0x9876543210fedcba9876543210fedcba98765432",
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = Instant.now().plusSeconds(3600).epochSecond,
            description = longDescription
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Description must be 160 characters or less") })
    }

    @Test
    fun `ClaimFundsRequest should validate successfully with valid data`() {
        // Given
        val request = ClaimFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894abcdefabcdefabcdefabcdefabcdefabcdefabcdef80801ba01234567890abcdef12"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `ClaimFundsRequest should fail validation with invalid contract address`() {
        // Given
        val request = ClaimFundsRequest(
            contractAddress = "invalid-address",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894abcdefabcdefabcdefabcdefabcdefabcdefabcdef80801ba01234567890abcdef12"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Invalid contract address format") })
    }

    @Test
    fun `ClaimFundsRequest should fail validation with invalid user wallet address`() {
        // Given
        val request = ClaimFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "invalid-wallet",
            signedTransaction = "0xf86c8082520894abcdefabcdefabcdefabcdefabcdefabcdefabcdef80801ba01234567890abcdef12"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Invalid user wallet address format") })
    }

    @Test
    fun `ClaimFundsRequest should fail validation with invalid signed transaction format`() {
        // Given
        val request = ClaimFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "invalid-transaction"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Invalid signed transaction format") })
    }

    @Test
    fun `DepositFundsRequest should validate successfully with contractId`() {
        // Given
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894abcdefabcdefabcdefabcdefabcdefabcdefabcdef80801ba01234567890abcdef12",
            contractId = "507f1f77bcf86cd799439011",
            amount = "100.00 USDC",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `DepositFundsRequest should validate successfully without contractId`() {
        // Given
        val request = DepositFundsRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894abcdefabcdefabcdefabcdefabcdefabcdefabcdef80801ba01234567890abcdef12",
            amount = "100.00 USDC",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `RaiseDisputeRequest should validate successfully with valid data`() {
        // Given
        val request = RaiseDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894abcdefabcdefabcdefabcdefabcdefabcdefabcdef80801ba01234567890abcdef12"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `RaiseDisputeRequest should fail validation with invalid contract address`() {
        // Given
        val request = RaiseDisputeRequest(
            contractAddress = "invalid-address",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894abcdefabcdefabcdefabcdefabcdefabcdefabcdef80801ba01234567890abcdef12"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Invalid contract address format") })
    }

    @Test
    fun `RaiseDisputeRequest should fail validation with invalid user wallet address`() {
        // Given
        val request = RaiseDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "invalid-wallet",
            signedTransaction = "0xf86c8082520894abcdefabcdefabcdefabcdefabcdefabcdefabcdef80801ba01234567890abcdef12"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Invalid user wallet address format") })
    }

    @Test
    fun `RaiseDisputeRequest should fail validation with invalid signed transaction format`() {
        // Given
        val request = RaiseDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "invalid-transaction"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Invalid signed transaction format") })
    }

    @Test
    fun `ResolveDisputeRequest should validate successfully with valid data`() {
        // Given
        val request = ResolveDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            recipientAddress = "0x9876543210fedcba9876543210fedcba98765432"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `ApproveUSDCRequest should validate successfully with valid data`() {
        // Given
        val request = ApproveUSDCRequest(
            userWalletAddress = "0x1234567890abcdef1234567890abcdef12345678",
            signedTransaction = "0xf86c8082520894abcdefabcdefabcdefabcdefabcdefabcdefabcdef80801ba01234567890abcdef12"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `multiple validation errors should be reported`() {
        // Given
        val request = CreateContractRequest(
            buyer = "", // Invalid: blank
            seller = "invalid", // Invalid: wrong format
            amount = BigInteger.valueOf(-1), // Invalid: negative
            expiryTimestamp = 0L, // Invalid: zero
            description = "" // Invalid: blank
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.size >= 4) // Should have multiple violations
        
        val messages = violations.map { it.message }
        assertTrue(messages.any { it.contains("Buyer address is required") })
        assertTrue(messages.any { it.contains("Invalid seller address format") })
        assertTrue(messages.any { it.contains("Amount must be positive") })
        assertTrue(messages.any { it.contains("Description is required") })
    }

    @Test
    fun `hex address validation should be case insensitive`() {
        // Given
        val request = CreateContractRequest(
            buyer = "0x1234567890ABCDEF1234567890ABCDEF12345678", // Uppercase hex
            seller = "0x9876543210fedcba9876543210fedcba98765432", // Lowercase hex
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = Instant.now().plusSeconds(3600).epochSecond,
            description = "Test contract"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `address validation should require 0x prefix`() {
        // Given
        val request = CreateContractRequest(
            buyer = "1234567890abcdef1234567890abcdef12345678", // Missing 0x prefix
            seller = "0x9876543210fedcba9876543210fedcba98765432",
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = Instant.now().plusSeconds(3600).epochSecond,
            description = "Test contract"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Invalid buyer address format") })
    }

    @Test
    fun `address validation should require exactly 40 hex characters after 0x`() {
        // Given
        val request = CreateContractRequest(
            buyer = "0x1234567890abcdef1234567890abcdef1234567", // 39 chars (too short)
            seller = "0x9876543210fedcba9876543210fedcba98765432",
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = Instant.now().plusSeconds(3600).epochSecond,
            description = "Test contract"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Invalid buyer address format") })
    }
}