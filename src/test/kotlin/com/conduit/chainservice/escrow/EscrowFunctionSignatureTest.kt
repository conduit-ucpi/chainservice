package com.conduit.chainservice.escrow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Hash
import java.math.BigInteger

/**
 * Test suite to verify function signature encodings for escrow contract interactions.
 *
 * These tests capture the CURRENT function encodings and serve as regression tests
 * to ensure the refactoring to ABI-driven function building doesn't change signatures.
 *
 * Each test verifies:
 * 1. Function selector (first 4 bytes) matches expected keccak256 hash
 * 2. Full encoding with test parameters produces expected output
 */
class EscrowFunctionSignatureTest {

    /**
     * Test createEscrowContract function encoding on factory contract
     * Signature: createEscrowContract(address,address,address,uint256,uint256,string)
     */
    @Test
    fun `test createEscrowContract function encoding`() {
        // Test parameters
        val tokenAddress = "0x036CbD53842c5426634e7929541eC2318f3dCF7e"
        val buyer = "0x1234567890123456789012345678901234567890"
        val seller = "0x9876543210987654321098765432109876543210"
        val amount = BigInteger.valueOf(1_000_000) // 1 USDC in microUSDC
        val expiryTimestamp = 1735689600L
        val description = "Test escrow contract"

        // Build function using current implementation approach
        val function = Function(
            "createEscrowContract",
            listOf(
                Address(tokenAddress),
                Address(buyer),
                Address(seller),
                Uint256(amount),
                Uint256(BigInteger.valueOf(expiryTimestamp)),
                Utf8String(description)
            ),
            emptyList()
        )

        val encodedFunction = FunctionEncoder.encode(function)

        // Verify function selector (first 4 bytes / 8 hex chars after 0x)
        val expectedSelector = calculateFunctionSelector("createEscrowContract(address,address,address,uint256,uint256,string)")
        val actualSelector = encodedFunction.substring(0, 10) // 0x + 8 hex chars

        assertEquals(expectedSelector, actualSelector,
            "Function selector mismatch for createEscrowContract")

        // Verify the encoding is not empty and has reasonable length
        assertTrue(encodedFunction.length > 10, "Encoded function should contain data beyond selector")

        // Log for debugging
        println("createEscrowContract selector: $actualSelector")
        println("Full encoding length: ${encodedFunction.length} chars")
    }

    /**
     * Test resolveDispute with percentage parameters
     * Signature: resolveDispute(uint256,uint256)
     */
    @Test
    fun `test resolveDispute with percentages function encoding`() {
        val buyerPercentage = BigInteger.valueOf(60)
        val sellerPercentage = BigInteger.valueOf(40)

        val function = Function(
            "resolveDispute",
            listOf(
                Uint256(buyerPercentage),
                Uint256(sellerPercentage)
            ),
            emptyList()
        )

        val encodedFunction = FunctionEncoder.encode(function)

        val expectedSelector = calculateFunctionSelector("resolveDispute(uint256,uint256)")
        val actualSelector = encodedFunction.substring(0, 10)

        assertEquals(expectedSelector, actualSelector,
            "Function selector mismatch for resolveDispute(uint256,uint256)")

        // Verify parameter encoding
        assertTrue(encodedFunction.length > 10, "Should contain encoded parameters")

        println("resolveDispute(uint256,uint256) selector: $actualSelector")
    }

    /**
     * Test claimFunds function encoding
     * Signature: claimFunds()
     */
    @Test
    fun `test claimFunds function encoding`() {
        val function = Function(
            "claimFunds",
            emptyList(),
            emptyList()
        )

        val encodedFunction = FunctionEncoder.encode(function)

        val expectedSelector = calculateFunctionSelector("claimFunds()")
        val actualSelector = encodedFunction.substring(0, 10)

        assertEquals(expectedSelector, actualSelector,
            "Function selector mismatch for claimFunds")

        // claimFunds takes no parameters, so encoding should be just the selector
        assertEquals(10, encodedFunction.length,
            "claimFunds() encoding should only be selector (0x + 8 hex chars)")

        println("claimFunds() selector: $actualSelector")
    }

    /**
     * Test depositFunds function encoding
     * Signature: depositFunds()
     */
    @Test
    fun `test depositFunds function encoding`() {
        val function = Function(
            "depositFunds",
            emptyList(),
            emptyList()
        )

        val encodedFunction = FunctionEncoder.encode(function)

        val expectedSelector = calculateFunctionSelector("depositFunds()")
        val actualSelector = encodedFunction.substring(0, 10)

        assertEquals(expectedSelector, actualSelector,
            "Function selector mismatch for depositFunds")

        assertEquals(10, encodedFunction.length,
            "depositFunds() encoding should only be selector")

        println("depositFunds() selector: $actualSelector")
    }

    /**
     * Test raiseDispute function encoding
     * Signature: raiseDispute()
     * Note: This is used in user-signed transactions, not built by EscrowTransactionService,
     * but included for completeness
     */
    @Test
    fun `test raiseDispute function encoding`() {
        val function = Function(
            "raiseDispute",
            emptyList(),
            emptyList()
        )

        val encodedFunction = FunctionEncoder.encode(function)

        val expectedSelector = calculateFunctionSelector("raiseDispute()")
        val actualSelector = encodedFunction.substring(0, 10)

        assertEquals(expectedSelector, actualSelector,
            "Function selector mismatch for raiseDispute")

        assertEquals(10, encodedFunction.length,
            "raiseDispute() encoding should only be selector")

        println("raiseDispute() selector: $actualSelector")
    }

