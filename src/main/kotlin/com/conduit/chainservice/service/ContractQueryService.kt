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
            val funded = contractData["funded"] as Boolean
            val disputed = contractData["disputed"] as Boolean
            val resolved = contractData["resolved"] as Boolean  
            val claimed = contractData["claimed"] as Boolean

            when {
                claimed -> ContractStatus.CLAIMED
                resolved -> ContractStatus.RESOLVED
                disputed -> ContractStatus.DISPUTED
                !funded -> ContractStatus.CREATED
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
            val funded = contractData["funded"] as Boolean
            
            if (buyer.equals(participantAddress, ignoreCase = true) || 
                seller.equals(participantAddress, ignoreCase = true)) {
                
                val status = getContractStatus(contractAddress)
                
                val createdEvent = eventHistory.events.find { it.eventType.name == "CONTRACT_CREATED" }
                val fundedEvent = eventHistory.events.find { it.eventType.name == "FUNDS_DEPOSITED" }
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
                    funded = funded,
                    status = status,
                    createdAt = createdEvent?.timestamp ?: Instant.now(),
                    fundedAt = fundedEvent?.timestamp,
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
        return try {
            // Use the getContractInfo function to get all data in one call
            val contractInfo = callGetContractInfo(contractAddress)
            
            mapOf(
                "buyer" to contractInfo["buyer"]!!,
                "seller" to contractInfo["seller"]!!,
                "amount" to contractInfo["amount"]!!,
                "expiryTimestamp" to contractInfo["expiryTimestamp"]!!,
                "description" to contractInfo["description"]!!,
                "funded" to contractInfo["funded"]!!,
                "disputed" to contractInfo["disputed"]!!,
                "resolved" to contractInfo["resolved"]!!,
                "claimed" to contractInfo["claimed"]!!
            )

        } catch (e: Exception) {
            logger.error("Error querying contract state for: $contractAddress", e)
            throw e
        }
    }

    private suspend fun callGetContractInfo(contractAddress: String): Map<String, Any> {
        return try {
            // Call getContractInfo() function - signature: 0x...
            val functionHash = "0xab5b3a82"  // getContractInfo() function selector

            val ethCall: EthCall = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    null, contractAddress, functionHash
                ),
                DefaultBlockParameterName.LATEST
            ).send()

            if (ethCall.hasError()) {
                throw RuntimeException("Contract call failed: ${ethCall.error.message}")
            }

            val result = ethCall.result
            parseGetContractInfoResult(result)

        } catch (e: Exception) {
            logger.error("Error calling getContractInfo on $contractAddress", e)
            // Return default values
            mapOf(
                "buyer" to "0x0000000000000000000000000000000000000000",
                "seller" to "0x0000000000000000000000000000000000000000", 
                "amount" to BigInteger.ZERO,
                "expiryTimestamp" to 0L,
                "description" to "",
                "disputed" to false,
                "resolved" to false,
                "claimed" to false
            )
        }
    }

    private fun parseGetContractInfoResult(result: String): Map<String, Any> {
        return try {
            // Parse the ABI-encoded return data
            // getContractInfo returns: (address, address, uint256, uint256, string, bool, bool, bool, bool, uint256)
            
            val data = result.removePrefix("0x")
            
            // Each slot is 32 bytes (64 hex chars)
            val buyer = "0x" + data.substring(24, 64)
            val seller = "0x" + data.substring(88, 128)
            val amount = BigInteger(data.substring(128, 192), 16)
            val expiryTimestamp = BigInteger(data.substring(192, 256), 16).toLong()
            
            // String offset and length parsing
            val stringOffset = BigInteger(data.substring(256, 320), 16).toInt() * 2
            val stringLength = BigInteger(data.substring(stringOffset, stringOffset + 64), 16).toInt() * 2
            val description = if (stringLength > 0) {
                val hexString = data.substring(stringOffset + 64, stringOffset + 64 + stringLength)
                String(hexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()).trim('\u0000')
            } else ""
            
            val funded = data.substring(383, 384) == "1"
            val disputed = data.substring(447, 448) == "1"
            val resolved = data.substring(511, 512) == "1" 
            val claimed = data.substring(575, 576) == "1"
            
            mapOf(
                "buyer" to buyer,
                "seller" to seller,
                "amount" to amount,
                "expiryTimestamp" to expiryTimestamp,
                "description" to description,
                "funded" to funded,
                "disputed" to disputed,
                "resolved" to resolved,
                "claimed" to claimed
            )
            
        } catch (e: Exception) {
            logger.error("Error parsing getContractInfo result", e)
            mapOf(
                "buyer" to "0x0000000000000000000000000000000000000000",
                "seller" to "0x0000000000000000000000000000000000000000",
                "amount" to BigInteger.ZERO,
                "expiryTimestamp" to 0L,
                "description" to "",
                "funded" to false,
                "disputed" to false,
                "resolved" to false,
                "claimed" to false
            )
        }
    }
}