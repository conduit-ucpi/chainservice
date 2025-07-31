package com.conduit.chainservice.service

import com.conduit.chainservice.model.ContractServiceUpdateRequest
import com.conduit.chainservice.model.ContractServiceUpdateResponse
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

@Service
class ContractServiceClient(
    private val webClientBuilder: WebClient.Builder,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(ContractServiceClient::class.java)

    @Value("\${contract-service.url}")
    private lateinit var contractServiceUrl: String

    @Value("\${contract-service.enabled:true}")
    private var enabled: Boolean = true

    private val webClient: WebClient by lazy {
        webClientBuilder
            .baseUrl(contractServiceUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    fun updateContractWithDeployment(
        contractId: String,
        chainAddress: String,
        chainId: String,
        buyerAddress: String,
        request: HttpServletRequest
    ): Mono<ContractServiceUpdateResponse> {
        if (!enabled) {
            logger.info("Contract service integration disabled, skipping update")
            return Mono.just(ContractServiceUpdateResponse(
                id = contractId,
                chainAddress = chainAddress,
                chainId = chainId,
                status = "skipped"
            ))
        }

        // Include status update to "OK" since deployment was successful
        val updateRequestWithStatus = mapOf(
            "chainAddress" to chainAddress,
            "chainId" to chainId,
            "buyerAddress" to buyerAddress,
            "status" to "OK"
        )

        logger.info("Updating contract $contractId with deployment details: chainAddress=$chainAddress, chainId=$chainId")

        return webClient.patch()
            .uri("/api/contracts/$contractId")
            .headers { headers ->
                // Forward authentication headers
                request.getHeader(HttpHeaders.AUTHORIZATION)?.let {
                    headers.set(HttpHeaders.AUTHORIZATION, it)
                }
                
                // Forward cookies including AUTH-TOKEN and session
                request.getHeader(HttpHeaders.COOKIE)?.let {
                    headers.set(HttpHeaders.COOKIE, it)
                }
                
                // Add any custom headers that might be needed
                headers.set("X-Forwarded-For", request.remoteAddr)
                headers.set("X-Original-URI", request.requestURI)
            }
            .bodyValue(updateRequestWithStatus)
            .retrieve()
            .bodyToMono(ContractServiceUpdateResponse::class.java)
            .doOnSuccess { response ->
                logger.info("Successfully updated contract $contractId in contract service")
            }
            .onErrorResume { error ->
                when (error) {
                    is WebClientResponseException -> {
                        logger.error("Contract service returned error: ${error.statusCode} - ${error.responseBodyAsString}")
                        Mono.error(error)
                    }
                    else -> {
                        logger.error("Failed to update contract in contract service", error)
                        Mono.error(error)
                    }
                }
            }
    }

    fun getContract(
        contractId: String,
        request: HttpServletRequest
    ): Mono<Map<String, Any>> {
        if (!enabled) {
            logger.info("Contract service integration disabled, skipping get contract")
            return Mono.error(RuntimeException("Contract service integration disabled"))
        }

        logger.info("Getting contract $contractId from contract service")

        return webClient.get()
            .uri("/api/contracts/$contractId")
            .headers { headers ->
                // Forward authentication headers
                request.getHeader(HttpHeaders.AUTHORIZATION)?.let {
                    headers.set(HttpHeaders.AUTHORIZATION, it)
                }
                
                // Forward cookies including AUTH-TOKEN and session
                request.getHeader(HttpHeaders.COOKIE)?.let {
                    headers.set(HttpHeaders.COOKIE, it)
                }
                
                // Add any custom headers that might be needed
                headers.set("X-Forwarded-For", request.remoteAddr)
                headers.set("X-Original-URI", request.requestURI)
            }
            .retrieve()
            .bodyToMono(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any>>() {})
            .doOnSuccess { response ->
                logger.info("Successfully retrieved contract $contractId from contract service")
            }
            .onErrorResume { error ->
                when (error) {
                    is WebClientResponseException -> {
                        logger.error("Contract service returned error: ${error.statusCode} - ${error.responseBodyAsString}")
                        Mono.error<Map<String, Any>>(error)
                    }
                    else -> {
                        logger.error("Failed to get contract from contract service", error)
                        Mono.error<Map<String, Any>>(error)
                    }
                }
            }
    }

    fun updateContractStatus(
        contractId: String,
        status: String,
        request: HttpServletRequest
    ): Mono<Map<String, Any>> {
        if (!enabled) {
            logger.info("Contract service integration disabled, skipping status update")
            return Mono.just(mapOf("status" to "skipped"))
        }

        val statusUpdate = mapOf("status" to status)

        logger.info("Updating contract $contractId status to $status")

        return webClient.patch()
            .uri("/api/contracts/$contractId")
            .headers { headers ->
                // Forward authentication headers
                request.getHeader(HttpHeaders.AUTHORIZATION)?.let {
                    headers.set(HttpHeaders.AUTHORIZATION, it)
                }
                
                // Forward cookies including AUTH-TOKEN and session
                request.getHeader(HttpHeaders.COOKIE)?.let {
                    headers.set(HttpHeaders.COOKIE, it)
                }
                
                // Add any custom headers that might be needed
                headers.set("X-Forwarded-For", request.remoteAddr)
                headers.set("X-Original-URI", request.requestURI)
            }
            .bodyValue(statusUpdate)
            .retrieve()
            .bodyToMono(object : org.springframework.core.ParameterizedTypeReference<Map<String, Any>>() {})
            .doOnSuccess { response ->
                logger.info("Successfully updated contract $contractId status to $status")
            }
            .onErrorResume { error ->
                when (error) {
                    is WebClientResponseException -> {
                        logger.error("Contract service returned error: ${error.statusCode} - ${error.responseBodyAsString}")
                        Mono.error<Map<String, Any>>(error)
                    }
                    else -> {
                        logger.error("Failed to update contract status in contract service", error)
                        Mono.error<Map<String, Any>>(error)
                    }
                }
            }
    }
}