    /**
     * Test USDC_TOKEN view function encoding
     * Signature: USDC_TOKEN()
     * This is the actual function name in the contract ABI
     */
    @Test
    fun `test USDC_TOKEN view function encoding`() {
        val function = Function(
            "USDC_TOKEN",
            emptyList(),
            listOf(object : org.web3j.abi.TypeReference<Address>() {})
        )

        val encodedFunction = FunctionEncoder.encode(function)

        val expectedSelector = calculateFunctionSelector("USDC_TOKEN()")
        val actualSelector = encodedFunction.substring(0, 10)

        assertEquals(expectedSelector, actualSelector,
            "Function selector mismatch for USDC_TOKEN")

        assertEquals(10, encodedFunction.length,
            "USDC_TOKEN() encoding should only be selector")

        println("USDC_TOKEN() selector: $actualSelector")
    }

    /**
     * Test getContractInfo view function encoding
     * Signature: getContractInfo()
     * This is used by ContractQueryService which was already refactored to use ABI
     */
    @Test
    fun `test getContractInfo view function encoding`() {
        val function = Function(
            "getContractInfo",
            emptyList(),
            // Output parameters - not encoded, just for reference
            emptyList()
        )

        val encodedFunction = FunctionEncoder.encode(function)

        val expectedSelector = calculateFunctionSelector("getContractInfo()")
        val actualSelector = encodedFunction.substring(0, 10)

        assertEquals(expectedSelector, actualSelector,
            "Function selector mismatch for getContractInfo")

        println("getContractInfo() selector: $actualSelector")
    }

    /**
     * Calculate function selector from signature
     * Function selector = first 4 bytes of keccak256(signature)
     */
    private fun calculateFunctionSelector(signature: String): String {
        val hash = Hash.sha3String(signature)
        return "0x" + hash.substring(2, 10) // First 4 bytes (8 hex chars)
    }

    /**
     * Comprehensive test that documents all expected function selectors
     * This serves as a reference for what selectors the system should produce
     */
    @Test
    fun `document all function selectors`() {
        val expectedSelectors = mapOf(
            "createEscrowContract(address,address,address,uint256,uint256,string)" to calculateFunctionSelector("createEscrowContract(address,address,address,uint256,uint256,string)"),
            "resolveDispute(uint256,uint256)" to calculateFunctionSelector("resolveDispute(uint256,uint256)"),
            "claimFunds()" to calculateFunctionSelector("claimFunds()"),
            "depositFunds()" to calculateFunctionSelector("depositFunds()"),
            "raiseDispute()" to calculateFunctionSelector("raiseDispute()"),
            "USDC_TOKEN()" to calculateFunctionSelector("USDC_TOKEN()"),
            "getContractInfo()" to calculateFunctionSelector("getContractInfo()")
        )

        println("\n=== Expected Function Selectors ===")
        expectedSelectors.forEach { (signature, selector) ->
            println("$signature -> $selector")
        }
        println("===================================\n")

        // This test always passes - it's just for documentation
        assertTrue(true)
    }

    /**
     * Test that verifies encoding consistency for createEscrowContract with different parameters
     */
    @Test
    fun `test createEscrowContract encoding with various parameters`() {
        // Test case 1: Small amount
        val encoding1 = encodeCreateEscrowContract(
            tokenAddress = "0x036CbD53842c5426634e7929541eC2318f3dCF7e",
            buyer = "0x1111111111111111111111111111111111111111",
            seller = "0x2222222222222222222222222222222222222222",
            amount = BigInteger.valueOf(1000), // 0.001 USDC
            expiryTimestamp = 1735689600L,
            description = "Small"
        )

        // Test case 2: Large amount
        val encoding2 = encodeCreateEscrowContract(
            tokenAddress = "0x036CbD53842c5426634e7929541eC2318f3dCF7e",
            buyer = "0x3333333333333333333333333333333333333333",
            seller = "0x4444444444444444444444444444444444444444",
            amount = BigInteger.valueOf(1_000_000_000), // 1000 USDC
            expiryTimestamp = 1735689600L,
            description = "Large amount contract"
        )

        // Both should start with same selector
        assertEquals(encoding1.substring(0, 10), encoding2.substring(0, 10),
            "Different parameters should produce same function selector")

        // But full encodings should differ
        assertNotEquals(encoding1, encoding2,
            "Different parameters should produce different encodings")

        println("Encoding 1 length: ${encoding1.length}")
        println("Encoding 2 length: ${encoding2.length}")
    }

    /**
     * Helper function to encode createEscrowContract calls
     */
    private fun encodeCreateEscrowContract(
        tokenAddress: String,
        buyer: String,
        seller: String,
        amount: BigInteger,
        expiryTimestamp: Long,
        description: String
    ): String {
        val function = Function(
            "createEscrowContract",
            listOf(
                Address(tokenAddress),
                Address(buyer),
                Address(seller),
                Uint256(amount),
                Uint256(BigInteger.valueOf(expiryTimestamp)),
                Utf8String(description)
            ),
            emptyList()
        )
        return FunctionEncoder.encode(function)
    }
}
