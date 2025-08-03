package com.conduit.chainservice.validation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*

class ApiValidatorTest {
    
    private lateinit var validator: ApiValidator
    
    @BeforeEach
    fun setUp() {
        validator = ApiValidator(timeoutMs = 5000)
    }
    
    @Test
    @DisplayName("Should handle service unavailable gracefully")
    fun shouldHandleServiceUnavailableGracefully() {
        // Test with a non-existent service URL
        val result = validator.validateUserService("http://localhost:99999")
        
        assertFalse(result.isServiceAvailable)
        assertTrue(result.hasErrors())
        assertTrue(result.errors.any { it.type == ErrorType.SERVICE_UNAVAILABLE })
    }
    
    @Test
    @DisplayName("Should validate UserService client specifications")
    fun shouldValidateUserServiceClientSpecs() {
        assertDoesNotThrow {
            val endpoints = UserServiceClientSpec.getExpectedEndpoints()
            assertTrue(endpoints.isNotEmpty())
            
            val identityEndpoint = endpoints.find { it.path == "/api/user/identity" }
            assertTrue(identityEndpoint != null)
            assertEquals("GET", identityEndpoint?.method)
            assertTrue(identityEndpoint?.requiresAuthentication == true)
        }
    }
    
    @Test
    @DisplayName("Should validate ContractService client specifications")
    fun shouldValidateContractServiceClientSpecs() {
        assertDoesNotThrow {
            val endpoints = ContractServiceClientSpec.getExpectedEndpoints()
            assertTrue(endpoints.isNotEmpty())
            
            val updateEndpoint = endpoints.find { 
                it.path == "/api/contracts/{contractId}" && it.method == "PATCH" 
            }
            assertTrue(updateEndpoint != null)
            assertTrue(updateEndpoint?.requiresAuthentication == true)
            assertTrue(updateEndpoint?.requestBodySchema != null)
        }
    }
    
    @Test
    @DisplayName("Should validate EmailService client specifications")
    fun shouldValidateEmailServiceClientSpecs() {
        assertDoesNotThrow {
            val endpoints = EmailServiceClientSpec.getExpectedEndpoints()
            assertTrue(endpoints.isNotEmpty())
            
            val paymentNotificationEndpoint = endpoints.find { 
                it.path == "/api/email/payment-notification" 
            }
            assertTrue(paymentNotificationEndpoint != null)
            assertEquals("POST", paymentNotificationEndpoint?.method)
            assertTrue(paymentNotificationEndpoint?.requestBodySchema != null)
        }
    }
    
    @Test
    @DisplayName("Should identify critical endpoints correctly")
    fun shouldIdentifyCriticalEndpointsCorrectly() {
        val userCritical = UserServiceClientSpec.getCriticalEndpoints()
        val contractCritical = ContractServiceClientSpec.getCriticalEndpoints()
        val emailCritical = EmailServiceClientSpec.getCriticalEndpoints()
        
        // Verify that critical endpoints are identified
        assertTrue(userCritical.isNotEmpty())
        assertTrue(contractCritical.isNotEmpty())
        assertTrue(emailCritical.isNotEmpty())
        
        // Verify that all critical endpoints require authentication (except health)
        userCritical.filter { !it.path.contains("health") }.forEach { endpoint ->
            assertTrue(endpoint.requiresAuthentication, "Critical endpoint ${endpoint.path} should require authentication")
        }
    }
    
    @Test
    @DisplayName("Should create validation result with correct structure")
    fun shouldCreateValidationResultWithCorrectStructure() {
        val errors = listOf(
            ValidationError(ErrorType.ENDPOINT_NOT_FOUND, "Test error")
        )
        val warnings = listOf(
            ValidationWarning(WarningType.DEPRECATED_ENDPOINT, "Test warning")
        )
        
        val result = ValidationResult(
            serviceName = "Test Service",
            serviceUrl = "http://test.example.com",
            isServiceAvailable = true,
            errors = errors,
            warnings = warnings
        )
        
        assertEquals("Test Service", result.serviceName)
        assertEquals("http://test.example.com", result.serviceUrl)
        assertTrue(result.isServiceAvailable)
        assertTrue(result.hasErrors())
        assertTrue(result.hasWarnings())
        assertFalse(result.isSuccess())
    }
    
    @Test
    @DisplayName("Should handle network timeouts gracefully")
    fun shouldHandleNetworkTimeoutsGracefully() {
        // Use a very short timeout to simulate timeout scenarios
        val shortTimeoutValidator = ApiValidator(timeoutMs = 1)
        
        val result = shortTimeoutValidator.validateUserService("http://httpbin.org/delay/5")
        
        assertFalse(result.isServiceAvailable)
        assertTrue(result.hasErrors())
    }
}