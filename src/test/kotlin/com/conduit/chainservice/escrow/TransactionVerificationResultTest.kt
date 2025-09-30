package com.conduit.chainservice.escrow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TransactionVerificationResultTest {

    @Test
    fun `should create successful verification result`() {
        // Given
        val result = TransactionVerificationResult(
            verified = true,
            transactionHash = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
            contractAddress = "0x9876543210fedcba9876543210fedcba98765432",
            amount = 100.0,
            actualAmount = 100.0,
            recipient = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef",
            error = null
        )

        // Then
        assertTrue(result.verified)
        assertEquals("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", result.transactionHash)
        assertEquals("0x9876543210fedcba9876543210fedcba98765432", result.contractAddress)
        assertEquals(100.0, result.amount)
        assertEquals(100.0, result.actualAmount)
        assertEquals("0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef", result.recipient)
        assertNull(result.error)
    }

    @Test
    fun `should create failed verification result`() {
        // Given
        val result = TransactionVerificationResult(
            verified = false,
            transactionHash = null,
            contractAddress = null,
            amount = null,
            actualAmount = null,
            recipient = null,
            error = "Transaction not found on blockchain"
        )

        // Then
        assertFalse(result.verified)
        assertNull(result.transactionHash)
        assertNull(result.contractAddress)
        assertNull(result.amount)
        assertNull(result.actualAmount)
        assertNull(result.recipient)
        assertEquals("Transaction not found on blockchain", result.error)
    }

    @Test
    fun `should handle partial verification result`() {
        // Given
        val result = TransactionVerificationResult(
            verified = false,
            transactionHash = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
            contractAddress = null,
            amount = null,
            actualAmount = 50.0,
            recipient = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef",
            error = "Amount too low"
        )

        // Then
        assertFalse(result.verified)
        assertEquals("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", result.transactionHash)
        assertNull(result.contractAddress)
        assertNull(result.amount)
        assertEquals(50.0, result.actualAmount)
        assertEquals("0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef", result.recipient)
        assertEquals("Amount too low", result.error)
    }
}