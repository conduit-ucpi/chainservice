package com.conduit.chainservice.auth

import com.conduit.chainservice.config.ContractServiceProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Validates API keys for service-to-service authentication.
 * This is a reusable service that can be injected into any controller
 * that needs to validate incoming requests from other services.
 */
@Service
class ServiceApiKeyValidator(
    private val contractServiceProperties: ContractServiceProperties
) {

    private val logger = LoggerFactory.getLogger(ServiceApiKeyValidator::class.java)

    /**
     * Validates that the provided API key matches the expected contract service API key.
     *
     * @param apiKey The API key from the request header (X-API-Key)
     * @return true if the API key is valid, false otherwise
     */
    fun validateContractServiceApiKey(apiKey: String?): Boolean {
        // Check if API key is configured
        if (contractServiceProperties.apiKey.isEmpty()) {
            logger.error("CONTRACT_SERVICE_API_KEY is not configured - service-to-service calls will be rejected")
            return false
        }

        // Check if API key was provided in request
        if (apiKey.isNullOrEmpty()) {
            logger.warn("No X-API-Key header provided in request")
            return false
        }

        // Validate API key matches
        val isValid = apiKey == contractServiceProperties.apiKey
        if (!isValid) {
            logger.warn("Invalid X-API-Key provided in request")
        }

        return isValid
    }
}
