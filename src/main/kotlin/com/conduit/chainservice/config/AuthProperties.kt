package com.conduit.chainservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth")
data class AuthProperties(
    var userServiceUrl: String = "",
    var enabled: Boolean = true
)