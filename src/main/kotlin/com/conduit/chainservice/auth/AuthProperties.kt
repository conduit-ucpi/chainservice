package com.conduit.chainservice.auth

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "auth")
class AuthProperties {
    var userServiceUrl: String = ""
    var enabled: Boolean = true
    
    private val logger = LoggerFactory.getLogger(AuthProperties::class.java)
    
    @PostConstruct
    fun validateConfiguration() {
        if (enabled && userServiceUrl.isBlank()) {
            val errorMessage = "USER_SERVICE_URL environment variable is required when authentication is enabled"
            logger.error(errorMessage)
            throw IllegalStateException(errorMessage)
        }
        
        if (enabled) {
            logger.info("Authentication enabled with user service: $userServiceUrl")
        } else {
            logger.warn("Authentication is DISABLED - this should only be used in development")
        }
    }
}