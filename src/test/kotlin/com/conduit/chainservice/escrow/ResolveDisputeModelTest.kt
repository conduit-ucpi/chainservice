package com.conduit.chainservice.escrow

import com.conduit.chainservice.escrow.models.AdminResolveContractRequest
import com.conduit.chainservice.escrow.models.AdminResolveContractResponse
import com.conduit.chainservice.escrow.models.ResolveDisputeRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ResolveDisputeModelTest {

    @Test
    fun `ResolveDisputeRequest should support percentage-based resolution`() {
        val request = ResolveDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            resolutionNote = "Test resolution"
        )

        assertEquals("0x1234567890abcdef1234567890abcdef12345678", request.contractAddress)
        assertEquals(60.0, request.buyerPercentage)
        assertEquals(40.0, request.sellerPercentage)
        assertEquals("Test resolution", request.resolutionNote)
        assertNull(request.recipientAddress)
    }

    @Test
    fun `ResolveDisputeRequest should support legacy single recipient resolution`() {
        val request = ResolveDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            recipientAddress = "0x9876543210fedcba9876543210fedcba98765432"
        )

        assertEquals("0x1234567890abcdef1234567890abcdef12345678", request.contractAddress)
        assertEquals("0x9876543210fedcba9876543210fedcba98765432", request.recipientAddress)
        assertNull(request.buyerPercentage)
        assertNull(request.sellerPercentage)
        assertNull(request.resolutionNote)
    }

    @Test
    fun `AdminResolveContractRequest should have correct properties`() {
        val request = AdminResolveContractRequest(
            buyerPercentage = 75.5,
            sellerPercentage = 24.5,
            resolutionNote = "Admin decision: buyer provided evidence",
            buyerEmail = "buyer@test.com",
            sellerEmail = "seller@test.com",
            contractDescription = "Test contract description",
            amount = "100.0",
            currency = "USDC",
            payoutDateTime = "2024-12-31T23:59:59Z"
        )

        assertEquals(75.5, request.buyerPercentage)
        assertEquals(24.5, request.sellerPercentage)
        assertEquals("Admin decision: buyer provided evidence", request.resolutionNote)
    }

    @Test
    fun `AdminResolveContractResponse should handle success case`() {
        val response = AdminResolveContractResponse(
            success = true,
            transactionHash = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            error = null
        )

        assertTrue(response.success)
        assertEquals("0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890", response.transactionHash)
        assertNull(response.error)
    }

    @Test
    fun `AdminResolveContractResponse should handle failure case`() {
        val response = AdminResolveContractResponse(
            success = false,
            transactionHash = null,
            error = "Transaction failed: insufficient gas"
        )

        assertFalse(response.success)
        assertNull(response.transactionHash)
        assertEquals("Transaction failed: insufficient gas", response.error)
    }

    @Test
    fun `ResolveDisputeRequest should support both percentage and recipient fields`() {
        // This tests backward compatibility - the request model can handle both formats
        val requestWithBoth = ResolveDisputeRequest(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            recipientAddress = "0x9876543210fedcba9876543210fedcba98765432",
            buyerPercentage = 100.0,
            sellerPercentage = 0.0,
            resolutionNote = "Mixed format test"
        )

        assertEquals("0x1234567890abcdef1234567890abcdef12345678", requestWithBoth.contractAddress)
        assertEquals("0x9876543210fedcba9876543210fedcba98765432", requestWithBoth.recipientAddress)
        assertEquals(100.0, requestWithBoth.buyerPercentage)
        assertEquals(0.0, requestWithBoth.sellerPercentage)
        assertEquals("Mixed format test", requestWithBoth.resolutionNote)
    }
}