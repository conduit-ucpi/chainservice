package com.conduit.chainservice.service

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class WebhookService(
    private val webClientBuilder: WebClient.Builder
) {
    private val logger = LoggerFactory.getLogger(WebhookService::class.java)

    /**
     * Sends a webhook to WordPress with transaction verification result
     */
    suspend fun sendWebhook(
        webhookUrl: String,
        contractId: String,
        orderId: Int,
        transactionHash: String,
        amount: Double
    ): WebhookResult {
        return try {
            logger.info("Sending webhook to WordPress: url=$webhookUrl, contractId=$contractId, orderId=$orderId")

            val webhookPayload = mapOf(
                "contract_id" to contractId,
                "status" to "funded",
                "order_id" to orderId,
                "transaction_hash" to transactionHash,
                "amount" to amount,
                "timestamp" to Instant.now().epochSecond
            )

            val webClient = webClientBuilder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "Conduit-ChainService/1.0")
                .build()

            val response = webClient.post()
                .uri(webhookUrl)
                .bodyValue(webhookPayload)
                .retrieve()
                .bodyToMono(String::class.java)
                .doOnSuccess { responseBody ->
                    logger.info("Webhook sent successfully to $webhookUrl. Response: ${responseBody?.take(200)}")
                }
                .onErrorResume { error ->
                    when (error) {
                        is WebClientResponseException -> {
                            logger.error("Webhook failed with HTTP ${error.statusCode}: ${error.responseBodyAsString}")
                            Mono.error(error)
                        }
                        else -> {
                            logger.error("Webhook failed with error: ${error.message}", error)
                            Mono.error(error)
                        }
                    }
                }
                .block()

            WebhookResult(
                success = true,
                responseBody = response
            )

        } catch (e: WebClientResponseException) {
            logger.error("Webhook HTTP error: ${e.statusCode} - ${e.responseBodyAsString}")
            WebhookResult(
                success = false,
                error = "HTTP ${e.statusCode}: ${e.responseBodyAsString}",
                statusCode = e.statusCode.value()
            )
        } catch (e: Exception) {
            logger.error("Webhook failed for $webhookUrl", e)
            WebhookResult(
                success = false,
                error = e.message ?: "Failed to send webhook"
            )
        }
    }
}

/**
 * Result of webhook sending operation
 */
data class WebhookResult(
    val success: Boolean,
    val responseBody: String? = null,
    val error: String? = null,
    val statusCode: Int? = null
)