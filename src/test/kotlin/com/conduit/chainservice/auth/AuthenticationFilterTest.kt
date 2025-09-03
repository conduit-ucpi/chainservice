package com.conduit.chainservice.auth

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when` as mockitoWhen
import org.springframework.security.core.context.SecurityContextHolder
import reactor.core.publisher.Mono
import java.lang.reflect.Method

class AuthenticationFilterTest {

    @Mock
    private lateinit var userServiceClient: UserServiceClient

    @Mock
    private lateinit var request: HttpServletRequest

    @Mock
    private lateinit var response: HttpServletResponse

    @Mock
    private lateinit var filterChain: FilterChain

    @Mock
    private lateinit var responseWriter: java.io.PrintWriter

    private lateinit var authenticationFilter: AuthenticationFilter
    private lateinit var objectMapper: ObjectMapper
    private lateinit var doFilterInternalMethod: Method

    private val testUserId = "user123"
    private val testEmail = "test@example.com"
    private val testWalletAddress = "0x1234567890abcdef1234567890abcdef12345678"
    private val testAuthToken = "Bearer test-token"

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        objectMapper = ObjectMapper()
        authenticationFilter = AuthenticationFilter(userServiceClient, objectMapper)
        
        // Get the protected doFilterInternal method
        doFilterInternalMethod = AuthenticationFilter::class.java.getDeclaredMethod(
            "doFilterInternal",
            HttpServletRequest::class.java,
            HttpServletResponse::class.java,
            FilterChain::class.java
        )
        doFilterInternalMethod.isAccessible = true
        
        // Clear security context before each test
        SecurityContextHolder.clearContext()
        
        // Default mock setup
        whenever(userServiceClient.isAuthEnabled()).thenReturn(true)
        whenever(request.requestURI).thenReturn("/api/contracts/0x1234567890abcdef1234567890abcdef12345678")
        whenever(response.writer).thenReturn(responseWriter)
    }

    private fun callDoFilterInternal() {
        doFilterInternalMethod.invoke(authenticationFilter, request, response, filterChain)
    }

    @Test
    fun `doFilterInternal - sets userType attribute for regular user`() {
        val validationResponse = UserServiceClient.TokenValidationResponse(
            valid = true,
            userId = testUserId,
            email = testEmail,
            walletAddress = testWalletAddress,
            userType = "regular"
        )

        whenever(request.getHeader("Authorization")).thenReturn(testAuthToken)
        whenever(request.cookies).thenReturn(emptyArray())
        whenever(userServiceClient.validateToken("test-token", null))
            .thenReturn(Mono.just(validationResponse))

        callDoFilterInternal()

        verify(request).setAttribute("userId", testUserId)
        verify(request).setAttribute("userEmail", testEmail)
        verify(request).setAttribute("userWallet", testWalletAddress)
        verify(request).setAttribute("userType", "regular")
        verify(filterChain).doFilter(request, response)
        
        val authentication = SecurityContextHolder.getContext().authentication
        assertNotNull(authentication)
        assertEquals(testUserId, authentication.principal)
    }

    @Test
    fun `doFilterInternal - sets userType attribute for admin user`() {
        val validationResponse = UserServiceClient.TokenValidationResponse(
            valid = true,
            userId = "admin123",
            email = "admin@example.com",
            walletAddress = "0x9876543210fedcba9876543210fedcba98765432",
            userType = "admin"
        )

        whenever(request.getHeader("Authorization")).thenReturn(testAuthToken)
        whenever(request.cookies).thenReturn(emptyArray())
        whenever(userServiceClient.validateToken("test-token", null))
            .thenReturn(Mono.just(validationResponse))

        callDoFilterInternal()

        verify(request).setAttribute("userId", "admin123")
        verify(request).setAttribute("userEmail", "admin@example.com")
        verify(request).setAttribute("userWallet", "0x9876543210fedcba9876543210fedcba98765432")
        verify(request).setAttribute("userType", "admin")
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `doFilterInternal - handles null userType gracefully`() {
        val validationResponse = UserServiceClient.TokenValidationResponse(
            valid = true,
            userId = testUserId,
            email = testEmail,
            walletAddress = testWalletAddress,
            userType = null
        )

        whenever(request.getHeader("Authorization")).thenReturn(testAuthToken)
        whenever(request.cookies).thenReturn(emptyArray())
        whenever(userServiceClient.validateToken("test-token", null))
            .thenReturn(Mono.just(validationResponse))

        callDoFilterInternal()

        verify(request).setAttribute("userId", testUserId)
        verify(request).setAttribute("userEmail", testEmail)
        verify(request).setAttribute("userWallet", testWalletAddress)
        verify(request).setAttribute("userType", null)
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `doFilterInternal - uses Bearer token when available`() {
        val bearerToken = "Bearer bearer-token"
        val validationResponse = UserServiceClient.TokenValidationResponse(
            valid = true,
            userId = testUserId,
            email = testEmail,
            walletAddress = testWalletAddress,
            userType = "regular"
        )

        whenever(request.getHeader("Authorization")).thenReturn(bearerToken)
        whenever(request.cookies).thenReturn(emptyArray())
        whenever(userServiceClient.validateToken("bearer-token", null))
            .thenReturn(Mono.just(validationResponse))

        callDoFilterInternal()

        verify(userServiceClient).validateToken("bearer-token", null)
        verify(request).setAttribute("userType", "regular")
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `doFilterInternal - uses cookie auth token when no Bearer token`() {
        val authCookie = mock<Cookie>()
        whenever(authCookie.name).thenReturn("AUTH-TOKEN")
        whenever(authCookie.value).thenReturn("cookie-token")

        val validationResponse = UserServiceClient.TokenValidationResponse(
            valid = true,
            userId = testUserId,
            email = testEmail,
            walletAddress = testWalletAddress,
            userType = "admin"
        )

        whenever(request.getHeader("Authorization")).thenReturn(null)
        whenever(request.cookies).thenReturn(arrayOf(authCookie))
        whenever(userServiceClient.validateToken("cookie-token", null))
            .thenReturn(Mono.just(validationResponse))

        callDoFilterInternal()

        verify(userServiceClient).validateToken("cookie-token", null)
        verify(request).setAttribute("userType", "admin")
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `doFilterInternal - includes httpOnly session cookie when available`() {
        val authCookie = mock<Cookie>()
        whenever(authCookie.name).thenReturn("AUTH-TOKEN")
        whenever(authCookie.value).thenReturn("auth-token")

        val sessionCookie = mock<Cookie>()
        whenever(sessionCookie.name).thenReturn("session")
        whenever(sessionCookie.value).thenReturn("session-token")

        val validationResponse = UserServiceClient.TokenValidationResponse(
            valid = true,
            userId = testUserId,
            email = testEmail,
            walletAddress = testWalletAddress,
            userType = "regular"
        )

        whenever(request.getHeader("Authorization")).thenReturn(null)
        whenever(request.cookies).thenReturn(arrayOf(authCookie, sessionCookie))
        whenever(userServiceClient.validateToken("auth-token", "session-token"))
            .thenReturn(Mono.just(validationResponse))

        callDoFilterInternal()

        verify(userServiceClient).validateToken("auth-token", "session-token")
        verify(request).setAttribute("userType", "regular")
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `doFilterInternal - rejects invalid token`() {
        val validationResponse = UserServiceClient.TokenValidationResponse(
            valid = false,
            userId = null,
            email = null,
            walletAddress = null,
            userType = null
        )

        whenever(request.getHeader("Authorization")).thenReturn(testAuthToken)
        whenever(request.cookies).thenReturn(emptyArray())
        whenever(userServiceClient.validateToken("test-token", null))
            .thenReturn(Mono.just(validationResponse))

        callDoFilterInternal()

        verify(response).status = HttpServletResponse.SC_UNAUTHORIZED
        verify(filterChain, never()).doFilter(request, response)
        verify(request, never()).setAttribute(eq("userType"), any())
    }

    @Test
    fun `doFilterInternal - skips authentication for health endpoints`() {
        whenever(request.requestURI).thenReturn("/actuator/health")

        callDoFilterInternal()

        verify(userServiceClient, never()).validateToken(any(), any())
        verify(filterChain).doFilter(request, response)
        verify(request, never()).setAttribute(eq("userType"), any())
    }

    @Test
    fun `doFilterInternal - skips authentication for swagger endpoints`() {
        whenever(request.requestURI).thenReturn("/swagger-ui/index.html")

        callDoFilterInternal()

        verify(userServiceClient, never()).validateToken(any(), any())
        verify(filterChain).doFilter(request, response)
        verify(request, never()).setAttribute(eq("userType"), any())
    }

    @Test
    fun `doFilterInternal - skips authentication when disabled`() {
        whenever(userServiceClient.isAuthEnabled()).thenReturn(false)

        callDoFilterInternal()

        verify(userServiceClient, never()).validateToken(any(), any())
        verify(filterChain).doFilter(request, response)
        verify(request, never()).setAttribute(eq("userType"), any())
    }

    @Test
    fun `doFilterInternal - handles missing auth token`() {
        whenever(request.getHeader("Authorization")).thenReturn(null)
        whenever(request.cookies).thenReturn(emptyArray())

        callDoFilterInternal()

        verify(response).status = HttpServletResponse.SC_UNAUTHORIZED
        verify(filterChain, never()).doFilter(request, response)
        verify(request, never()).setAttribute(eq("userType"), any())
    }
}