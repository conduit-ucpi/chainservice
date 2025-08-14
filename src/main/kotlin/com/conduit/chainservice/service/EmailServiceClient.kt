package com.conduit.chainservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
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
    val currency: String,
    val payoutDateTime: String
)

data class DisputeRaisedRequest(
    val to: String,
    val buyerEmail: String,
    val link: String,
    val amount: String,
    val currency: String,
    val description: String,
    val sellerEmail: String,
    val payoutDateTime: String,
    val productName: String,
    val reason: String? = null,
    val suggestedSplit: Int? = null
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
    val buyerActualAmount: String,
    val productName: String,
    val link: String
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
        currency: String,
        payoutDateTime: String,
        contractLink: String,
        httpRequest: HttpServletRequest
    ): Mono<SendEmailResponse> {
        if (!enabled) {
            logger.info("Email service integration disabled, skipping payment notification")
            return Mono.just(SendEmailResponse(
                success = true,
                messageId = null,
                message = "Email service disabled"
            ))
        }

        val emailRequest = PaymentNotificationRequest(
            to = sellerEmail,
            buyerEmail = buyerEmail,
            link = contractLink,
            description = contractDescription,
            amount = amount,
            currency = currency,
            payoutDateTime = payoutDateTime
        )

        logger.info("Sending payment notification email to seller: $sellerEmail")

        return webClient.post()
            .uri("/api/email/payment-notification")
            .headers { headers ->
                // Forward authentication headers
                httpRequest.getHeader(HttpHeaders.AUTHORIZATION)?.let {
                    headers.set(HttpHeaders.AUTHORIZATION, it)
                }
                
                // Forward cookies including AUTH-TOKEN and session
                httpRequest.getHeader(HttpHeaders.COOKIE)?.let {
                    headers.set(HttpHeaders.COOKIE, it)
                }
                
                // Add any custom headers that might be needed
                headers.set("X-Forwarded-For", httpRequest.remoteAddr)
                headers.set("X-Original-URI", httpRequest.requestURI)
            }
            .bodyValue(emailRequest)
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
        productName: String,
        link: String,
        reason: String? = null,
        suggestedSplit: Int? = null,
        httpRequest: HttpServletRequest
    ): Mono<SendEmailResponse> {
        if (!enabled) {
            logger.info("Email service integration disabled, skipping dispute raised notification")
            return Mono.just(SendEmailResponse(
                success = true,
                messageId = null,
                message = "Email service disabled"
            ))
        }

        val emailRequest = DisputeRaisedRequest(
            to = recipientEmail,
            buyerEmail = buyerEmail,
            link = link,
            amount = amount,
            currency = currency,
            description = contractDescription,
            sellerEmail = sellerEmail,
            payoutDateTime = payoutDateTime,
            productName = productName,
            reason = reason,
            suggestedSplit = suggestedSplit
        )

        logger.info("Sending dispute raised notification email to: $recipientEmail")

        return webClient.post()
            .uri("/api/email/dispute-raised")
            .headers { headers ->
                // Forward authentication headers
                httpRequest.getHeader(HttpHeaders.AUTHORIZATION)?.let {
                    headers.set(HttpHeaders.AUTHORIZATION, it)
                }
                
                // Forward cookies including AUTH-TOKEN and session
                httpRequest.getHeader(HttpHeaders.COOKIE)?.let {
                    headers.set(HttpHeaders.COOKIE, it)
                }
                
                // Add any custom headers that might be needed
                headers.set("X-Forwarded-For", httpRequest.remoteAddr)
                headers.set("X-Original-URI", httpRequest.requestURI)
            }
            .bodyValue(emailRequest)
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
        buyerActualAmount: String,
        productName: String,
        link: String,
        httpRequest: HttpServletRequest
    ): Mono<SendEmailResponse> {
        if (!enabled) {
            logger.info("Email service integration disabled, skipping dispute resolved notification")
            return Mono.just(SendEmailResponse(
                success = true,
                messageId = null,
                message = "Email service disabled"
            ))
        }

        val emailRequest = DisputeResolvedRequest(
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
            buyerActualAmount = buyerActualAmount,
            productName = productName,
            link = link
        )

        logger.info("Sending dispute resolved notification email to: $recipientEmail")

        return webClient.post()
            .uri("/api/email/dispute-resolved")
            .headers { headers ->
                // Forward authentication headers
                httpRequest.getHeader(HttpHeaders.AUTHORIZATION)?.let {
                    headers.set(HttpHeaders.AUTHORIZATION, it)
                }
                
                // Forward cookies including AUTH-TOKEN and session
                httpRequest.getHeader(HttpHeaders.COOKIE)?.let {
                    headers.set(HttpHeaders.COOKIE, it)
                }
                
                // Add any custom headers that might be needed
                headers.set("X-Forwarded-For", httpRequest.remoteAddr)
                headers.set("X-Original-URI", httpRequest.requestURI)
            }
            .bodyValue(emailRequest)
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