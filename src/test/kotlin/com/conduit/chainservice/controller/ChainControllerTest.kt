package com.conduit.chainservice.controller

import com.conduit.chainservice.model.ContractInfo
import com.conduit.chainservice.model.ContractStatus
import com.conduit.chainservice.model.GetContractsResponse
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.ContractServiceClient
import com.conduit.chainservice.service.TransactionRelayService
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import java.math.BigInteger
import java.time.Instant

class ChainControllerTest {

    @Mock
    private lateinit var transactionRelayService: TransactionRelayService

    @Mock
    private lateinit var contractQueryService: ContractQueryService

    @Mock
    private lateinit var contractServiceClient: ContractServiceClient

    @Mock
    private lateinit var request: HttpServletRequest

    private lateinit var chainController: ChainController

    private val testWalletAddress = "0x1234567890abcdef1234567890abcdef12345678"
    private val adminWalletAddress = "0x9876543210fedcba9876543210fedcba98765432"
    private val otherWalletAddress = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef"

    private val contract1 = "0xaaa1111111111111111111111111111111111111"
    private val contract2 = "0xbbb2222222222222222222222222222222222222"
    private val contract3 = "0xccc3333333333333333333333333333333333333"

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        chainController = ChainController(
            transactionRelayService,
            contractQueryService,
            contractServiceClient
        )
    }

    @Test
    fun `getContracts - regular user gets only their contracts`() = runBlocking {
        val contractInfo1 = ContractInfo(
            contractAddress = contract1,
            buyer = testWalletAddress,
            seller = otherWalletAddress,
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract 1",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )

        val contractInfo2 = ContractInfo(
            contractAddress = contract2,
            buyer = otherWalletAddress,
            seller = testWalletAddress,
            amount = BigInteger.valueOf(2000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 7200,
            description = "Test contract 2",
            status = ContractStatus.CREATED,
            funded = true,
            createdAt = Instant.now()
        )

        whenever(request.getAttribute("userType")).thenReturn("regular")
        whenever(contractQueryService.getContractsForWallet(testWalletAddress, "regular")).thenReturn(listOf(contractInfo1, contractInfo2))

        val result = chainController.getContracts(testWalletAddress, request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertEquals(2, result.body!!.contracts.size)
        
        val contracts = result.body!!.contracts
        assertTrue(contracts.any { it.contractAddress == contract1 })
        assertTrue(contracts.any { it.contractAddress == contract2 })

        verify(contractQueryService).getContractsForWallet(testWalletAddress, "regular")
    }

    @Test
    fun `getContracts - admin user gets all contracts`() = runBlocking {
        val contractInfo1 = ContractInfo(
            contractAddress = contract1,
            buyer = testWalletAddress,
            seller = otherWalletAddress,
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract 1",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )

        val contractInfo2 = ContractInfo(
            contractAddress = contract2,
            buyer = otherWalletAddress,
            seller = "0xddd4444444444444444444444444444444444444",
            amount = BigInteger.valueOf(2000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 7200,
            description = "Test contract 2",
            status = ContractStatus.CREATED,
            funded = true,
            createdAt = Instant.now()
        )

        val contractInfo3 = ContractInfo(
            contractAddress = contract3,
            buyer = "0xeee5555555555555555555555555555555555555",
            seller = "0xfff6666666666666666666666666666666666666",
            amount = BigInteger.valueOf(3000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 10800,
            description = "Test contract 3",
            status = ContractStatus.DISPUTED,
            funded = true,
            createdAt = Instant.now()
        )

        whenever(request.getAttribute("userType")).thenReturn("admin")
        whenever(contractQueryService.getContractsForWallet(adminWalletAddress, "admin")).thenReturn(listOf(contractInfo1, contractInfo2, contractInfo3))

        val result = chainController.getContracts(adminWalletAddress, request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertEquals(3, result.body!!.contracts.size)
        
        val contracts = result.body!!.contracts
        assertTrue(contracts.any { it.contractAddress == contract1 })
        assertTrue(contracts.any { it.contractAddress == contract2 })
        assertTrue(contracts.any { it.contractAddress == contract3 })

        verify(contractQueryService).getContractsForWallet(adminWalletAddress, "admin")
    }

    @Test
    fun `getContracts - null userType defaults to regular user behavior`() = runBlocking {
        val contractInfo = ContractInfo(
            contractAddress = contract1,
            buyer = testWalletAddress,
            seller = otherWalletAddress,
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )

        whenever(request.getAttribute("userType")).thenReturn(null)
        whenever(contractQueryService.getContractsForWallet(testWalletAddress, null)).thenReturn(listOf(contractInfo))

        val result = chainController.getContracts(testWalletAddress, request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertEquals(1, result.body!!.contracts.size)

        verify(contractQueryService).getContractsForWallet(testWalletAddress, null)
    }

    @Test
    fun `getContracts - rejects invalid wallet address format`() = runBlocking {
        val invalidWalletAddress = "invalid-wallet-address"

        whenever(request.getAttribute("userType")).thenReturn("regular")

        val result = chainController.getContracts(invalidWalletAddress, request)

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertNotNull(result.body)
        assertEquals(0, result.body!!.contracts.size)

        verify(contractQueryService, never()).getContractsForWallet(any(), any())
    }

    @Test
    fun `getContracts - handles short wallet address`() = runBlocking {
        val shortWalletAddress = "0x1234567890abcdef1234567890abcdef1234567" // 41 chars instead of 42

        whenever(request.getAttribute("userType")).thenReturn("regular")

        val result = chainController.getContracts(shortWalletAddress, request)

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertNotNull(result.body)
        assertEquals(0, result.body!!.contracts.size)

        verify(contractQueryService, never()).getContractsForWallet(any(), any())
    }

    @Test
    fun `getContracts - handles long wallet address`() = runBlocking {
        val longWalletAddress = "0x1234567890abcdef1234567890abcdef123456789" // 43 chars instead of 42

        whenever(request.getAttribute("userType")).thenReturn("regular")

        val result = chainController.getContracts(longWalletAddress, request)

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertNotNull(result.body)
        assertEquals(0, result.body!!.contracts.size)

        verify(contractQueryService, never()).getContractsForWallet(any(), any())
    }

    @Test
    fun `getContracts - handles wallet address without 0x prefix`() = runBlocking {
        val noPrefixWalletAddress = "1234567890abcdef1234567890abcdef12345678"

        whenever(request.getAttribute("userType")).thenReturn("regular")

        val result = chainController.getContracts(noPrefixWalletAddress, request)

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertNotNull(result.body)
        assertEquals(0, result.body!!.contracts.size)

        verify(contractQueryService, never()).getContractsForWallet(any(), any())
    }

    @Test
    fun `getContracts - handles empty contract list`() = runBlocking {
        whenever(request.getAttribute("userType")).thenReturn("regular")
        whenever(contractQueryService.getContractsForWallet(testWalletAddress, "regular")).thenReturn(emptyList())

        val result = chainController.getContracts(testWalletAddress, request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertEquals(0, result.body!!.contracts.size)

        verify(contractQueryService).getContractsForWallet(testWalletAddress, "regular")
    }

    @Test
    fun `getContracts - handles service exception`() = runBlocking {
        whenever(request.getAttribute("userType")).thenReturn("regular")
        whenever(contractQueryService.getContractsForWallet(testWalletAddress, "regular")).thenThrow(RuntimeException("Database connection failed"))

        val result = chainController.getContracts(testWalletAddress, request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
        assertNotNull(result.body)
        assertEquals(0, result.body!!.contracts.size)

        verify(contractQueryService).getContractsForWallet(testWalletAddress, "regular")
    }

    @Test
    fun `getContracts - accepts uppercase hex wallet address`() = runBlocking {
        val uppercaseWalletAddress = "0X1234567890ABCDEF1234567890ABCDEF12345678"
        
        val contractInfo = ContractInfo(
            contractAddress = contract1,
            buyer = uppercaseWalletAddress,
            seller = otherWalletAddress,
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )

        whenever(request.getAttribute("userType")).thenReturn("regular")
        whenever(contractQueryService.getContractsForWallet(uppercaseWalletAddress, "regular")).thenReturn(listOf(contractInfo))

        val result = chainController.getContracts(uppercaseWalletAddress, request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertEquals(1, result.body!!.contracts.size)

        verify(contractQueryService).getContractsForWallet(uppercaseWalletAddress, "regular")
    }

    @Test
    fun `getContracts - accepts mixed case hex wallet address`() = runBlocking {
        val mixedCaseWalletAddress = "0x1234567890AbCdEf1234567890aBcDeF12345678"
        
        val contractInfo = ContractInfo(
            contractAddress = contract1,
            buyer = mixedCaseWalletAddress,
            seller = otherWalletAddress,
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )

        whenever(request.getAttribute("userType")).thenReturn("admin")
        whenever(contractQueryService.getContractsForWallet(mixedCaseWalletAddress, "admin")).thenReturn(listOf(contractInfo))

        val result = chainController.getContracts(mixedCaseWalletAddress, request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertEquals(1, result.body!!.contracts.size)

        verify(contractQueryService).getContractsForWallet(mixedCaseWalletAddress, "admin")
    }
}