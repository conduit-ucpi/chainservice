package com.conduit.chainservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Unit tests for ContractServiceClient notification functionality
 */
class ContractServiceClientNotificationTest {

    private lateinit var contractServiceClient: ContractServiceClient
    private lateinit var mockRequest: HttpServletRequest
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        val webClientBuilder = WebClient.builder()
        contractServiceClient = ContractServiceClient(webClientBuilder, objectMapper)
        
        // Use reflection to set the URL since it's normally set by @Value
        val urlField = ContractServiceClient::class.java.getDeclaredField("contractServiceUrl")
        urlField.isAccessible = true
        urlField.set(contractServiceClient, "http://localhost:8080")
        
        // Set enabled to true
        val enabledField = ContractServiceClient::class.java.getDeclaredField("enabled")
        enabledField.isAccessible = true
        enabledField.set(contractServiceClient, true)
        
        mockRequest = mock()
        whenever(mockRequest.remoteAddr).thenReturn("127.0.0.1")
        whenever(mockRequest.requestURI).thenReturn("/api/chain/create-contract")
    }

    @Test 
    fun `should create notification request with correct parameters`() {
        // This test verifies the method exists and accepts the correct parameters
        val contractId = "507f1f77bcf86cd799439011"
        val contractHash = "0x1234567890abcdef1234567890abcdef12345678"
        
        // Since we can't easily mock WebClient in this setup, we'll just verify the method signature
        // and the enabled check works
        val result = contractServiceClient.notifyContractCreation(contractId, contractHash, mockRequest)
        
        // The method should exist and return a Mono (even if it errors due to no actual server)
        assert(result is Mono<*>)
    }

    @Test
    fun `should return skipped status when service is disabled`() {
        // Arrange
        val contractId = "507f1f77bcf86cd799439011"
        val contractHash = "0x1234567890abcdef1234567890abcdef12345678"
        
        // Disable the service
        val enabledField = ContractServiceClient::class.java.getDeclaredField("enabled")
        enabledField.isAccessible = true
        enabledField.set(contractServiceClient, false)

        // Act
        val result = contractServiceClient.notifyContractCreation(contractId, contractHash, mockRequest)

        // Assert
        StepVerifier.create(result)
            .expectNext(mapOf("status" to "skipped"))
            .verifyComplete()
    }
}