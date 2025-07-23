package com.conduit.chainservice.service

import com.conduit.chainservice.config.BlockchainProperties
import com.conduit.chainservice.model.ContractInfo
import com.conduit.chainservice.model.ContractStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.web3j.abi.EventEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.EthCall
import org.web3j.utils.Convert
import java.math.BigInteger
import java.time.Instant

@Service
class ContractQueryService(
    private val web3j: Web3j,
    private val blockchainProperties: BlockchainProperties,
    private val eventParsingService: EventParsingService
) {

    private val logger = LoggerFactory.getLogger(ContractQueryService::class.java)

    suspend fun getContractsForWallet(walletAddress: String): List<ContractInfo> {
        return try {
            logger.info("Querying contracts for wallet: $walletAddress")

            val contractAddresses = eventParsingService.findContractsByParticipant(walletAddress)
            
            contractAddresses.mapNotNull { contractAddress ->
                getContractInfo(contractAddress, walletAddress)
            }

        } catch (e: Exception) {
            logger.error("Error querying contracts for wallet: $walletAddress", e)
            emptyList()
        }
    }

    suspend fun getContractStatus(contractAddress: String): ContractStatus {
        return try {
            val contractData = queryContractState(contractAddress)
            
            val currentTime = Instant.now().epochSecond
            val expiryTime = contractData["expiryTimestamp"] as Long
            val disputed = contractData["disputed"] as Boolean
            val resolved = contractData["resolved"] as Boolean  
            val claimed = contractData["claimed"] as Boolean

            when {
                claimed -> ContractStatus.CLAIMED
                resolved -> ContractStatus.RESOLVED
                disputed -> ContractStatus.DISPUTED
                currentTime > expiryTime -> ContractStatus.EXPIRED
                else -> ContractStatus.ACTIVE
            }

        } catch (e: Exception) {
            logger.error("Error getting contract status for: $contractAddress", e)
            ContractStatus.EXPIRED
        }
    }

    private suspend fun getContractInfo(contractAddress: String, participantAddress: String): ContractInfo? {
        return try {
            val contractData = queryContractState(contractAddress)
            val eventHistory = eventParsingService.parseContractEvents(contractAddress)
            
            val buyer = contractData["buyer"] as String
            val seller = contractData["seller"] as String
            val amount = contractData["amount"] as BigInteger
            val expiryTimestamp = contractData["expiryTimestamp"] as Long
            val description = contractData["description"] as String
            
            if (buyer.equals(participantAddress, ignoreCase = true) || 
                seller.equals(participantAddress, ignoreCase = true)) {
                
                val status = getContractStatus(contractAddress)
                
                val createdEvent = eventHistory.events.find { it.eventType.name == "CONTRACT_CREATED" }
                val disputedEvent = eventHistory.events.find { it.eventType.name == "DISPUTE_RAISED" }
                val resolvedEvent = eventHistory.events.find { it.eventType.name == "DISPUTE_RESOLVED" }
                val claimedEvent = eventHistory.events.find { it.eventType.name == "FUNDS_CLAIMED" }

                ContractInfo(
                    contractAddress = contractAddress,
                    buyer = buyer,
                    seller = seller,
                    amount = amount,
                    expiryTimestamp = expiryTimestamp,
                    description = description,
                    status = status,
                    createdAt = createdEvent?.timestamp ?: Instant.now(),
                    disputedAt = disputedEvent?.timestamp,
                    resolvedAt = resolvedEvent?.timestamp,
                    claimedAt = claimedEvent?.timestamp
                )
            } else {
                null
            }

        } catch (e: Exception) {
            logger.error("Error getting contract info for: $contractAddress", e)
            null
        }
    }

    private suspend fun queryContractState(contractAddress: String): Map<String, Any> {
        val results = mutableMapOf<String, Any>()

        try {
            results["buyer"] = callContractFunction(contractAddress, "buyer()")
            results["seller"] = callContractFunction(contractAddress, "seller()")
            results["amount"] = callContractFunction(contractAddress, "amount()")
            results["expiryTimestamp"] = callContractFunction(contractAddress, "expiryTimestamp()")
            results["description"] = callContractFunction(contractAddress, "description()")
            results["disputed"] = callContractFunction(contractAddress, "disputed()")
            results["resolved"] = callContractFunction(contractAddress, "resolved()")
            results["claimed"] = callContractFunction(contractAddress, "claimed()")

        } catch (e: Exception) {
            logger.error("Error querying contract state for: $contractAddress", e)
            throw e
        }

        return results
    }

    private suspend fun callContractFunction(contractAddress: String, functionSignature: String): Any {
        return try {
            val functionHash = when (functionSignature) {
                "buyer()" -> "0x7150d8ae"
                "seller()" -> "0x08551a53"
                "amount()" -> "0xaa8c217c"
                "expiryTimestamp()" -> "0x156a4a24"
                "description()" -> "0x7284e416"
                "disputed()" -> "0x1551c99a"
                "resolved()" -> "0x4a72584d"
                "claimed()" -> "0x297c8b5b"
                else -> throw IllegalArgumentException("Unknown function: $functionSignature")
            }

            val ethCall: EthCall = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    null, contractAddress, functionHash
                ),
                DefaultBlockParameterName.LATEST
            ).send()

            val result = ethCall.result
            
            when (functionSignature) {
                "buyer()", "seller()" -> {
                    "0x" + result.substring(26)
                }
                "amount()", "expiryTimestamp()" -> {
                    BigInteger(result.substring(2), 16)
                }
                "description()" -> {
                    val hex = result.substring(130)
                    String(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()).trim('\u0000')
                }
                "disputed()", "resolved()", "claimed()" -> {
                    result.substring(result.length - 1) == "1"
                }
                else -> result
            }

        } catch (e: Exception) {
            logger.error("Error calling contract function $functionSignature on $contractAddress", e)
            when (functionSignature) {
                "buyer()", "seller()" -> "0x0000000000000000000000000000000000000000"
                "amount()", "expiryTimestamp()" -> BigInteger.ZERO
                "description()" -> ""
                "disputed()", "resolved()", "claimed()" -> false
                else -> ""
            }
        }
    }
}