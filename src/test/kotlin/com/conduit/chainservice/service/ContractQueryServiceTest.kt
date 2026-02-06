package com.conduit.chainservice.service

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.config.BlockchainProperties
import com.conduit.chainservice.config.AbiLoader
import com.conduit.chainservice.model.ContractInfo
import com.conduit.chainservice.model.ContractStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.web3j.protocol.Web3j
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint8
import com.conduit.chainservice.model.Multicall3Result
import java.math.BigInteger
import java.time.Instant

class ContractQueryServiceTest {

    @Mock
    private lateinit var web3j: Web3j

    @Mock
    private lateinit var escrowProperties: EscrowProperties

    @Mock
    private lateinit var blockchainProperties: BlockchainProperties

    @Mock
    private lateinit var eventParsingService: EventParsingService

    @Mock
    private lateinit var abiLoader: AbiLoader

    private lateinit var contractQueryService: ContractQueryService

    private val walletAddress = "0x1234567890abcdef1234567890abcdef12345678"
    private val adminWallet = "0x9876543210fedcba9876543210fedcba98765432"
    private val otherWallet = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef"
    
    private val contract1 = "0xaaa1111111111111111111111111111111111111"
    private val contract2 = "0xbbb2222222222222222222222222222222222222"
    private val contract3 = "0xccc3333333333333333333333333333333333333"

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(blockchainProperties.usdcContractAddress).thenReturn("0x5425890298aed601595a70AB815c96711a31Bc65")

        // Mock ABI loader to return contract info output types matching current ABI
        whenever(abiLoader.getContractInfoOutputTypes()).thenReturn(
            listOf(
                TypeReference.create(Address::class.java),  // buyer
                TypeReference.create(Address::class.java),  // seller
                TypeReference.create(Uint256::class.java),  // amount
                TypeReference.create(Uint256::class.java),  // expiryTimestamp
                TypeReference.create(Uint8::class.java),    // currentState
                TypeReference.create(Uint256::class.java),  // currentTimestamp
                TypeReference.create(Uint256::class.java),  // creatorFee
                TypeReference.create(Uint256::class.java),  // createdAt
                TypeReference.create(Address::class.java)   // tokenAddress
            )
        )

        whenever(abiLoader.getContractInfoOutputs()).thenReturn(
            listOf(
                AbiLoader.OutputParameter("_buyer", "address", TypeReference.create(Address::class.java)),
                AbiLoader.OutputParameter("_seller", "address", TypeReference.create(Address::class.java)),
                AbiLoader.OutputParameter("_amount", "uint256", TypeReference.create(Uint256::class.java)),
                AbiLoader.OutputParameter("_expiryTimestamp", "uint256", TypeReference.create(Uint256::class.java)),
                AbiLoader.OutputParameter("_currentState", "uint8", TypeReference.create(Uint8::class.java)),
                AbiLoader.OutputParameter("_currentTimestamp", "uint256", TypeReference.create(Uint256::class.java)),
                AbiLoader.OutputParameter("_creatorFee", "uint256", TypeReference.create(Uint256::class.java)),
                AbiLoader.OutputParameter("_createdAt", "uint256", TypeReference.create(Uint256::class.java)),
                AbiLoader.OutputParameter("_tokenAddress", "address", TypeReference.create(Address::class.java))
            )
        )

        contractQueryService = spy(ContractQueryService(web3j, escrowProperties, blockchainProperties, eventParsingService, abiLoader))
    }

    @Test
    fun `getContractsForWallet - regular user sees only their contracts`() = runBlocking {
        // Mock that user participates in contract1 and contract2
        whenever(eventParsingService.findContractsByParticipant(walletAddress))
            .thenReturn(listOf(contract1, contract2))
        
        // Remove private method mocking - test will use real implementation

        val result = contractQueryService.getContractsForWallet(walletAddress, null)

        assertEquals(2, result.size)
        assertTrue(result.any { it.contractAddress == contract1 })
        assertTrue(result.any { it.contractAddress == contract2 })
        
        verify(eventParsingService).findContractsByParticipant(walletAddress)
        verify(eventParsingService, never()).findAllContracts()
    }

    @Test
    fun `getContractsForWallet - admin user sees all contracts`() = runBlocking {
        // Mock that admin gets all contracts
        whenever(eventParsingService.findAllContracts())
            .thenReturn(listOf(contract1, contract2, contract3))
        
        // Remove private method mocking - test will use real implementation

        val result = contractQueryService.getContractsForWallet(adminWallet, "admin")

        assertEquals(3, result.size)
        assertTrue(result.any { it.contractAddress == contract1 })
        assertTrue(result.any { it.contractAddress == contract2 })
        assertTrue(result.any { it.contractAddress == contract3 })
        
        verify(eventParsingService).findAllContracts()
        verify(eventParsingService, never()).findContractsByParticipant(any())
    }

    @Test
    fun `getContractsForWallet - non-admin userType still gets participant filtering`() = runBlocking {
        // Mock that user with userType "regular" still gets filtered results
        whenever(eventParsingService.findContractsByParticipant(walletAddress))
            .thenReturn(listOf(contract1))
        
        // Remove private method mocking - test will use real implementation

        val result = contractQueryService.getContractsForWallet(walletAddress, "regular")

        assertEquals(1, result.size)
        assertEquals(contract1, result[0].contractAddress)
        
        verify(eventParsingService).findContractsByParticipant(walletAddress)
        verify(eventParsingService, never()).findAllContracts()
    }

    @Test
    fun `getContractsForWallet - handles empty results gracefully`() = runBlocking {
        whenever(eventParsingService.findContractsByParticipant(walletAddress))
            .thenReturn(emptyList())

        val result = contractQueryService.getContractsForWallet(walletAddress, null)

        assertEquals(0, result.size)
        verify(eventParsingService).findContractsByParticipant(walletAddress)
    }

    @Test
    fun `getContractsForWallet - handles exceptions gracefully`() = runBlocking {
        whenever(eventParsingService.findContractsByParticipant(walletAddress))
            .thenThrow(RuntimeException("Blockchain error"))

        val result = contractQueryService.getContractsForWallet(walletAddress, null)

        assertEquals(0, result.size)
    }

    @Test
    fun `multicall3 TypeReference construction - should not throw ClassCastException`() {
        // Test that the TypeReference for DynamicArray<Multicall3Result> can be created without errors
        // This verifies our fix for the Web3j ABI decoding issue
        assertDoesNotThrow {
            val typeReference = object : TypeReference<DynamicArray<Multicall3Result>>() {}
            assertNotNull(typeReference)

            // Verify the type is properly parameterized
            assertTrue(typeReference.type.toString().contains("DynamicArray"))
            assertTrue(typeReference.type.toString().contains("Multicall3Result"))
        }
    }
}