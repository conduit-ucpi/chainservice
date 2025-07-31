package com.conduit.chainservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

data class PaymentNotificationRequest(
    val to: String,
    val buyerEmail: String,
    val link: String,
    val description: String,
    val amount: String,
    val payoutDateTime: String
)

data class DisputeRaisedRequest(
    val to: String,
    val buyerEmail: String,
    val amount: String,
    val currency: String,
    val description: String,
    val sellerEmail: String,
    val payoutDateTime: String,
    val productName: String
)

data class DisputeResolvedRequest(
    val to: String,
    val amount: String,
    val currency: String,
    val buyerEmail: String,
    val sellerEmail: String,
    val description: String,
    val payoutDateTime: String,
    val sellerPercentAmount: String,
    val sellerActualAmount: String,
    val buyerPercentAmount: String,
    val buyerActualAmount: String
)

data class SendEmailResponse(
    val success: Boolean,
    val messageId: String?,
    val message: String
)

@Service
class EmailServiceClient(
    private val webClientBuilder: WebClient.Builder,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(EmailServiceClient::class.java)

    @Value("\${email-service.url}")
    private lateinit var emailServiceUrl: String

    @Value("\${email-service.enabled:true}")
    private var enabled: Boolean = true

    private val webClient: WebClient by lazy {
        webClientBuilder
            .baseUrl(emailServiceUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    fun sendPaymentNotification(
        sellerEmail: String,
        buyerEmail: String,
        contractDescription: String,
        amount: String,
        payoutDateTime: String,
        contractLink: String
    ): Mono<SendEmailResponse> {
        if (!enabled) {
            logger.info("Email service integration disabled, skipping payment notification")
            return Mono.just(SendEmailResponse(
                success = true,
                messageId = null,
                message = "Email service disabled"
            ))
        }

        val request = PaymentNotificationRequest(
            to = sellerEmail,
            buyerEmail = buyerEmail,
            link = contractLink,
            description = contractDescription,
            amount = amount,
            payoutDateTime = payoutDateTime
        )

        logger.info("Sending payment notification email to seller: $sellerEmail")

        return webClient.post()
            .uri("/api/email/payment-notification")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(SendEmailResponse::class.java)
            .doOnSuccess { response ->
                logger.info("Successfully sent payment notification email to $sellerEmail: ${response.message}")
            }
            .onErrorResume { error ->
                when (error) {
                    is WebClientResponseException -> {
                        logger.error("Email service returned error: ${error.statusCode} - ${error.responseBodyAsString}")
                        Mono.just(SendEmailResponse(
                            success = false,
                            messageId = null,
                            message = "Email service error: ${error.statusCode}"
                        ))
                    }
                    else -> {
                        logger.error("Failed to send payment notification email", error)
                        Mono.just(SendEmailResponse(
                            success = false,
                            messageId = null,
                            message = "Email service error: ${error.message}"
                        ))
                    }
                }
            }
    }

    fun sendDisputeRaised(
        recipientEmail: String,
        buyerEmail: String,
        sellerEmail: String,
        amount: String,
        currency: String,
        contractDescription: String,
        payoutDateTime: String,
        productName: String
    ): Mono<SendEmailResponse> {
        if (!enabled) {
            logger.info("Email service integration disabled, skipping dispute raised notification")
            return Mono.just(SendEmailResponse(
                success = true,
                messageId = null,
                message = "Email service disabled"
            ))
        }

        val request = DisputeRaisedRequest(
            to = recipientEmail,
            buyerEmail = buyerEmail,
            amount = amount,
            currency = currency,
            description = contractDescription,
            sellerEmail = sellerEmail,
            payoutDateTime = payoutDateTime,
            productName = productName
        )

        logger.info("Sending dispute raised notification email to: $recipientEmail")

        return webClient.post()
            .uri("/api/email/dispute-raised")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(SendEmailResponse::class.java)
            .doOnSuccess { response ->
                logger.info("Successfully sent dispute raised email to $recipientEmail: ${response.message}")
            }
            .onErrorResume { error ->
                when (error) {
                    is WebClientResponseException -> {
                        logger.error("Email service returned error: ${error.statusCode} - ${error.responseBodyAsString}")
                        Mono.just(SendEmailResponse(
                            success = false,
                            messageId = null,
                            message = "Email service error: ${error.statusCode}"
                        ))
                    }
                    else -> {
                        logger.error("Failed to send dispute raised email", error)
                        Mono.just(SendEmailResponse(
                            success = false,
                            messageId = null,
                            message = "Email service error: ${error.message}"
                        ))
                    }
                }
            }
    }

    fun sendDisputeResolved(
        recipientEmail: String,
        amount: String,
        currency: String,
        buyerEmail: String,
        sellerEmail: String,
        contractDescription: String,
        payoutDateTime: String,
        sellerPercentage: String,
        sellerActualAmount: String,
        buyerPercentage: String,
        buyerActualAmount: String
    ): Mono<SendEmailResponse> {
        if (!enabled) {
            logger.info("Email service integration disabled, skipping dispute resolved notification")
            return Mono.just(SendEmailResponse(
                success = true,
                messageId = null,
                message = "Email service disabled"
            ))
        }

        val request = DisputeResolvedRequest(
            to = recipientEmail,
            amount = amount,
            currency = currency,
            buyerEmail = buyerEmail,
            sellerEmail = sellerEmail,
            description = contractDescription,
            payoutDateTime = payoutDateTime,
            sellerPercentAmount = sellerPercentage,
            sellerActualAmount = sellerActualAmount,
            buyerPercentAmount = buyerPercentage,
            buyerActualAmount = buyerActualAmount
        )

        logger.info("Sending dispute resolved notification email to: $recipientEmail")

        return webClient.post()
            .uri("/api/email/dispute-resolved")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(SendEmailResponse::class.java)
            .doOnSuccess { response ->
                logger.info("Successfully sent dispute resolved email to $recipientEmail: ${response.message}")
            }
            .onErrorResume { error ->
                when (error) {
                    is WebClientResponseException -> {
                        logger.error("Email service returned error: ${error.statusCode} - ${error.responseBodyAsString}")
                        Mono.just(SendEmailResponse(
                            success = false,
                            messageId = null,
                            message = "Email service error: ${error.statusCode}"
                        ))
                    }
                    else -> {
                        logger.error("Failed to send dispute resolved email", error)
                        Mono.just(SendEmailResponse(
                            success = false,
                            messageId = null,
                            message = "Email service error: ${error.message}"
                        ))
                    }
                }
            }
    }
}