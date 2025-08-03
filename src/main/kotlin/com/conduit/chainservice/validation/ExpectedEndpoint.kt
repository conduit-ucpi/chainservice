package com.conduit.chainservice.validation

/**
 * Represents an expected API endpoint that the chainservice depends on.
 */
data class ExpectedEndpoint(
    val path: String,
    val method: String,
    val description: String,
    val requestBodySchema: Map<String, Any>? = null,
    val responseSchema: Map<String, Any>? = null,
    val requiresAuthentication: Boolean = false,
    val tags: List<String> = emptyList()
)