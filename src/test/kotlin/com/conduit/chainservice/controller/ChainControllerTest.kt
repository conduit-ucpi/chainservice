package com.conduit.chainservice.controller

import com.conduit.chainservice.model.*
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.ContractServiceClient
import com.conduit.chainservice.service.TransactionRelayService
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigInteger
import java.time.Instant

@ExtendWith(MockitoExtension::class)
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
        chainController = ChainController(
            transactionRelayService,
            contractQueryService,
            contractServiceClient
        )
        // Set the chainId field using reflection
        ReflectionTestUtils.setField(chainController, "chainId", "43113")
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
    fun `getContract - regular user can access their own contract as buyer`() = runBlocking {
        val contractAddress = contract1
        val contractInfo = ContractInfo(
            contractAddress = contractAddress,
            buyer = testWalletAddress,
            seller = otherWalletAddress,
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )

        whenever(request.getAttribute("userType")).thenReturn("regular")
        whenever(request.getAttribute("userId")).thenReturn("user123")
        whenever(request.getAttribute("userWallet")).thenReturn(testWalletAddress)
        whenever(contractQueryService.getContractInfo(contractAddress)).thenReturn(contractInfo)

        val result = chainController.getContract(contractAddress, request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body is ContractInfo)
        assertEquals(contractAddress, (result.body as ContractInfo).contractAddress)

        verify(contractQueryService).getContractInfo(contractAddress)
    }

    @Test
    fun `getContract - regular user can access their own contract as seller`() = runBlocking {
        val contractAddress = contract1
        val contractInfo = ContractInfo(
            contractAddress = contractAddress,
            buyer = otherWalletAddress,
            seller = testWalletAddress,
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )

        whenever(request.getAttribute("userType")).thenReturn("regular")
        whenever(request.getAttribute("userId")).thenReturn("user123")
        whenever(request.getAttribute("userWallet")).thenReturn(testWalletAddress)
        whenever(contractQueryService.getContractInfo(contractAddress)).thenReturn(contractInfo)

        val result = chainController.getContract(contractAddress, request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body is ContractInfo)
        assertEquals(contractAddress, (result.body as ContractInfo).contractAddress)

        verify(contractQueryService).getContractInfo(contractAddress)
    }

    @Test
    fun `getContract - regular user cannot access other users contract`() = runBlocking {
        val contractAddress = contract1
        val contractInfo = ContractInfo(
            contractAddress = contractAddress,
            buyer = otherWalletAddress,
            seller = "0xddd4444444444444444444444444444444444444",
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )

        whenever(request.getAttribute("userType")).thenReturn("regular")
        whenever(request.getAttribute("userId")).thenReturn("user123")
        whenever(request.getAttribute("userWallet")).thenReturn(testWalletAddress)
        whenever(contractQueryService.getContractInfo(contractAddress)).thenReturn(contractInfo)

        val result = chainController.getContract(contractAddress, request)

        assertEquals(HttpStatus.FORBIDDEN, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body is Map<*, *>)
        assertEquals("Access denied - you are not authorized to view this contract", (result.body as Map<*, *>)["error"])

        verify(contractQueryService).getContractInfo(contractAddress)
    }

    @Test
    fun `getContract - admin user can access any contract`() = runBlocking {
        val contractAddress = contract1
        val contractInfo = ContractInfo(
            contractAddress = contractAddress,
            buyer = otherWalletAddress,
            seller = "0xddd4444444444444444444444444444444444444",
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )

        whenever(request.getAttribute("userType")).thenReturn("admin")
        whenever(request.getAttribute("userId")).thenReturn("admin123")
        whenever(contractQueryService.getContractInfo(contractAddress)).thenReturn(contractInfo)

        val result = chainController.getContract(contractAddress, request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body is ContractInfo)
        assertEquals(contractAddress, (result.body as ContractInfo).contractAddress)

        verify(contractQueryService).getContractInfo(contractAddress)
    }

    @Test
    fun `getContract - returns 404 when contract not found`() = runBlocking {
        val contractAddress = contract1

        whenever(request.getAttribute("userType")).thenReturn("admin")
        whenever(request.getAttribute("userId")).thenReturn("admin123")
        whenever(contractQueryService.getContractInfo(contractAddress)).thenReturn(null)

        val result = chainController.getContract(contractAddress, request)

        assertEquals(HttpStatus.NOT_FOUND, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body is Map<*, *>)
        assertEquals("Contract not found", (result.body as Map<*, *>)["error"])

        verify(contractQueryService).getContractInfo(contractAddress)
    }

    @Test
    fun `getContract - rejects invalid contract address format`() = runBlocking {
        val invalidContractAddress = "invalid-contract-address"

        whenever(request.getAttribute("userType")).thenReturn("admin")
        whenever(request.getAttribute("userId")).thenReturn("admin123")

        val result = chainController.getContract(invalidContractAddress, request)

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body is Map<*, *>)
        assertEquals("Invalid contract address format", (result.body as Map<*, *>)["error"])

        verify(contractQueryService, never()).getContractInfo(any())
    }

    @Test
    fun `getContract - handles user with no wallet address`() = runBlocking {
        val contractAddress = contract1
        val contractInfo = ContractInfo(
            contractAddress = contractAddress,
            buyer = testWalletAddress,
            seller = otherWalletAddress,
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = System.currentTimeMillis() / 1000 + 3600,
            description = "Test contract",
            status = ContractStatus.ACTIVE,
            funded = true,
            createdAt = Instant.now()
        )

        whenever(request.getAttribute("userType")).thenReturn("regular")
        whenever(request.getAttribute("userId")).thenReturn("user123")
        whenever(request.getAttribute("userWallet")).thenReturn(null)
        whenever(contractQueryService.getContractInfo(contractAddress)).thenReturn(contractInfo)

        val result = chainController.getContract(contractAddress, request)

        assertEquals(HttpStatus.FORBIDDEN, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body is Map<*, *>)
        assertEquals("Access denied - you are not authorized to view this contract", (result.body as Map<*, *>)["error"])

        verify(contractQueryService).getContractInfo(contractAddress)
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

    @Test
    fun `raiseDispute - successful dispute raising with gas transfer`() = runBlocking {
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432"
        val signedTransaction = "0xf86c8082520894..."
        val transactionHash = "0xabcdef1234567890abcdef1234567890abcdef12"

        val request = RaiseDisputeRequest(
            contractAddress = contractAddress,
            userWalletAddress = userWalletAddress,
            signedTransaction = signedTransaction
        )

        val mockResult = TransactionResult(
            success = true,
            transactionHash = transactionHash,
            error = null
        )

        whenever(transactionRelayService.raiseDisputeWithGasTransfer(userWalletAddress, signedTransaction))
            .thenReturn(mockResult)

        val result = chainController.raiseDispute(request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body!!.success)
        assertEquals(transactionHash, result.body!!.transactionHash)
        assertNull(result.body!!.error)

        verify(transactionRelayService).raiseDisputeWithGasTransfer(userWalletAddress, signedTransaction)
    }

    @Test
    fun `raiseDispute - failed dispute raising returns error`() = runBlocking {
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432"
        val signedTransaction = "0xf86c8082520894..."
        val errorMessage = "Insufficient gas for transaction"

        val request = RaiseDisputeRequest(
            contractAddress = contractAddress,
            userWalletAddress = userWalletAddress,
            signedTransaction = signedTransaction
        )

        val mockResult = TransactionResult(
            success = false,
            transactionHash = null,
            error = errorMessage
        )

        whenever(transactionRelayService.raiseDisputeWithGasTransfer(userWalletAddress, signedTransaction))
            .thenReturn(mockResult)

        val result = chainController.raiseDispute(request)

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertNotNull(result.body)
        assertFalse(result.body!!.success)
        assertNull(result.body!!.transactionHash)
        assertEquals(errorMessage, result.body!!.error)

        verify(transactionRelayService).raiseDisputeWithGasTransfer(userWalletAddress, signedTransaction)
    }

    @Test
    fun `raiseDispute - service exception returns internal server error`() = runBlocking {
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432"
        val signedTransaction = "0xf86c8082520894..."

        val request = RaiseDisputeRequest(
            contractAddress = contractAddress,
            userWalletAddress = userWalletAddress,
            signedTransaction = signedTransaction
        )

        whenever(transactionRelayService.raiseDisputeWithGasTransfer(userWalletAddress, signedTransaction))
            .thenThrow(RuntimeException("Network connection failed"))

        val result = chainController.raiseDispute(request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
        assertNotNull(result.body)
        assertFalse(result.body!!.success)
        assertNull(result.body!!.transactionHash)
        assertEquals("Network connection failed", result.body!!.error)

        verify(transactionRelayService).raiseDisputeWithGasTransfer(userWalletAddress, signedTransaction)
    }

    @Test
    fun `raiseDispute - transaction succeeds but blockchain fails`() = runBlocking {
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432"
        val signedTransaction = "0xf86c8082520894..."
        val transactionHash = "0xabcdef1234567890abcdef1234567890abcdef12"

        val request = RaiseDisputeRequest(
            contractAddress = contractAddress,
            userWalletAddress = userWalletAddress,
            signedTransaction = signedTransaction
        )

        val mockResult = TransactionResult(
            success = false,
            transactionHash = transactionHash,
            error = "Transaction failed on blockchain"
        )

        whenever(transactionRelayService.raiseDisputeWithGasTransfer(userWalletAddress, signedTransaction))
            .thenReturn(mockResult)

        val result = chainController.raiseDispute(request)

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertNotNull(result.body)
        assertFalse(result.body!!.success)
        assertEquals(transactionHash, result.body!!.transactionHash)
        assertEquals("Transaction failed on blockchain", result.body!!.error)

        verify(transactionRelayService).raiseDisputeWithGasTransfer(userWalletAddress, signedTransaction)
    }

    // ============== CREATE CONTRACT TESTS ==============

    @Test
    fun `createContract - successful contract creation`() = runBlocking {
        val buyer = "0x1234567890abcdef1234567890abcdef12345678"
        val seller = "0x9876543210fedcba9876543210fedcba98765432"
        val amount = BigInteger.valueOf(1000000)
        val expiryTimestamp = System.currentTimeMillis() / 1000 + 3600
        val description = "Test contract"
        val contractAddress = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef"
        val transactionHash = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef12"

        val request = CreateContractRequest(
            buyer = buyer,
            seller = seller,
            amount = amount,
            expiryTimestamp = expiryTimestamp,
            description = description
        )

        val mockResult = ContractCreationResult(
            success = true,
            transactionHash = transactionHash,
            contractAddress = contractAddress,
            error = null
        )

        whenever(transactionRelayService.createContract(buyer, seller, amount, expiryTimestamp, description))
            .thenReturn(mockResult)

        val result = chainController.createContract(request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body!!.success)
        assertEquals(transactionHash, result.body!!.transactionHash)
        assertEquals(contractAddress, result.body!!.contractAddress)
        assertNull(result.body!!.error)

        verify(transactionRelayService).createContract(buyer, seller, amount, expiryTimestamp, description)
    }

    @Test
    fun `createContract - failed contract creation`() = runBlocking {
        val buyer = "0x1234567890abcdef1234567890abcdef12345678"
        val seller = "0x9876543210fedcba9876543210fedcba98765432"
        val amount = BigInteger.valueOf(1000000)
        val expiryTimestamp = System.currentTimeMillis() / 1000 + 3600
        val description = "Test contract"
        val errorMessage = "Insufficient gas for contract creation"

        val request = CreateContractRequest(
            buyer = buyer,
            seller = seller,
            amount = amount,
            expiryTimestamp = expiryTimestamp,
            description = description
        )

        val mockResult = ContractCreationResult(
            success = false,
            transactionHash = null,
            contractAddress = null,
            error = errorMessage
        )

        whenever(transactionRelayService.createContract(buyer, seller, amount, expiryTimestamp, description))
            .thenReturn(mockResult)

        val result = chainController.createContract(request)

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertNotNull(result.body)
        assertFalse(result.body!!.success)
        assertNull(result.body!!.transactionHash)
        assertNull(result.body!!.contractAddress)
        assertEquals(errorMessage, result.body!!.error)
    }

    // ============== DEPOSIT FUNDS TESTS ==============

    @Test
    fun `depositFunds - successful deposit with contract service update`() = runBlocking {
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432"
        val signedTransaction = "0xf86c8082520894..."
        val contractId = "507f1f77bcf86cd799439011"
        val transactionHash = "0xabcdef1234567890abcdef1234567890abcdef12"

        val request = DepositFundsRequest(
            contractAddress = contractAddress,
            userWalletAddress = userWalletAddress,
            signedTransaction = signedTransaction,
            contractId = contractId
        )

        val mockResult = TransactionResult(
            success = true,
            transactionHash = transactionHash,
            error = null
        )

        whenever(transactionRelayService.depositFundsWithGasTransfer(userWalletAddress, signedTransaction))
            .thenReturn(mockResult)
        whenever(contractServiceClient.updateContractWithDeployment(any(), any(), any(), any(), any()))
            .thenReturn(mock())

        val result = chainController.depositFunds(request, this@ChainControllerTest.request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body!!.success)
        assertEquals(transactionHash, result.body!!.transactionHash)
        assertNull(result.body!!.error)

        verify(transactionRelayService).depositFundsWithGasTransfer(userWalletAddress, signedTransaction)
        verify(contractServiceClient).updateContractWithDeployment(
            contractId = contractId,
            chainAddress = contractAddress,
            chainId = "43113",
            buyerAddress = userWalletAddress,
            request = this@ChainControllerTest.request
        )
    }

    @Test
    fun `depositFunds - successful deposit without contract service update`() = runBlocking {
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432"
        val signedTransaction = "0xf86c8082520894..."
        val transactionHash = "0xabcdef1234567890abcdef1234567890abcdef12"

        val request = DepositFundsRequest(
            contractAddress = contractAddress,
            userWalletAddress = userWalletAddress,
            signedTransaction = signedTransaction,
            contractId = null
        )

        val mockResult = TransactionResult(
            success = true,
            transactionHash = transactionHash,
            error = null
        )

        whenever(transactionRelayService.depositFundsWithGasTransfer(userWalletAddress, signedTransaction))
            .thenReturn(mockResult)

        val result = chainController.depositFunds(request, this@ChainControllerTest.request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body!!.success)
        assertEquals(transactionHash, result.body!!.transactionHash)

        verify(transactionRelayService).depositFundsWithGasTransfer(userWalletAddress, signedTransaction)
        verify(contractServiceClient, never()).updateContractWithDeployment(any(), any(), any(), any(), any())
    }

    // ============== CLAIM FUNDS TESTS ==============

    @Test
    fun `claimFunds - successful funds claim`() = runBlocking {
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432"
        val signedTransaction = "0xf86c8082520894..."
        val transactionHash = "0xabcdef1234567890abcdef1234567890abcdef12"

        val request = ClaimFundsRequest(
            contractAddress = contractAddress,
            userWalletAddress = userWalletAddress,
            signedTransaction = signedTransaction
        )

        val mockResult = TransactionResult(
            success = true,
            transactionHash = transactionHash,
            error = null
        )

        whenever(transactionRelayService.claimFundsWithGasTransfer(userWalletAddress, signedTransaction))
            .thenReturn(mockResult)

        val result = chainController.claimFunds(request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body!!.success)
        assertEquals(transactionHash, result.body!!.transactionHash)
        assertNull(result.body!!.error)

        verify(transactionRelayService).claimFundsWithGasTransfer(userWalletAddress, signedTransaction)
    }

    // ============== RESOLVE DISPUTE TESTS ==============

    @Test
    fun `resolveDispute - successful dispute resolution`() = runBlocking {
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val recipientAddress = "0x9876543210fedcba9876543210fedcba98765432"
        val transactionHash = "0xabcdef1234567890abcdef1234567890abcdef12"

        val request = ResolveDisputeRequest(
            contractAddress = contractAddress,
            recipientAddress = recipientAddress
        )

        val mockResult = TransactionResult(
            success = true,
            transactionHash = transactionHash,
            error = null
        )

        whenever(transactionRelayService.resolveDispute(contractAddress, recipientAddress))
            .thenReturn(mockResult)

        val result = chainController.resolveDispute(request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body!!.success)
        assertEquals(transactionHash, result.body!!.transactionHash)
        assertNull(result.body!!.error)

        verify(transactionRelayService).resolveDispute(contractAddress, recipientAddress)
    }

    // ============== APPROVE USDC TESTS ==============

    @Test
    fun `approveUSDC - successful USDC approval`() = runBlocking {
        val userWalletAddress = "0x9876543210fedcba9876543210fedcba98765432"
        val signedTransaction = "0xf86c8082520894..."
        val transactionHash = "0xabcdef1234567890abcdef1234567890abcdef12"

        val request = ApproveUSDCRequest(
            userWalletAddress = userWalletAddress,
            signedTransaction = signedTransaction
        )

        val mockResult = TransactionResult(
            success = true,
            transactionHash = transactionHash,
            error = null
        )

        whenever(transactionRelayService.approveUSDCWithGasTransfer(userWalletAddress, signedTransaction))
            .thenReturn(mockResult)

        val result = chainController.approveUSDC(request)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertTrue(result.body!!.success)
        assertEquals(transactionHash, result.body!!.transactionHash)
        assertNull(result.body!!.error)

        verify(transactionRelayService).approveUSDCWithGasTransfer(userWalletAddress, signedTransaction)
    }

    // ============== GET GAS COSTS TESTS ==============

    @Test
    fun `getGasCosts - successful gas costs retrieval`() {
        val mockOperationCosts = listOf(
            OperationGasCost(
                operation = "createContract",
                gasLimit = 500000L,
                gasPriceWei = BigInteger.valueOf(20000000000),
                totalCostWei = BigInteger.valueOf(10000000000000000L),
                totalCostAvax = "0.01"
            ),
            OperationGasCost(
                operation = "depositFunds",
                gasLimit = 200000L,
                gasPriceWei = BigInteger.valueOf(20000000000),
                totalCostWei = BigInteger.valueOf(4000000000000000L),
                totalCostAvax = "0.004"
            )
        )

        whenever(transactionRelayService.getOperationGasCosts()).thenReturn(mockOperationCosts)

        val result = chainController.getGasCosts()

        assertEquals(HttpStatus.OK, result.statusCode)
        assertNotNull(result.body)
        assertEquals(2, result.body!!.operations.size)
        assertNotNull(result.body!!.timestamp)

        val operations = result.body!!.operations
        assertEquals("createContract", operations[0].operation)
        assertEquals(500000L, operations[0].gasLimit)
        assertEquals("depositFunds", operations[1].operation)
        assertEquals(200000L, operations[1].gasLimit)

        verify(transactionRelayService).getOperationGasCosts()
    }

    @Test
    fun `getGasCosts - handles service exception`() {
        whenever(transactionRelayService.getOperationGasCosts())
            .thenThrow(RuntimeException("Gas calculation failed"))

        val result = chainController.getGasCosts()

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
        assertNotNull(result.body)
        assertEquals(0, result.body!!.operations.size)
        assertNotNull(result.body!!.timestamp)

        verify(transactionRelayService).getOperationGasCosts()
    }

}