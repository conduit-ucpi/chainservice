package com.conduit.chainservice.validation

/**
 * Defines the expected API endpoints for the User Service that chainservice depends on.
 * Based on the UserServiceClient implementation.
 */
object UserServiceClientSpec {
    
    fun getExpectedEndpoints(): List<ExpectedEndpoint> {
        return listOf(
            // User Identity Endpoint - used for token validation
            ExpectedEndpoint(
                path = "/api/user/identity",
                method = "GET",
                description = "Get user identity from token - used for authentication validation",
                responseSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "userId" to mapOf("type" to "string"),
                        "email" to mapOf("type" to "string"),
                        "walletAddress" to mapOf("type" to "string"),
                        "userType" to mapOf("type" to "string")
                    ),
                    "required" to listOf("userId", "email", "walletAddress", "userType")
                ),
                requiresAuthentication = true,
                tags = listOf("authentication", "critical")
            )
        )
    }
    
    /**
     * Gets endpoints that are critical for chainservice operation.
     * If these fail, chainservice cannot function properly.
     */
    fun getCriticalEndpoints(): List<ExpectedEndpoint> {
        return getExpectedEndpoints().filter { "critical" in it.tags }
    }
}