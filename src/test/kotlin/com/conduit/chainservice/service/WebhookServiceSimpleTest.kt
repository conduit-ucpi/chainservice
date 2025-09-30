package com.conduit.chainservice.service

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@ExtendWith(MockitoExtension::class)
class WebhookServiceSimpleTest {

    @Mock
    private lateinit var webClientBuilder: WebClient.Builder

    @Mock
    private lateinit var webClient: WebClient

    @Mock
    private lateinit var requestBodyUriSpec: WebClient.RequestBodyUriSpec

    @Mock
    private lateinit var requestBodySpec: WebClient.RequestBodySpec

    @Mock
    private lateinit var requestHeadersSpec: WebClient.RequestHeadersSpec<*>

    @Mock
    private lateinit var responseSpec: WebClient.ResponseSpec

    private fun createService(): WebhookService {
        whenever(webClientBuilder.defaultHeader(any(), any())).thenReturn(webClientBuilder)
        whenever(webClientBuilder.build()).thenReturn(webClient)
        return WebhookService(webClientBuilder)
    }

    @Test
    fun `webhook service should initialize correctly`() {
        // Given & When
        val service = WebhookService(webClientBuilder)

        // Then
        assertNotNull(service)
    }

    @Test
    fun `webhook result should have correct properties`() {
        // Given
        val result = WebhookResult(
            success = true,
            responseBody = "OK",
            error = null,
            statusCode = 200
        )

        // Then
        assertTrue(result.success)
        assertEquals("OK", result.responseBody)
        assertNull(result.error)
        assertEquals(200, result.statusCode)
    }

    @Test
    fun `webhook result should handle failure case`() {
        // Given
        val result = WebhookResult(
            success = false,
            responseBody = null,
            error = "Connection failed",
            statusCode = 500
        )

        // Then
        assertFalse(result.success)
        assertNull(result.responseBody)
        assertEquals("Connection failed", result.error)
        assertEquals(500, result.statusCode)
    }
}