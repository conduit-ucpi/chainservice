package com.conduit.chainservice.service

import com.conduit.chainservice.model.SignedTransactionRequest
import com.conduit.chainservice.model.SignedTransactionResponse
import com.conduit.chainservice.model.TransactionResult
import com.conduit.chainservice.escrow.models.FundWalletRequest
import com.conduit.chainservice.escrow.models.FundWalletResponse
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.math.BigInteger

@Service
class GasPayerServiceClient(
    private val webClientBuilder: WebClient.Builder,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(GasPayerServiceClient::class.java)

    @Value("\${gas-payer.service-url}")
    private lateinit var gasPayerServiceUrl: String

    @Value("\${gas-payer.api-key:}")
    private lateinit var apiKey: String

    private val webClient: WebClient by lazy {
        logger.info("Creating WebClient for gas-payer-service at: $gasPayerServiceUrl")
        webClientBuilder
            .baseUrl(gasPayerServiceUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    suspend fun processTransactionWithGasTransfer(
        userWalletAddress: String,
        signedTransactionHex: String,
        operationType: String,
        gasLimit: BigInteger
    ): TransactionResult {
        if (apiKey.isEmpty()) {
            logger.error("GAS_PAYER_API_KEY is not configured - requests will fail")
            return TransactionResult(
                success = false,
                transactionHash = null,
                contractAddress = null,
                error = "Gas payer API key not configured"
            )
        }
        
        return try {
            logger.info("Processing transaction with gas transfer via gas-payer-service: operation=$operationType, user=$userWalletAddress")
            logger.debug("Gas limit parameter ignored (gas-payer-service calculates automatically): $gasLimit")
            
            val request = SignedTransactionRequest(
                userWalletAddress = userWalletAddress,
                signedTransactionHex = signedTransactionHex
            )
            
            val response = webClient.post()
                .uri("/api/v1/signed-transaction")
                .header("X-API-KEY", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SignedTransactionResponse::class.java)
                .awaitSingle()
            
            if (response.success) {
                logger.info("Transaction processed successfully: ${response.transactionHash}")
            } else {
                logger.error("Transaction failed: ${response.error}")
            }
            
            // Convert to TransactionResult format
            TransactionResult(
                success = response.success,
                transactionHash = response.transactionHash,
                contractAddress = response.contractAddress,
                error = response.error
            )
        } catch (e: WebClientResponseException) {
            logger.error("Gas payer service returned error: ${e.statusCode} - ${e.responseBodyAsString}", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                contractAddress = null,
                error = "Gas payer service error: ${e.message}"
            )
        } catch (e: Exception) {
            logger.error("Failed to call gas payer service", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                contractAddress = null,
                error = "Failed to process transaction: ${e.message}"
            )
        }
    }

    suspend fun relayTransaction(signedTransactionHex: String): TransactionResult {
        logger.warn("relayTransaction called without userWalletAddress - this may not work with gas-payer-service")
        return TransactionResult(
            success = false,
            transactionHash = null,
            contractAddress = null,
            error = "relayTransaction requires userWalletAddress - use processTransactionWithGasTransfer instead"
        )
    }

    suspend fun processSignedTransaction(
        userWalletAddress: String,
        signedTransactionHex: String
    ): SignedTransactionResponse {
        if (apiKey.isEmpty()) {
            logger.error("GAS_PAYER_API_KEY is not configured - requests will fail")
            return SignedTransactionResponse(
                success = false,
                transactionHash = null,
                contractAddress = null,
                error = "Gas payer API key not configured"
            )
        }
        
        return try {
            logger.info("Sending signed transaction to gas-payer-service for wallet: $userWalletAddress")
            
            val request = SignedTransactionRequest(
                userWalletAddress = userWalletAddress,
                signedTransactionHex = signedTransactionHex
            )
            
            val response = webClient.post()
                .uri("/api/v1/signed-transaction")
                .header("X-API-KEY", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SignedTransactionResponse::class.java)
                .awaitSingle()
            
            if (response.success) {
                logger.info("Transaction processed successfully: ${response.transactionHash}")
            } else {
                logger.error("Transaction failed: ${response.error}")
            }
            
            response
        } catch (e: WebClientResponseException) {
            logger.error("Gas payer service returned error: ${e.statusCode} - ${e.responseBodyAsString}", e)
            SignedTransactionResponse(
                success = false,
                transactionHash = null,
                contractAddress = null,
                error = "Gas payer service error: ${e.message}"
            )
        } catch (e: Exception) {
            logger.error("Failed to call gas payer service", e)
            SignedTransactionResponse(
                success = false,
                transactionHash = null,
                contractAddress = null,
                error = "Failed to process transaction: ${e.message}"
            )
        }
    }

    suspend fun fundWallet(
        walletAddress: String,
        totalAmountNeededWei: BigInteger
    ): FundWalletResponse {
        if (apiKey.isEmpty()) {
            logger.error("GAS_PAYER_API_KEY is not configured - requests will fail")
            return FundWalletResponse(
                success = false,
                message = "Gas payer API key not configured",
                error = "Gas payer API key not configured"
            )
        }
        
        return try {
            logger.info("Requesting wallet funding from gas-payer-service: wallet=$walletAddress, amount=$totalAmountNeededWei wei")
            
            val request = FundWalletRequest(
                walletAddress = walletAddress,
                totalAmountNeededWei = totalAmountNeededWei
            )
            
            val response = webClient.post()
                .uri("/api/v1/fund-wallet")
                .header("X-API-KEY", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FundWalletResponse::class.java)
                .awaitSingle()
            
            if (response.success) {
                logger.info("Wallet funding request successful: ${response.message}")
            } else {
                logger.error("Wallet funding request failed: ${response.error}")
            }
            
            response
        } catch (e: WebClientResponseException) {
            logger.error("Gas payer service returned error for fund-wallet: ${e.statusCode} - ${e.responseBodyAsString}", e)
            FundWalletResponse(
                success = false,
                message = "Gas payer service error",
                error = "Gas payer service error: ${e.message}"
            )
        } catch (e: Exception) {
            logger.error("Failed to call gas payer service fund-wallet endpoint", e)
            FundWalletResponse(
                success = false,
                message = "Failed to process wallet funding request",
                error = "Failed to process wallet funding request: ${e.message}"
            )
        }
    }
}