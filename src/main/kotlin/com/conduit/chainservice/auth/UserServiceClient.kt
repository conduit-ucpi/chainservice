package com.conduit.chainservice.auth

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Service
class UserServiceClient(
    private val authProperties: AuthProperties
) {

    private val logger = LoggerFactory.getLogger(UserServiceClient::class.java)
    
    private val webClient = WebClient.builder()
        .baseUrl(authProperties.userServiceUrl)
        .build()

    data class TokenValidationResponse(
        val valid: Boolean,
        val userId: String? = null,
        val email: String? = null,
        val walletAddress: String? = null
    )

    fun validateToken(bearerToken: String, httpOnlyToken: String?): Mono<TokenValidationResponse> {
        return try {
            val request = webClient.post()
                .uri("/api/auth/validate")
                .header("Authorization", bearerToken)
                
            // Add http-only token as header if provided
            val requestWithCookie = if (httpOnlyToken != null) {
                request.header("Cookie", "session=$httpOnlyToken")
            } else {
                request
            }
                
            requestWithCookie
                .retrieve()
                .bodyToMono(TokenValidationResponse::class.java)
                .doOnSuccess { response ->
                    logger.debug("Token validation successful for user: ${response.userId}")
                }
                .onErrorResume { error ->
                    when (error) {
                        is WebClientResponseException -> {
                            logger.warn("Token validation failed with status: ${error.statusCode}")
                            Mono.just(TokenValidationResponse(valid = false))
                        }
                        else -> {
                            logger.error("Error communicating with user service", error)
                            Mono.just(TokenValidationResponse(valid = false))
                        }
                    }
                }
        } catch (e: Exception) {
            logger.error("Error validating token", e)
            Mono.just(TokenValidationResponse(valid = false))
        }
    }

    fun isAuthEnabled(): Boolean = authProperties.enabled
}