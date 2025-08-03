package com.conduit.chainservice.validation

/**
 * Defines the expected API endpoints for the Contract Service that chainservice depends on.
 * Based on the ContractServiceClient implementation.
 */
object ContractServiceClientSpec {
    
    fun getExpectedEndpoints(): List<ExpectedEndpoint> {
        return listOf(
            // Update Contract - used for both deployment details and state updates
            ExpectedEndpoint(
                path = "/api/contracts/{id}",
                method = "PATCH",
                description = "Update contract with deployment details or state changes",
                requestBodySchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "chainAddress" to mapOf("type" to "string"),
                        "chainId" to mapOf("type" to "string"),
                        "buyerAddress" to mapOf("type" to "string"),
                        "state" to mapOf("type" to "string")
                    )
                    // No required fields since different update operations use different field combinations
                ),
                responseSchema = mapOf(
                    "type" to "object"
                    // Generic response schema since actual response varies by operation
                ),
                requiresAuthentication = true,
                tags = listOf("contract-management", "critical")
            ),
            
            // Get Contract - used to retrieve contract details
            ExpectedEndpoint(
                path = "/api/contracts/{id}",
                method = "GET",
                description = "Get contract details by ID",
                responseSchema = mapOf(
                    "type" to "object"
                    // Generic response schema since we accept any contract data structure
                ),
                requiresAuthentication = true,
                tags = listOf("contract-management", "critical")
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