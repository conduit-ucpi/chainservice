package com.conduit.chainservice.validation

import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.parser.core.models.SwaggerParseResult
import org.slf4j.LoggerFactory
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Core API validation logic that compares client expectations with remote OpenAPI specs.
 */
class ApiValidator(
    private val timeoutMs: Long = 30000
) {
    private val logger = LoggerFactory.getLogger(ApiValidator::class.java)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMs))
        .build()
    
    /**
     * Validates User Service API compatibility.
     */
    fun validateUserService(serviceUrl: String): ValidationResult {
        logger.info("Validating User Service API at: $serviceUrl")
        
        val expectedEndpoints = UserServiceClientSpec.getExpectedEndpoints()
        return validateService("User Service", serviceUrl, expectedEndpoints)
    }
    
    /**
     * Validates Contract Service API compatibility.
     */
    fun validateContractService(serviceUrl: String): ValidationResult {
        logger.info("Validating Contract Service API at: $serviceUrl")
        
        val expectedEndpoints = ContractServiceClientSpec.getExpectedEndpoints()
        return validateService("Contract Service", serviceUrl, expectedEndpoints)
    }
    
    /**
     * Validates Email Service API compatibility.
     */
    fun validateEmailService(serviceUrl: String): ValidationResult {
        logger.info("Validating Email Service API at: $serviceUrl")
        
        val expectedEndpoints = EmailServiceClientSpec.getExpectedEndpoints()
        return validateService("Email Service", serviceUrl, expectedEndpoints)
    }
    
    /**
     * Generic service validation logic.
     */
    private fun validateService(
        serviceName: String,
        serviceUrl: String,
        expectedEndpoints: List<ExpectedEndpoint>
    ): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        val endpointValidations = mutableListOf<EndpointValidation>()
        
        try {
            // First check if service is available
            val isAvailable = checkServiceAvailability(serviceUrl)
            if (!isAvailable) {
                return ValidationResult(
                    serviceName = serviceName,
                    serviceUrl = serviceUrl,
                    isServiceAvailable = false,
                    errors = listOf(
                        ValidationError(
                            type = ErrorType.SERVICE_UNAVAILABLE,
                            message = "Service is not available at $serviceUrl"
                        )
                    )
                )
            }
            
            // Fetch OpenAPI spec
            val openApiSpec = fetchOpenApiSpec(serviceUrl)
            if (openApiSpec == null) {
                return ValidationResult(
                    serviceName = serviceName,
                    serviceUrl = serviceUrl,
                    isServiceAvailable = true,
                    errors = listOf(
                        ValidationError(
                            type = ErrorType.OPENAPI_SPEC_NOT_FOUND,
                            message = "Could not fetch OpenAPI specification from service"
                        )
                    )
                )
            }
            
            // Validate each expected endpoint
            expectedEndpoints.forEach { expectedEndpoint ->
                val validation = validateEndpoint(openApiSpec, expectedEndpoint)
                endpointValidations.add(validation)
                
                if (!validation.isValid) {
                    errors.addAll(validation.errors.map { error ->
                        ValidationError(
                            type = ErrorType.ENDPOINT_NOT_FOUND,
                            message = error,
                            endpoint = "${expectedEndpoint.method} ${expectedEndpoint.path}"
                        )
                    })
                }
                
                warnings.addAll(validation.warnings.map { warning ->
                    ValidationWarning(
                        type = WarningType.RESPONSE_FORMAT_CHANGE,
                        message = warning,
                        endpoint = "${expectedEndpoint.method} ${expectedEndpoint.path}"
                    )
                })
            }
            
            return ValidationResult(
                serviceName = serviceName,
                serviceUrl = serviceUrl,
                isServiceAvailable = true,
                errors = errors,
                warnings = warnings,
                validatedEndpoints = endpointValidations
            )
            
        } catch (e: Exception) {
            logger.error("Error validating service $serviceName", e)
            return ValidationResult(
                serviceName = serviceName,
                serviceUrl = serviceUrl,
                isServiceAvailable = false,
                errors = listOf(
                    ValidationError(
                        type = ErrorType.NETWORK_ERROR,
                        message = "Validation failed: ${e.message}",
                        details = e.stackTraceToString()
                    )
                )
            )
        }
    }
    
    /**
     * Checks if a service is available by calling its health endpoint.
     */
    private fun checkServiceAvailability(serviceUrl: String): Boolean {
        return try {
            val healthRequest = HttpRequest.newBuilder()
                .uri(URI.create("$serviceUrl/actuator/health"))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build()
            
            val response = httpClient.send(healthRequest, HttpResponse.BodyHandlers.ofString())
            response.statusCode() in 200..299
        } catch (e: ConnectException) {
            logger.warn("Service unavailable - connection refused: $serviceUrl")
            false
        } catch (e: SocketTimeoutException) {
            logger.warn("Service unavailable - timeout: $serviceUrl")
            false
        } catch (e: Exception) {
            logger.warn("Service availability check failed: $serviceUrl", e)
            false
        }
    }
    
    /**
     * Fetches OpenAPI specification from a service.
     */
    private fun fetchOpenApiSpec(serviceUrl: String): OpenAPI? {
        return try {
            val openApiRequest = HttpRequest.newBuilder()
                .uri(URI.create("$serviceUrl/api-docs"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Accept", "application/json")
                .GET()
                .build()
            
            val response = httpClient.send(openApiRequest, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() != 200) {
                logger.warn("Failed to fetch OpenAPI spec: HTTP ${response.statusCode()}")
                return null
            }
            
            val parseResult: SwaggerParseResult = OpenAPIParser().readContents(response.body(), null, null)
            
            if (parseResult.messages.isNotEmpty()) {
                logger.warn("OpenAPI parsing warnings: ${parseResult.messages}")
            }
            
            parseResult.openAPI
        } catch (e: Exception) {
            logger.error("Failed to fetch OpenAPI specification", e)
            null
        }
    }
    
    /**
     * Validates a specific endpoint against the OpenAPI specification.
     */
    private fun validateEndpoint(openApi: OpenAPI, expectedEndpoint: ExpectedEndpoint): EndpointValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            val paths = openApi.paths ?: run {
                errors.add("No paths found in OpenAPI specification")
                return EndpointValidation(
                    path = expectedEndpoint.path,
                    method = expectedEndpoint.method,
                    isValid = false,
                    errors = errors
                )
            }
            
            val pathItem: PathItem? = paths[expectedEndpoint.path]
            if (pathItem == null) {
                errors.add("Path '${expectedEndpoint.path}' not found in OpenAPI specification")
                return EndpointValidation(
                    path = expectedEndpoint.path,
                    method = expectedEndpoint.method,
                    isValid = false,
                    errors = errors
                )
            }
            
            val operation: Operation? = when (expectedEndpoint.method.uppercase()) {
                "GET" -> pathItem.get
                "POST" -> pathItem.post
                "PUT" -> pathItem.put
                "PATCH" -> pathItem.patch
                "DELETE" -> pathItem.delete
                else -> {
                    errors.add("Unsupported HTTP method: ${expectedEndpoint.method}")
                    return EndpointValidation(
                        path = expectedEndpoint.path,
                        method = expectedEndpoint.method,
                        isValid = false,
                        errors = errors
                    )
                }
            }
            
            if (operation == null) {
                errors.add("Method '${expectedEndpoint.method}' not found for path '${expectedEndpoint.path}'")
                return EndpointValidation(
                    path = expectedEndpoint.path,
                    method = expectedEndpoint.method,
                    isValid = false,
                    errors = errors
                )
            }
            
            // Validate request body if expected
            expectedEndpoint.requestBodySchema?.let { expectedSchema ->
                validateRequestBody(operation, expectedSchema, errors, warnings)
            }
            
            // Validate response schema if expected
            expectedEndpoint.responseSchema?.let { expectedSchema ->
                validateResponseSchema(operation, expectedSchema, errors, warnings)
            }
            
            // Check for deprecated endpoint
            if (operation.deprecated == true) {
                warnings.add("Endpoint is marked as deprecated")
            }
            
            return EndpointValidation(
                path = expectedEndpoint.path,
                method = expectedEndpoint.method,
                isValid = errors.isEmpty(),
                errors = errors,
                warnings = warnings
            )
            
        } catch (e: Exception) {
            logger.error("Error validating endpoint ${expectedEndpoint.method} ${expectedEndpoint.path}", e)
            errors.add("Validation error: ${e.message}")
            return EndpointValidation(
                path = expectedEndpoint.path,
                method = expectedEndpoint.method,
                isValid = false,
                errors = errors
            )
        }
    }
    
    private fun validateRequestBody(
        operation: Operation,
        expectedSchema: Map<String, Any>,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val requestBody = operation.requestBody
        if (requestBody == null) {
            if (expectedSchema.isNotEmpty()) {
                errors.add("Expected request body but none found in specification")
            }
            return
        }
        
        val content = requestBody.content?.get("application/json")
        if (content == null) {
            errors.add("Expected JSON request body but not found in specification")
            return
        }
        
        // Basic schema validation - can be enhanced further
        val schema = content.schema
        if (schema == null) {
            warnings.add("Request body schema not defined")
        }
    }
    
    private fun validateResponseSchema(
        operation: Operation,
        expectedSchema: Map<String, Any>,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val responses = operation.responses
        if (responses == null || responses.isEmpty()) {
            errors.add("No response definitions found")
            return
        }
        
        val successResponse = responses["200"] ?: responses["201"]
        if (successResponse == null) {
            warnings.add("No success response (200/201) defined")
            return
        }
        
        val content = successResponse.content?.get("application/json")
        if (content == null) {
            warnings.add("No JSON response content defined for success response")
            return
        }
        
        // Basic schema validation - can be enhanced further
        val schema = content.schema
        if (schema == null) {
            warnings.add("Response schema not defined")
        }
    }
}