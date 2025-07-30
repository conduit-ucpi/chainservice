package com.conduit.chainservice.controller

import com.conduit.chainservice.config.EscrowBlockchainProperties
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant

@RestController
@RequestMapping("/actuator")
@Tag(name = "Health & Monitoring", description = "System health checks and application information")
class HealthController(
    private val web3j: Web3j,
    private val relayerCredentials: Credentials,
    private val blockchainProperties: EscrowBlockchainProperties
) {

    private val logger = LoggerFactory.getLogger(HealthController::class.java)

    @GetMapping("/health")
    @Operation(
        summary = "Health Check",
        description = "Performs comprehensive health checks including blockchain connectivity and relayer wallet status."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Service is healthy",
            content = [Content(schema = Schema(implementation = Map::class))]
        ),
        ApiResponse(
            responseCode = "503",
            description = "Service is unhealthy",
            content = [Content(schema = Schema(implementation = Map::class))]
        )
    ])
    fun health(): ResponseEntity<Map<String, Any>> {
        return try {
            val healthStatus = mutableMapOf<String, Any>()
            
            healthStatus["status"] = "UP"
            healthStatus["timestamp"] = Instant.now().toString()
            
            val blockchainHealth = checkBlockchainConnection()
            healthStatus["blockchain"] = blockchainHealth
            
            val relayerHealth = checkRelayerWallet()
            healthStatus["relayer"] = relayerHealth
            
            val overallHealthy = blockchainHealth["status"] == "UP" && relayerHealth["status"] == "UP"
            
            if (!overallHealthy) {
                healthStatus["status"] = "DOWN"
                return ResponseEntity.status(503).body(healthStatus)
            }
            
            ResponseEntity.ok(healthStatus)

        } catch (e: Exception) {
            logger.error("Health check failed", e)
            val errorResponse = mapOf(
                "status" to "DOWN",
                "timestamp" to Instant.now().toString(),
                "error" to (e.message ?: "Unknown error")
            )
            ResponseEntity.status(503).body(errorResponse)
        }
    }

    @GetMapping("/info")
    @Operation(
        summary = "Application Information",
        description = "Returns application information including version, configuration, and build details."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Application information retrieved successfully",
        content = [Content(schema = Schema(implementation = Map::class))]
    )
    fun info(): ResponseEntity<Map<String, Any>> {
        return try {
            val info = mapOf(
                "application" to mapOf(
                    "name" to "Chain Service",
                    "version" to "1.0.0",
                    "description" to "Conduit UCPI Web3 Transaction Relay Service"
                ),
                "blockchain" to mapOf(
                    "network" to blockchainProperties.rpcUrl,
                    "usdcContract" to blockchainProperties.usdcContractAddress,
                    "factoryContract" to blockchainProperties.contractFactoryAddress
                ),
                "build" to mapOf(
                    "timestamp" to Instant.now().toString()
                )
            )
            
            ResponseEntity.ok(info)

        } catch (e: Exception) {
            logger.error("Info endpoint failed", e)
            ResponseEntity.status(500).body(
                mapOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }

    private fun checkBlockchainConnection(): Map<String, Any> {
        return try {
            runBlocking {
                val clientVersion = web3j.web3ClientVersion().send()
                val latestBlock = web3j.ethBlockNumber().send()
                
                if (clientVersion.hasError() || latestBlock.hasError()) {
                    mapOf(
                        "status" to "DOWN",
                        "error" to (clientVersion.error?.message ?: latestBlock.error?.message ?: "Unknown error")
                    )
                } else {
                    mapOf(
                        "status" to "UP",
                        "clientVersion" to clientVersion.web3ClientVersion,
                        "latestBlock" to latestBlock.blockNumber.toString(),
                        "rpcUrl" to blockchainProperties.rpcUrl
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Blockchain connection check failed", e)
            mapOf(
                "status" to "DOWN",
                "error" to (e.message ?: "Connection failed")
            )
        }
    }

    private fun checkRelayerWallet(): Map<String, Any> {
        return try {
            runBlocking {
                val balance = web3j.ethGetBalance(
                    relayerCredentials.address,
                    DefaultBlockParameterName.LATEST
                ).send()
                
                if (balance.hasError()) {
                    mapOf(
                        "status" to "DOWN",
                        "error" to balance.error.message
                    )
                } else {
                    val balanceInEth = BigDecimal(balance.balance)
                        .divide(BigDecimal.valueOf(1000000000000000000L))
                    
                    val isLowBalance = balanceInEth < BigDecimal("0.01")
                    
                    mutableMapOf<String, Any>(
                        "status" to if (isLowBalance) "WARN" else "UP",
                        "address" to relayerCredentials.address,
                        "balance" to balanceInEth.toString(),
                        "balanceWei" to balance.balance.toString()
                    ).apply {
                        if (isLowBalance) {
                            put("warning", "Low balance - may not be able to relay transactions")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Relayer wallet check failed", e)
            mapOf(
                "status" to "DOWN",
                "error" to (e.message ?: "Wallet check failed")
            )
        }
    }
}