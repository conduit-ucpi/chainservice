package com.conduit.chainservice.escrow

import com.conduit.chainservice.escrow.validation.EmailFieldValidator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Test class to verify email validation utility methods.
 * Note: RaiseDisputeRequest no longer has email fields since email handling moved to contractservice.
 * These tests validate the email utility methods still used by other request types.
 */
class RaiseDisputeValidationTest {

    @Test
    fun `canSendDisputeRaisedEmail should return true when all fields are valid`() {
        // Arrange - valid fields as they should come from frontend
        val result = EmailFieldValidator.canSendDisputeRaisedEmail(
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = "100.50",  // Converted from microUSDC to USDC
            payoutDateTime = "2024-12-31T23:59:59Z",  // ISO string
            contractDescription = "Premium wireless headphones",
            productName = "Sony WH-1000XM4"
        )
        
        // Assert
        assertTrue(result, "Should return true when all fields are valid")
    }

    @Test
    fun `canSendDisputeRaisedEmail should return false when amount is null`() {
        // Arrange
        val result = EmailFieldValidator.canSendDisputeRaisedEmail(
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = null,  // This should cause failure
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractDescription = "Premium wireless headphones",
            productName = "Sony WH-1000XM4"
        )
        
        // Assert
        assertFalse(result, "Should return false when amount is null")
    }

    @Test
    fun `canSendDisputeRaisedEmail should return false when amount is empty string`() {
        // Arrange
        val result = EmailFieldValidator.canSendDisputeRaisedEmail(
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = "",  // Empty string should fail
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractDescription = "Premium wireless headphones",
            productName = "Sony WH-1000XM4"
        )
        
        // Assert
        assertFalse(result, "Should return false when amount is empty string")
    }

    @Test
    fun `canSendDisputeRaisedEmail should return false when amount is N_A`() {
        // Arrange
        val result = EmailFieldValidator.canSendDisputeRaisedEmail(
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = "N/A",  // Literal "N/A" should fail
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractDescription = "Premium wireless headphones",
            productName = "Sony WH-1000XM4"
        )
        
        // Assert
        assertFalse(result, "Should return false when amount is 'N/A'")
    }

    @Test
    fun `canSendDisputeRaisedEmail should return false when amount is null string`() {
        // Arrange
        val result = EmailFieldValidator.canSendDisputeRaisedEmail(
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = "null",  // Literal "null" should fail
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractDescription = "Premium wireless headphones",
            productName = "Sony WH-1000XM4"
        )
        
        // Assert
        assertFalse(result, "Should return false when amount is 'null'")
    }

    @Test
    fun `canSendDisputeRaisedEmail should return false when payoutDateTime is blank`() {
        // Arrange
        val result = EmailFieldValidator.canSendDisputeRaisedEmail(
            buyerEmail = "buyer@example.com",
            sellerEmail = "seller@example.com",
            amount = "100.50",
            payoutDateTime = "   ",  // Whitespace-only should fail
            contractDescription = "Premium wireless headphones",
            productName = "Sony WH-1000XM4"
        )
        
        // Assert
        assertFalse(result, "Should return false when payoutDateTime is whitespace only")
    }

    @Test
    fun `canSendDisputeRaisedEmail should return false when any required field is invalid`() {
        // Test each field individually
        val testCases = listOf(
            "buyerEmail" to EmailFieldValidator.canSendDisputeRaisedEmail(null, "seller@example.com", "100.50", "2024-12-31T23:59:59Z", "Premium wireless headphones", "Sony WH-1000XM4"),
            "sellerEmail" to EmailFieldValidator.canSendDisputeRaisedEmail("buyer@example.com", null, "100.50", "2024-12-31T23:59:59Z", "Premium wireless headphones", "Sony WH-1000XM4"),
            "amount" to EmailFieldValidator.canSendDisputeRaisedEmail("buyer@example.com", "seller@example.com", null, "2024-12-31T23:59:59Z", "Premium wireless headphones", "Sony WH-1000XM4"),
            "payoutDateTime" to EmailFieldValidator.canSendDisputeRaisedEmail("buyer@example.com", "seller@example.com", "100.50", null, "Premium wireless headphones", "Sony WH-1000XM4"),
            "contractDescription" to EmailFieldValidator.canSendDisputeRaisedEmail("buyer@example.com", "seller@example.com", "100.50", "2024-12-31T23:59:59Z", null, "Sony WH-1000XM4"),
            "productName" to EmailFieldValidator.canSendDisputeRaisedEmail("buyer@example.com", "seller@example.com", "100.50", "2024-12-31T23:59:59Z", "Premium wireless headphones", null)
        )
        
        testCases.forEach { (fieldName, result) ->
            assertFalse(result, "Should return false when $fieldName is null")
        }
    }

    @Test
    fun `canSendDisputeRaisedEmail should return false when only one email is provided`() {
        // Test with buyer email only
        val resultBuyerOnly = EmailFieldValidator.canSendDisputeRaisedEmail(
            buyerEmail = "buyer@example.com",
            sellerEmail = null,
            amount = "100.50",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractDescription = "Premium wireless headphones",
            productName = "Sony WH-1000XM4"
        )
        
        // Test with seller email only
        val resultSellerOnly = EmailFieldValidator.canSendDisputeRaisedEmail(
            buyerEmail = null,
            sellerEmail = "seller@example.com",
            amount = "100.50",
            payoutDateTime = "2024-12-31T23:59:59Z",
            contractDescription = "Premium wireless headphones",
            productName = "Sony WH-1000XM4"
        )
        
        assertFalse(resultBuyerOnly, "Should return false when only buyer email is provided")
        assertFalse(resultSellerOnly, "Should return false when only seller email is provided")
    }

    @Test
    fun `canSendDisputeRaisedEmail should handle case insensitive problematic values`() {
        val problematicValues = listOf("N/A", "n/a", "NULL", "null", "UNDEFINED", "undefined")
        
        problematicValues.forEach { problematicValue ->
            val result = EmailFieldValidator.canSendDisputeRaisedEmail(
                buyerEmail = "buyer@example.com",
                sellerEmail = "seller@example.com",
                amount = problematicValue,  // Test each problematic value
                payoutDateTime = "2024-12-31T23:59:59Z",
                contractDescription = "Premium wireless headphones",
                productName = "Sony WH-1000XM4"
            )
            
            assertFalse(result, "Should return false when amount is '$problematicValue'")
        }
    }
}