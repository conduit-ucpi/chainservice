package com.conduit.chainservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    var allowedOrigins: String = "http://localhost:3000"
)

@Configuration
@EnableWebMvc
class WebConfig(private val corsProperties: CorsProperties) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*corsProperties.allowedOrigins.split(",").map { it.trim() }.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600)
    }
}