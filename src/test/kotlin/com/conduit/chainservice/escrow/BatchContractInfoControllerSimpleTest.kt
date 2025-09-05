package com.conduit.chainservice.escrow

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.escrow.models.*
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.ContractServiceClient
import com.conduit.chainservice.service.EmailServiceClient
import com.conduit.chainservice.service.GasPayerServiceClient
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import java.math.BigInteger
import java.time.Instant

class BatchContractInfoControllerSimpleTest {

    @Mock
    private lateinit var escrowTransactionService: EscrowTransactionService

    @Mock
    private lateinit var contractQueryService: ContractQueryService

    @Mock
    private lateinit var contractServiceClient: ContractServiceClient

    @Mock
    private lateinit var emailServiceClient: EmailServiceClient

    @Mock
    private lateinit var gasPayerServiceClient: GasPayerServiceClient

    @Mock
    private lateinit var escrowProperties: EscrowProperties

    @Mock
    private lateinit var httpServletRequest: HttpServletRequest

    private lateinit var escrowController: EscrowController

    private val contract1 = "0xaaa1111111111111111111111111111111111111"
    private val contract2 = "0xbbb2222222222222222222222222222222222222"
    private val userWallet = "0x1234567890abcdef1234567890abcdef12345678"

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        // Mock the USDC contract address
        whenever(escrowProperties.usdcContractAddress).thenReturn("0x5425890298aed601595a70AB815c96711a31Bc65")
        
        escrowController = EscrowController(
            escrowTransactionService,
            contractQueryService,
            contractServiceClient,
            emailServiceClient,
            gasPayerServiceClient,
            escrowProperties
        )
    }

    @Test
    fun `getBatchContractInfo - successful batch query for admin user`() {
        val request = BatchContractInfoRequest(listOf(contract1, contract2))
        
        val mockResults = mapOf(
            contract1 to ContractInfoResult(
                success = true,
                contractInfo = createMockContractInfo(contract1, "buyer1", "seller1"),
                error = null
            ),
            contract2 to ContractInfoResult(
                success = true,
                contractInfo = createMockContractInfo(contract2, "buyer2", "seller2"),
                error = null
            )
        )

        runBlocking {
            whenever(contractQueryService.getBatchContractInfo(any())).thenReturn(mockResults)
        }

        whenever(httpServletRequest.getAttribute("userType")).thenReturn("admin")
        whenever(httpServletRequest.getAttribute("userId")).thenReturn("admin123")
        whenever(httpServletRequest.getAttribute("userWallet")).thenReturn(userWallet)

        val response = escrowController.getBatchContractInfo(request, httpServletRequest)

        assertEquals(HttpStatus.OK, response.statusCode)
        val responseBody = response.body as BatchContractInfoJsonResponse
        assertEquals(2, responseBody.contracts.size)
        assertEquals(0, responseBody.errors.size)
        assertTrue(responseBody.contracts.containsKey(contract1))
        assertTrue(responseBody.contracts.containsKey(contract2))
        assertEquals("ACTIVE", responseBody.contracts[contract1]?.status)
        assertEquals("ACTIVE", responseBody.contracts[contract2]?.status)
        assertEquals("0x5425890298aed601595a70AB815c96711a31Bc65", responseBody.contracts[contract1]?.tokenAddress)
    }

    @Test
    fun `getBatchContractInfo - handles empty request validation`() {
        // The BatchContractInfoRequest init block will throw IllegalArgumentException for empty list
        // so we test the controller's handling of that
        assertThrows(IllegalArgumentException::class.java) {
            BatchContractInfoRequest(emptyList())
        }
    }

    @Test
    fun `getBatchContractInfo - handles service exception`() {
        val request = BatchContractInfoRequest(listOf(contract1))

        runBlocking {
            whenever(contractQueryService.getBatchContractInfo(any())).thenThrow(RuntimeException("Database error"))
        }

        whenever(httpServletRequest.getAttribute("userType")).thenReturn("admin")
        whenever(httpServletRequest.getAttribute("userId")).thenReturn("admin123")

        val response = escrowController.getBatchContractInfo(request, httpServletRequest)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        val responseBody = response.body as ErrorResponse
        assertEquals("Internal Server Error", responseBody.error)
        assertEquals("Database error", responseBody.message)
    }

    @Test
    fun `getBatchContractInfo - handles partial failures`() {
        val request = BatchContractInfoRequest(listOf(contract1, contract2))
        
        val mockResults = mapOf(
            contract1 to ContractInfoResult(
                success = true,
                contractInfo = createMockContractInfo(contract1, userWallet, "seller1"),
                error = null
            ),
            contract2 to ContractInfoResult(
                success = false,
                contractInfo = null,
                error = "Contract not found"
            )
        )

        runBlocking {
            whenever(contractQueryService.getBatchContractInfo(any())).thenReturn(mockResults)
        }

        whenever(httpServletRequest.getAttribute("userType")).thenReturn("admin")
        whenever(httpServletRequest.getAttribute("userId")).thenReturn("admin123")

        val response = escrowController.getBatchContractInfo(request, httpServletRequest)

        assertEquals(HttpStatus.OK, response.statusCode)
        val responseBody = response.body as BatchContractInfoJsonResponse
        assertEquals(1, responseBody.contracts.size)
        assertEquals(1, responseBody.errors.size)
        assertTrue(responseBody.contracts.containsKey(contract1))
        assertTrue(responseBody.errors.containsKey(contract2))
        assertEquals("ACTIVE", responseBody.contracts[contract1]?.status)
        assertEquals("Contract not found", responseBody.errors[contract2])
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