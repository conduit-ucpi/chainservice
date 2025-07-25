package com.conduit.chainservice.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
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
                        
                        ## Transaction Flow
                        For post-creation contract interactions (deposit, claim, dispute), the frontend must:
                        1. Have user sign the transaction with their wallet (to authorize token transfers/contract calls)
                        2. Send the signed transaction hex to this service via the API
                        3. Service relays the signed transaction to blockchain and pays gas fees
                        
                        ## Authentication
                        All API endpoints require authentication via Bearer token obtained from the user service.
                        - **Bearer Token**: Include in Authorization header as `Bearer <token>`
                        - **Session Cookie**: HTTP-only session cookie for additional validation
                        
                        ## Rate Limiting
                        Rate limiting is implemented to prevent abuse of the gas-paying service.
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
            .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
            .components(
                Components()
                    .addSecuritySchemes("bearerAuth", 
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Bearer token obtained from user service")
                    )
            )
    }
}