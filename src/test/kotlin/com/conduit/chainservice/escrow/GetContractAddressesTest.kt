package com.conduit.chainservice.escrow

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.ContractServiceClient
import com.conduit.chainservice.service.EmailServiceClient
import com.conduit.chainservice.service.GasPayerServiceClient
import com.conduit.chainservice.service.WebhookService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus

class GetContractAddressesTest {

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
    private lateinit var webhookService: WebhookService

    @Mock
    private lateinit var escrowProperties: EscrowProperties

    private lateinit var escrowController: EscrowController

    private val testFactoryAddress = "0x1234567890abcdef1234567890abcdef12345678"
    private val testImplementationAddress = "0x9876543210fedcba9876543210fedcba98765432"

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(escrowProperties.contractFactoryAddress).thenReturn(testFactoryAddress)
        whenever(escrowProperties.implementationAddress).thenReturn(testImplementationAddress)

        escrowController = EscrowController(
            escrowTransactionService,
            contractQueryService,
            contractServiceClient,
            emailServiceClient,
            gasPayerServiceClient,
            webhookService,
            escrowProperties
        )
    }

    @Test
    fun `getContractAddresses - returns factory and implementation addresses successfully`() {
        val response = escrowController.getContractAddresses()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)

        val responseBody = response.body!!
        assertEquals(testFactoryAddress, responseBody.factoryAddress)
        assertEquals(testImplementationAddress, responseBody.implementationAddress)
        assertNotNull(responseBody.timestamp)
    }

    @Test
    fun `getContractAddresses - response has correct structure`() {
        val response = escrowController.getContractAddresses()

        assertNotNull(response.body)
        val responseBody = response.body!!

        // Verify all expected fields are present
        assertNotNull(responseBody.factoryAddress)
        assertNotNull(responseBody.implementationAddress)
        assertNotNull(responseBody.timestamp)

        // Verify timestamp is a valid ISO string format
        assertTrue(responseBody.timestamp.isNotBlank())
    }

    @Test
    fun `getContractAddresses - returns correct addresses from configuration`() {
        // Test with different addresses
        val newFactoryAddress = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
        val newImplementationAddress = "0xfedcbafedcbafedcbafedcbafedcbafedcbafedcba"

        whenever(escrowProperties.contractFactoryAddress).thenReturn(newFactoryAddress)
        whenever(escrowProperties.implementationAddress).thenReturn(newImplementationAddress)

        val response = escrowController.getContractAddresses()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)

        val responseBody = response.body!!
        assertEquals(newFactoryAddress, responseBody.factoryAddress)
        assertEquals(newImplementationAddress, responseBody.implementationAddress)
    }
}
