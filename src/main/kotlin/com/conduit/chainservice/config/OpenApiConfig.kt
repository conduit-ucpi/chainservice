package com.conduit.chainservice.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Value("\${server.port:8978}")
    private val serverPort: String = "8978"

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Conduit UCPI Chain Service")
                    .description(
                        """
                        A Kotlin/Spring Boot microservice that acts as a transaction relayer for blockchain interactions.
                        
                        ## Features
                        - **Transaction Relaying**: Pays gas fees for user-signed blockchain transactions
                        - **Contract Management**: Handles escrow contract creation and queries
                        - **Dispute Resolution**: Supports raising and resolving contract disputes
                        - **Fund Claims**: Enables sellers to claim escrowed funds
                        
                        ## Authentication
                        This service currently operates without authentication. In production, implement proper API key or JWT-based authentication.
                        
                        ## Rate Limiting
                        Consider implementing rate limiting per IP address to prevent abuse of the gas-paying service.
                        """.trimIndent()
                    )
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("Conduit UCPI Team")
                            .email("support@conduit.com")
                    )
                    .license(
                        License()
                            .name("Private License")
                            .url("https://conduit.com/license")
                    )
            )
            .addServersItem(
                Server()
                    .url("http://localhost:$serverPort")
                    .description("Local development server")
            )
            .addServersItem(
                Server()
                    .url("https://api.conduit.com")
                    .description("Production server")
            )
    }
}