package com.conduit.chainservice.validation

/**
 * Defines the expected API endpoints for the Contract Service that chainservice depends on.
 * Based on the ContractServiceClient implementation.
 */
object ContractServiceClientSpec {
    
    fun getExpectedEndpoints(): List<ExpectedEndpoint> {
        return listOf(
            // Update Contract with Deployment - used after contract creation
            ExpectedEndpoint(
                path = "/api/contracts/{contractId}",
                method = "PATCH",
                description = "Update contract with deployment details (chainAddress, chainId, buyerAddress, state)",
                requestBodySchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "chainAddress" to mapOf("type" to "string"),
                        "chainId" to mapOf("type" to "string"),
                        "buyerAddress" to mapOf("type" to "string"),
                        "state" to mapOf("type" to "string")
                    ),
                    "required" to listOf("chainAddress", "chainId", "buyerAddress", "state")
                ),
                responseSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "id" to mapOf("type" to "string"),
                        "chainAddress" to mapOf("type" to "string"),
                        "chainId" to mapOf("type" to "string"),
                        "status" to mapOf("type" to "string")
                    )
                ),
                requiresAuthentication = true,
                tags = listOf("contract-management", "critical")
            ),
            
            // Get Contract - used to retrieve contract details
            ExpectedEndpoint(
                path = "/api/contracts/{contractId}",
                method = "GET",
                description = "Get contract details by ID",
                responseSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "id" to mapOf("type" to "string"),
                        "chainAddress" to mapOf("type" to "string"),
                        "chainId" to mapOf("type" to "string"),
                        "state" to mapOf("type" to "string"),
                        "buyer" to mapOf("type" to "string"),
                        "seller" to mapOf("type" to "string"),
                        "amount" to mapOf("type" to "number"),
                        "description" to mapOf("type" to "string")
                    )
                ),
                requiresAuthentication = true,
                tags = listOf("contract-management", "critical")
            ),
            
            // Update Contract Status - used for state transitions
            ExpectedEndpoint(
                path = "/api/contracts/{contractId}",
                method = "PATCH",
                description = "Update contract state (alternative pattern for status updates)",
                requestBodySchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "state" to mapOf("type" to "string")
                    ),
                    "required" to listOf("state")
                ),
                responseSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "id" to mapOf("type" to "string"),
                        "state" to mapOf("type" to "string"),
                        "updatedAt" to mapOf("type" to "string")
                    )
                ),
                requiresAuthentication = true,
                tags = listOf("contract-management", "critical")
            ),
            
            // Health endpoint for service availability check
            ExpectedEndpoint(
                path = "/actuator/health",
                method = "GET",
                description = "Service health check endpoint",
                responseSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "status" to mapOf("type" to "string")
                    )
                ),
                requiresAuthentication = false,
                tags = listOf("health")
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