package com.conduit.chainservice.service

import com.conduit.chainservice.config.BlockchainProperties
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
import java.math.BigInteger
import java.time.Instant

class ContractQueryServiceTest {

    @Mock
    private lateinit var web3j: Web3j
    
    @Mock
    private lateinit var blockchainProperties: BlockchainProperties

    @Mock
    private lateinit var eventParsingService: EventParsingService

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
        contractQueryService = spy(ContractQueryService(web3j, blockchainProperties, eventParsingService))
    }

    @Test
    fun `getContractsForWallet - regular user sees only their contracts`() = runBlocking {
        // Mock that user participates in contract1 and contract2
        whenever(eventParsingService.findContractsByParticipant(walletAddress))
            .thenReturn(listOf(contract1, contract2))
        
        // Mock contract info retrieval - user is buyer in contract1, seller in contract2
        val contractInfo1 = ContractInfo(
            contractAddress = contract1,
            buyer = walletAddress,
            seller = otherWallet,
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract 1",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )
        
        val contractInfo2 = ContractInfo(
            contractAddress = contract2,
            buyer = otherWallet,
            seller = walletAddress,
            amount = BigInteger.valueOf(2000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 7200,
            description = "Test contract 2",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )
        
        // Remove private method mocking - test will use real implementation

        val result = contractQueryService.getContractsForWallet(walletAddress)

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
        
        // Mock contract info retrieval for all contracts
        val contractInfo1 = ContractInfo(
            contractAddress = contract1,
            buyer = walletAddress,
            seller = otherWallet,
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract 1",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )
        
        val contractInfo2 = ContractInfo(
            contractAddress = contract2,
            buyer = otherWallet,
            seller = adminWallet,
            amount = BigInteger.valueOf(2000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 7200,
            description = "Test contract 2",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )
        
        val contractInfo3 = ContractInfo(
            contractAddress = contract3,
            buyer = "0xddd4444444444444444444444444444444444444",
            seller = "0xeee5555555555555555555555555555555555555",
            amount = BigInteger.valueOf(3000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 10800,
            description = "Test contract 3",
            status = ContractStatus.CREATED,
            funded = true,
            createdAt = Instant.now()
        )
        
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
        
        val contractInfo1 = ContractInfo(
            contractAddress = contract1,
            buyer = walletAddress,
            seller = otherWallet,
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract 1",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )
        
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

        val result = contractQueryService.getContractsForWallet(walletAddress)

        assertEquals(0, result.size)
        verify(eventParsingService).findContractsByParticipant(walletAddress)
    }

    @Test
    fun `getContractsForWallet - handles exceptions gracefully`() = runBlocking {
        whenever(eventParsingService.findContractsByParticipant(walletAddress))
            .thenThrow(RuntimeException("Blockchain error"))

        val result = contractQueryService.getContractsForWallet(walletAddress)

        assertEquals(0, result.size)
    }
}