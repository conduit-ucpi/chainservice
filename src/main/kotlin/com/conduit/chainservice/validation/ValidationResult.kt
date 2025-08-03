package com.conduit.chainservice.validation

/**
 * Represents the result of API validation for a service.
 */
data class ValidationResult(
    val serviceName: String,
    val serviceUrl: String,
    val isServiceAvailable: Boolean,
    val errors: List<ValidationError> = emptyList(),
    val warnings: List<ValidationWarning> = emptyList(),
    val validatedEndpoints: List<EndpointValidation> = emptyList(),
    val validationTimestamp: Long = System.currentTimeMillis()
) {
    fun hasErrors(): Boolean = errors.isNotEmpty() || !isServiceAvailable
    
    fun hasWarnings(): Boolean = warnings.isNotEmpty()
    
    fun isSuccess(): Boolean = !hasErrors()
}

/**
 * Represents a validation error.
 */
data class ValidationError(
    val type: ErrorType,
    val message: String,
    val endpoint: String? = null,
    val details: String? = null
)

/**
 * Represents a validation warning.
 */
data class ValidationWarning(
    val type: WarningType,
    val message: String,
    val endpoint: String? = null,
    val details: String? = null
)

/**
 * Represents validation for a specific endpoint.
 */
data class EndpointValidation(
    val path: String,
    val method: String,
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Types of validation errors.
 */
enum class ErrorType {
    SERVICE_UNAVAILABLE,
    OPENAPI_SPEC_NOT_FOUND,
    OPENAPI_SPEC_INVALID,
    ENDPOINT_NOT_FOUND,
    REQUEST_SCHEMA_MISMATCH,
    RESPONSE_SCHEMA_MISMATCH,
    PARAMETER_MISMATCH,
    AUTHENTICATION_REQUIREMENT_MISMATCH,
    NETWORK_ERROR,
    PARSING_ERROR
}

/**
 * Types of validation warnings.
 */
enum class WarningType {
    DEPRECATED_ENDPOINT,
    OPTIONAL_PARAMETER_MISSING,
    ADDITIONAL_PARAMETER_FOUND,
    RESPONSE_FORMAT_CHANGE,
    VERSION_MISMATCH,
    TIMEOUT_WARNING
}