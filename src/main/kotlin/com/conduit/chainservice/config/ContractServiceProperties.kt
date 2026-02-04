package com.conduit.chainservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "contract-service")
data class ContractServiceProperties(
    var url: String = "",
    var enabled: Boolean = true,
    var apiKey: String = ""
)
