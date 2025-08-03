package com.conduit.chainservice.validation

import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * Main entry point for API validation tasks.
 * Can be run to validate all services or a specific service.
 */
class ApiValidationRunner {
    
    companion object {
        private val logger = LoggerFactory.getLogger(ApiValidationRunner::class.java)
        
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                val enabled = System.getProperty("api.validation.enabled", "true").toBoolean()
                if (!enabled) {
                    logger.info("API validation is disabled")
                    return
                }
                
                val failOnMismatch = System.getProperty("api.validation.failOnMismatch", "true").toBoolean()
                val timeout = System.getProperty("api.validation.timeout", "30000").toLong()
                
                val validator = ApiValidator(timeout)
                val hasFailures: Boolean
                
                when {
                    args.isNotEmpty() -> {
                        // Validate specific service
                        val serviceName = args[0]
                        logger.info("Validating API compatibility for service: $serviceName")
                        hasFailures = validateSpecificService(validator, serviceName)
                    }
                    else -> {
                        // Validate all services
                        logger.info("Validating API compatibility for all dependent services")
                        hasFailures = validateAllServices(validator)
                    }
                }
                
                if (hasFailures) {
                    logger.error("API validation completed with failures")
                    if (failOnMismatch) {
                        logger.error("Failing build due to API validation failures")
                        exitProcess(1)
                    }
                } else {
                    logger.info("API validation completed successfully")
                }
                
            } catch (e: Exception) {
                logger.error("API validation failed with error", e)
                exitProcess(1)
            }
        }
        
        private fun validateSpecificService(validator: ApiValidator, serviceName: String): Boolean {
            return when (serviceName.lowercase()) {
                "user-service" -> {
                    val userServiceUrl = System.getenv("USER_SERVICE_URL") 
                        ?: "https://api.conduit-ucpi.com/userservice"
                    val result = validator.validateUserService(userServiceUrl)
                    logValidationResult("User Service", result)
                    result.hasErrors()
                }
                "contract-service" -> {
                    val contractServiceUrl = System.getenv("CONTRACT_SERVICE_URL") 
                        ?: "http://localhost:8080"
                    val result = validator.validateContractService(contractServiceUrl)
                    logValidationResult("Contract Service", result)
                    result.hasErrors()
                }
                "email-service" -> {
                    val emailServiceUrl = System.getenv("EMAIL_SERVICE_URL") 
                        ?: "http://localhost:8979"
                    val result = validator.validateEmailService(emailServiceUrl)
                    logValidationResult("Email Service", result)
                    result.hasErrors()
                }
                else -> {
                    logger.error("Unknown service: $serviceName")
                    true
                }
            }
        }
        
        private fun validateAllServices(validator: ApiValidator): Boolean {
            val results = mutableListOf<Boolean>()
            
            // Validate User Service
            System.getProperty("USER_SERVICE_URL")?.let { url ->
                val result = validator.validateUserService(url)
                logValidationResult("User Service", result)
                results.add(result.hasErrors())
            } ?: logger.warn("USER_SERVICE_URL not provided, skipping user service validation")
            
            // Validate Contract Service  
            System.getProperty("CONTRACT_SERVICE_URL")?.let { url ->
                val result = validator.validateContractService(url)
                logValidationResult("Contract Service", result)
                results.add(result.hasErrors())
            } ?: logger.warn("CONTRACT_SERVICE_URL not provided, skipping contract service validation")
            
            // Validate Email Service
            System.getProperty("EMAIL_SERVICE_URL")?.let { url ->
                val result = validator.validateEmailService(url)
                logValidationResult("Email Service", result)
                results.add(result.hasErrors())
            } ?: logger.warn("EMAIL_SERVICE_URL not provided, skipping email service validation")
            
            return results.any { it }
        }
        
        private fun logValidationResult(serviceName: String, result: ValidationResult) {
            if (result.hasErrors()) {
                logger.error("$serviceName validation failed:")
                result.errors.forEach { error ->
                    logger.error("  - ${error.type}: ${error.message}")
                    error.details?.let { details ->
                        logger.error("    Details: $details")
                    }
                }
            } else {
                logger.info("$serviceName validation passed")
            }
            
            if (result.warnings.isNotEmpty()) {
                logger.warn("$serviceName validation warnings:")
                result.warnings.forEach { warning ->
                    logger.warn("  - ${warning.type}: ${warning.message}")
                }
            }
        }
    }
}