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
            tokenAddress = "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
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
    fun `CreateContractRequest should fail validation with invalid token address`() {
        // Given
        val request = CreateContractRequest(
            tokenAddress = "invalid-token-address",
            buyer = "0x1234567890abcdef1234567890abcdef12345678",
            seller = "0x9876543210fedcba9876543210fedcba98765432",
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = Instant.now().plusSeconds(3600).epochSecond,
            description = "Test contract"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Invalid token address format") })
    }

    @Test
    fun `CreateContractRequest should fail validation with invalid buyer address`() {
        // Given
        val request = CreateContractRequest(
            tokenAddress = "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
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
            tokenAddress = "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
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
            tokenAddress = "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
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
            tokenAddress = "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
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
            tokenAddress = "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
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
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
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
            contractId = null,
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractLink = "https://test.com/contract"
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
            signedTransaction = "0xf86c8082520894abcdefabcdefabcdefabcdefabcdefabcdefabcdef80801ba01234567890abcdef12",
            reason = "Product was not delivered as described",
            refundPercent = 50,
            databaseId = "507f1f77bcf86cd799439011"
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
            contractAddress = "", // Blank contract address should fail validation
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "0xf86c8082520894abcdefabcdefabcdefabcdefabcdefabcdefabcdef80801ba01234567890abcdef12",
            reason = "Product was not delivered as described",
            refundPercent = 50,
            databaseId = "507f1f77bcf86cd799439011"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Contract address is required") || it.message.contains("must not be blank") })
    }

    @Test
    fun `RaiseDisputeRequest should fail validation with invalid user wallet address`() {
        // Given
        val request = RaiseDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "", // Blank user wallet address should fail validation
            signedTransaction = "0xf86c8082520894abcdefabcdefabcdefabcdefabcdefabcdefabcdef80801ba01234567890abcdef12",
            reason = "Product was not delivered as described",
            refundPercent = 50,
            databaseId = "507f1f77bcf86cd799439011"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("User wallet address is required") || it.message.contains("must not be blank") })
    }

    @Test
    fun `RaiseDisputeRequest should fail validation with invalid signed transaction format`() {
        // Given
        val request = RaiseDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            signedTransaction = "", // Blank signed transaction should fail validation
            reason = "Product was not delivered as described",
            refundPercent = 50,
            databaseId = "507f1f77bcf86cd799439011"
        )

        // When
        val violations = validator.validate(request)

        // Then
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.any { it.message.contains("Signed transaction is required") || it.message.contains("must not be blank") })
    }

    @Test
    fun `ResolveDisputeRequest should validate successfully with valid data`() {
        // Given
        val request = ResolveDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            recipientAddress = "0x9876543210fedcba9876543210fedcba98765432",
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z"
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
            tokenAddress = "invalid-token", // Invalid: wrong format
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
            tokenAddress = "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
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
            tokenAddress = "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
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
            tokenAddress = "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a",
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