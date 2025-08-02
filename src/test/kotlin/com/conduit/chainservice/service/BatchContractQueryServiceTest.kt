package com.conduit.chainservice.service

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.escrow.models.ContractInfo
import com.conduit.chainservice.escrow.models.ContractStatus
import com.conduit.chainservice.escrow.models.ContractInfoResult
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

class BatchContractQueryServiceTest {

    @Mock
    private lateinit var web3j: Web3j
    
    @Mock
    private lateinit var escrowProperties: EscrowProperties

    @Mock
    private lateinit var eventParsingService: EventParsingService

    private lateinit var contractQueryService: ContractQueryService

    private val contract1 = "0xaaa1111111111111111111111111111111111111"
    private val contract2 = "0xbbb2222222222222222222222222222222222222"
    private val contract3 = "0xccc3333333333333333333333333333333333333"

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        contractQueryService = spy(ContractQueryService(web3j, escrowProperties, eventParsingService))
    }

    @Test
    fun `getBatchContractInfo - successful batch query with multiple contracts`() = runBlocking {
        // Mock getContractInfo for individual contracts
        val contractInfo1 = createMockContractInfo(contract1, "buyer1", "seller1")
        val contractInfo2 = createMockContractInfo(contract2, "buyer2", "seller2")
        val contractInfo3 = createMockContractInfo(contract3, "buyer3", "seller3")

        doReturn(contractInfo1).whenever(contractQueryService).getContractInfo(contract1)
        doReturn(contractInfo2).whenever(contractQueryService).getContractInfo(contract2)
        doReturn(contractInfo3).whenever(contractQueryService).getContractInfo(contract3)

        val contractAddresses = listOf(contract1, contract2, contract3)
        val results = contractQueryService.getBatchContractInfo(contractAddresses)

        assertEquals(3, results.size)
        assertTrue(results[contract1]?.success == true)
        assertTrue(results[contract2]?.success == true)
        assertTrue(results[contract3]?.success == true)
        
        assertNotNull(results[contract1]?.contractInfo)
        assertEquals("buyer1", results[contract1]?.contractInfo?.buyer)
        assertEquals("seller1", results[contract1]?.contractInfo?.seller)
        assertEquals(BigInteger.valueOf(1000), results[contract1]?.contractInfo?.amount)
        assertEquals(ContractStatus.ACTIVE, results[contract1]?.contractInfo?.status)
    }

    @Test
    fun `getBatchContractInfo - handles partial failures gracefully`() = runBlocking {
        val contractInfo1 = createMockContractInfo(contract1, "buyer1", "seller1")

        doReturn(contractInfo1).whenever(contractQueryService).getContractInfo(contract1)
        doReturn(null).whenever(contractQueryService).getContractInfo(contract2)

        val contractAddresses = listOf(contract1, contract2)
        val results = contractQueryService.getBatchContractInfo(contractAddresses)

        assertEquals(2, results.size)
        assertTrue(results[contract1]?.success == true)
        assertTrue(results[contract2]?.success == false)
        assertEquals("Contract not found or invalid", results[contract2]?.error)
        assertNull(results[contract2]?.contractInfo)
    }

    @Test
    fun `getBatchContractInfo - handles exceptions gracefully`() = runBlocking {
        doThrow(RuntimeException("Network error")).whenever(contractQueryService).getContractInfo(contract1)

        val contractAddresses = listOf(contract1)
        val results = contractQueryService.getBatchContractInfo(contractAddresses)

        assertEquals(1, results.size)
        assertTrue(results[contract1]?.success == false)
        assertEquals("Network error", results[contract1]?.error)
    }

    @Test
    fun `getBatchContractInfo - handles empty contract list`() = runBlocking {
        val results = contractQueryService.getBatchContractInfo(emptyList())
        
        assertTrue(results.isEmpty())
    }

    @Test
    fun `getBatchContractInfo - processes contracts in parallel`() = runBlocking {
        // Mock multiple contracts
        val contractInfo1 = createMockContractInfo(contract1, "buyer1", "seller1")
        val contractInfo2 = createMockContractInfo(contract2, "buyer2", "seller2")

        doReturn(contractInfo1).whenever(contractQueryService).getContractInfo(contract1)
        doReturn(contractInfo2).whenever(contractQueryService).getContractInfo(contract2)

        val contractAddresses = listOf(contract1, contract2)
        val results = contractQueryService.getBatchContractInfo(contractAddresses)

        assertEquals(2, results.size)
        assertTrue(results[contract1]?.success == true)
        assertTrue(results[contract2]?.success == true)

        // Verify both contracts were queried
        verify(contractQueryService).getContractInfo(contract1)
        verify(contractQueryService).getContractInfo(contract2)
    }

    private fun createMockContractInfo(contractAddress: String, buyer: String, seller: String): ContractInfo {
        return ContractInfo(
            contractAddress = contractAddress,
            buyer = buyer,
            seller = seller,
            amount = BigInteger.valueOf(1000),
            expiryTimestamp = Instant.now().epochSecond + 86400,
            description = "Test contract",
            funded = true,
            status = ContractStatus.ACTIVE,
            createdAt = Instant.now(),
            fundedAt = Instant.now(),
            disputedAt = null,
            resolvedAt = null,
            claimedAt = null
        )
    }
}