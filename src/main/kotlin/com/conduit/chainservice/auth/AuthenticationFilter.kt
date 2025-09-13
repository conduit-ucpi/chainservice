package com.conduit.chainservice.auth

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

@Component
class AuthenticationFilter(
    private val userServiceClient: UserServiceClient,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(AuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Skip authentication for health and docs endpoints
        if (shouldSkipAuthentication(request.requestURI)) {
            filterChain.doFilter(request, response)
            return
        }

        // Skip authentication if disabled
        if (!userServiceClient.isAuthEnabled()) {
            logger.debug("Authentication disabled, allowing request")
            filterChain.doFilter(request, response)
            return
        }

        try {
            val bearerToken = extractBearerToken(request)
            val authToken = extractAuthToken(request)
            val httpOnlyToken = extractHttpOnlyToken(request)

            val tokenToValidate = bearerToken ?: authToken
            if (tokenToValidate == null) {
                logger.warn("No auth token provided for protected endpoint: ${request.requestURI}")
                sendUnauthorizedResponse(response, "Auth token required")
                return
            }

            // Validate with user service
            val validationResult = userServiceClient.validateToken(tokenToValidate, httpOnlyToken).block()

            if (validationResult?.valid == true) {
                // Set authentication context
                val authentication = UsernamePasswordAuthenticationToken(
                    validationResult.userId,
                    null,
                    emptyList()
                )
                SecurityContextHolder.getContext().authentication = authentication
                
                // Add user info to request attributes for controllers
                request.setAttribute("userId", validationResult.userId)
                request.setAttribute("userEmail", validationResult.email)
                request.setAttribute("userWallet", validationResult.walletAddress)
                request.setAttribute("userType", validationResult.userType)
                
                logger.debug("Authentication successful for user: ${validationResult.userId}")
                filterChain.doFilter(request, response)
            } else {
                logger.warn("Token validation failed for request: ${request.requestURI}")
                sendUnauthorizedResponse(response, "Invalid or expired token")
            }

        } catch (e: Exception) {
            logger.error("Authentication error", e)
            sendUnauthorizedResponse(response, "Authentication error")
        }
    }

    private fun shouldSkipAuthentication(uri: String): Boolean {
        val publicPaths = listOf(
            "/actuator/health",
            "/actuator/info", 
            "/swagger-ui",
            "/api-docs",
            "/v3/api-docs",
            "/api/chain/claim-funds-as-gas-payer",
            "/api/chain/contracts/batch-info"
        )
        return publicPaths.any { uri.startsWith(it) }
    }

    private fun extractBearerToken(request: HttpServletRequest): String? {
        val authHeader = request.getHeader("Authorization")
        return if (authHeader?.startsWith("Bearer ") == true) {
            authHeader.substring(7)
        } else null
    }

    private fun extractAuthToken(request: HttpServletRequest): String? {
        return request.cookies?.find { it.name == "AUTH-TOKEN" }?.value
    }

    private fun extractHttpOnlyToken(request: HttpServletRequest): String? {
        return request.cookies?.find { it.name == "session" }?.value
    }

    private fun sendUnauthorizedResponse(response: HttpServletResponse, message: String) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        
        val errorResponse = mapOf(
            "error" to "Unauthorized",
            "message" to message,
            "timestamp" to Instant.now().toString()
        )
        
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}