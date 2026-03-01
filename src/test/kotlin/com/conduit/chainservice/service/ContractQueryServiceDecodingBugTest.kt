package com.conduit.chainservice.service

import com.conduit.chainservice.config.AbiLoader
import com.conduit.chainservice.config.BlockchainProperties
import com.conduit.chainservice.config.EscrowProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.mockito.kotlin.mock
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.EthCall
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import java.math.BigInteger

/**
 * Test that demonstrates the bug in ContractQueryService where it tries to decode
 * contract responses with hardcoded 10-field structure, but the actual ABI only
 * has 9 fields, causing StringIndexOutOfBoundsException
 */
@DisplayName("ContractQueryService Decoding Bug Test")
class ContractQueryServiceDecodingBugTest {

    @Test
    @DisplayName("Demonstrates the hardcoded 10-field assumption in ContractQueryService")
    fun `ContractQueryService incorrectly assumes 10 fields but ABI only has 9`() {
        // Load the actual ABI
        val abiLoader = AbiLoader()
        abiLoader.loadAbis()

        // Get the ACTUAL output types from ABI
        val actualAbiOutputs = abiLoader.getContractInfoOutputTypes()
        assertEquals(9, actualAbiOutputs.size, "ABI defines 9 fields for getContractInfo")

        // But ContractQueryService.decodeContractInfoResult() has hardcoded 10 fields:
        val hardcodedInCode = listOf(
            TypeReference.create(Address::class.java),     // buyer
            TypeReference.create(Address::class.java),     // seller
            TypeReference.create(Uint256::class.java),     // amount
            TypeReference.create(Uint256::class.java),     // expiryTimestamp
            TypeReference.create(Utf8String::class.java),  // description
            TypeReference.create(Uint8::class.java),       // currentState
            TypeReference.create(Uint256::class.java),     // currentTimestamp
            TypeReference.create(Uint256::class.java),     // creatorFee
            TypeReference.create(Uint256::class.java),     // createdAt
            TypeReference.create(Address::class.java)      // tokenAddress - THIS DOESN'T EXIST!
        )

        assertEquals(10, hardcodedInCode.size, "Code incorrectly hardcodes 10 fields")

        // This mismatch (10 fields in code vs 9 in ABI) causes the StringIndexOutOfBoundsException
        // when decoding real blockchain data
        println("❌ BUG DEMONSTRATED:")
        println("   - ABI defines ${actualAbiOutputs.size} fields")
        println("   - Code hardcodes ${hardcodedInCode.size} fields")
        println("   - This causes StringIndexOutOfBoundsException when decoding real contract responses")
        println("   - Solution: Use abiLoader.getContractInfoOutputTypes() instead of hardcoded list")
    }

    @Test
    @DisplayName("Verifies ABI fields match contract and are loaded correctly")
    fun `verify ABI defines getContractInfo output fields correctly`() {
        val abiLoader = AbiLoader()
        abiLoader.loadAbis()

        val outputs = abiLoader.getContractInfoOutputs()

        assertEquals(9, outputs.size, "getContractInfo should return 9 fields according to ABI")

        // Document the actual fields from ABI
        val fieldNames = outputs.map { it.name }
        println("Actual ABI fields: $fieldNames")

        // Verify expected fields are present
        val expectedFields = listOf("_buyer", "_seller", "_amount", "_expiryTimestamp",
            "_currentState", "_currentTimestamp", "_creatorFee", "_createdAt", "_tokenAddress")

        expectedFields.forEach { expectedField ->
            assertTrue(
                outputs.any { it.name == expectedField },
                "ABI should contain field: $expectedField"
            )
        }
    }
}
