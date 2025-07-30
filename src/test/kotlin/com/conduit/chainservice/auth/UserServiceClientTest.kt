package com.conduit.chainservice.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.utility.chainservice.AuthProperties
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import reactor.test.StepVerifier

class UserServiceClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var userServiceClient: UserServiceClient
    private lateinit var objectMapper: ObjectMapper

    @Mock
    private lateinit var authProperties: AuthProperties

    private val testUserId = "user123"
    private val testEmail = "test@example.com"
    private val testWalletAddress = "0x1234567890abcdef1234567890abcdef12345678"
    private val testAuthToken = "Bearer test-token"
    private val testHttpOnlyToken = "session-token"

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        mockWebServer = MockWebServer()
        mockWebServer.start()

        whenever(authProperties.userServiceUrl).thenReturn(mockWebServer.url("/").toString())

        userServiceClient = UserServiceClient(authProperties)
        objectMapper = ObjectMapper()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `validateToken - successful validation with regular user`() {
        val userIdentityResponse = UserServiceClient.UserIdentityResponse(
            userId = testUserId,
            email = testEmail,
            walletAddress = testWalletAddress,
            userType = "regular"
        )

        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody(objectMapper.writeValueAsString(userIdentityResponse))
            .addHeader("Content-Type", "application/json")

        mockWebServer.enqueue(mockResponse)

        val result = userServiceClient.validateToken(testAuthToken, testHttpOnlyToken)

        StepVerifier.create(result)
            .expectNextMatches { response ->
                response.valid &&
                response.userId == testUserId &&
                response.email == testEmail &&
                response.walletAddress == testWalletAddress &&
                response.userType == "regular"
            }
            .verifyComplete()

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/api/user/identity", recordedRequest.path)
        assertEquals(testAuthToken, recordedRequest.getHeader("Authorization"))
        assertEquals("session=$testHttpOnlyToken", recordedRequest.getHeader("Cookie"))
    }

    @Test
    fun `validateToken - successful validation with admin user`() {
        val userIdentityResponse = UserServiceClient.UserIdentityResponse(
            userId = "admin123",
            email = "admin@example.com",
            walletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            userType = "admin"
        )

        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody(objectMapper.writeValueAsString(userIdentityResponse))
            .addHeader("Content-Type", "application/json")

        mockWebServer.enqueue(mockResponse)

        val result = userServiceClient.validateToken(testAuthToken, null)

        StepVerifier.create(result)
            .expectNextMatches { response ->
                response.valid &&
                response.userId == "admin123" &&
                response.email == "admin@example.com" &&
                response.walletAddress == "0x9876543210fedcba9876543210fedcba98765432" &&
                response.userType == "admin"
            }
            .verifyComplete()

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/api/user/identity", recordedRequest.path)
        assertEquals(testAuthToken, recordedRequest.getHeader("Authorization"))
        assertNull(recordedRequest.getHeader("Cookie"))
    }

    @Test
    fun `validateToken - handles 401 unauthorized`() {
        val mockResponse = MockResponse()
            .setResponseCode(401)
            .setBody("{\"error\": \"Unauthorized\"}")

        mockWebServer.enqueue(mockResponse)

        val result = userServiceClient.validateToken(testAuthToken, testHttpOnlyToken)

        StepVerifier.create(result)
            .expectNextMatches { response ->
                !response.valid &&
                response.userId == null &&
                response.email == null &&
                response.walletAddress == null &&
                response.userType == null
            }
            .verifyComplete()
    }

    @Test
    fun `validateToken - handles 403 forbidden`() {
        val mockResponse = MockResponse()
            .setResponseCode(403)
            .setBody("{\"error\": \"Forbidden\"}")

        mockWebServer.enqueue(mockResponse)

        val result = userServiceClient.validateToken(testAuthToken, testHttpOnlyToken)

        StepVerifier.create(result)
            .expectNextMatches { response ->
                !response.valid &&
                response.userId == null &&
                response.email == null &&
                response.walletAddress == null &&
                response.userType == null
            }
            .verifyComplete()
    }

    @Test
    fun `validateToken - handles network errors`() {
        mockWebServer.shutdown() // Simulate network failure

        val result = userServiceClient.validateToken(testAuthToken, testHttpOnlyToken)

        StepVerifier.create(result)
            .expectNextMatches { response ->
                !response.valid &&
                response.userId == null &&
                response.email == null &&
                response.walletAddress == null &&
                response.userType == null
            }
            .verifyComplete()
    }

    @Test
    fun `validateToken - handles malformed JSON response`() {
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody("{ invalid json }")
            .addHeader("Content-Type", "application/json")

        mockWebServer.enqueue(mockResponse)

        val result = userServiceClient.validateToken(testAuthToken, testHttpOnlyToken)

        StepVerifier.create(result)
            .expectNextMatches { response ->
                !response.valid &&
                response.userId == null &&
                response.email == null &&
                response.walletAddress == null &&
                response.userType == null
            }
            .verifyComplete()
    }

    @Test
    fun `validateToken - without httpOnlyToken parameter`() {
        val userIdentityResponse = UserServiceClient.UserIdentityResponse(
            userId = testUserId,
            email = testEmail,
            walletAddress = testWalletAddress,
            userType = "regular"
        )

        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody(objectMapper.writeValueAsString(userIdentityResponse))
            .addHeader("Content-Type", "application/json")

        mockWebServer.enqueue(mockResponse)

        val result = userServiceClient.validateToken(testAuthToken, null)

        StepVerifier.create(result)
            .expectNextMatches { response ->
                response.valid &&
                response.userType == "regular"
            }
            .verifyComplete()

        val recordedRequest = mockWebServer.takeRequest()
        assertNull(recordedRequest.getHeader("Cookie"))
    }

    @Test
    fun `isAuthEnabled - returns auth properties enabled value`() {
        whenever(authProperties.enabled).thenReturn(true)
        assertTrue(userServiceClient.isAuthEnabled())

        whenever(authProperties.enabled).thenReturn(false)
        assertFalse(userServiceClient.isAuthEnabled())
    }
}