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
import org.web3j.abi.datatypes.Utf8String
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

    suspend fun getContractsForWallet(walletAddress: String, userType: String? = null): List<ContractInfo> {
        return try {
            logger.info("Querying contracts for wallet: $walletAddress, userType: $userType")

            val contractAddresses = if (userType == "admin") {
                // Admin users can see all contracts
                eventParsingService.findAllContracts()
            } else {
                // Regular users can only see contracts where they are buyer or seller
                eventParsingService.findContractsByParticipant(walletAddress)
            }
            
            contractAddresses.mapNotNull { contractAddress ->
                getContractInfo(contractAddress, walletAddress, userType)
            }

        } catch (e: Exception) {
            logger.error("Error querying contracts for wallet: $walletAddress", e)
            emptyList()
        }
    }

    suspend fun getContractInfo(contractAddress: String): ContractInfo? {
        return try {
            val contractData = queryContractState(contractAddress)
            val eventHistory = eventParsingService.parseContractEvents(contractAddress)
            
            val buyer = contractData["buyer"] as String
            val seller = contractData["seller"] as String
            val amount = contractData["amount"] as BigInteger
            val expiryTimestamp = contractData["expiryTimestamp"] as Long
            val description = contractData["description"] as String
            val funded = contractData["funded"] as Boolean
            
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

        } catch (e: Exception) {
            logger.error("Error getting contract info for: $contractAddress", e)
            null
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

    private suspend fun getContractInfo(contractAddress: String, participantAddress: String, userType: String? = null): ContractInfo? {
        return try {
            val contractData = queryContractState(contractAddress)
            val eventHistory = eventParsingService.parseContractEvents(contractAddress)
            
            val buyer = contractData["buyer"] as String
            val seller = contractData["seller"] as String
            val amount = contractData["amount"] as BigInteger
            val expiryTimestamp = contractData["expiryTimestamp"] as Long
            val description = contractData["description"] as String
            val funded = contractData["funded"] as Boolean
            
            if (userType == "admin" || 
                buyer.equals(participantAddress, ignoreCase = true) || 
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
            // Use Web3j to properly encode the function call
            val function = org.web3j.abi.datatypes.Function(
                "getContractInfo",
                emptyList(),
                listOf(
                    org.web3j.abi.TypeReference.create(Address::class.java),     // buyer
                    org.web3j.abi.TypeReference.create(Address::class.java),     // seller
                    org.web3j.abi.TypeReference.create(Uint256::class.java),     // amount
                    org.web3j.abi.TypeReference.create(Uint256::class.java),     // expiryTimestamp
                    org.web3j.abi.TypeReference.create(Utf8String::class.java),  // description
                    org.web3j.abi.TypeReference.create(org.web3j.abi.datatypes.generated.Uint8::class.java),   // currentState
                    org.web3j.abi.TypeReference.create(Uint256::class.java)      // currentTimestamp
                )
            )
            
            val encodedFunction = org.web3j.abi.FunctionEncoder.encode(function)

            val ethCall: EthCall = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    null, contractAddress, encodedFunction
                ),
                DefaultBlockParameterName.LATEST
            ).send()

            if (ethCall.hasError()) {
                throw RuntimeException("Contract call failed: ${ethCall.error.message}")
            }

            val result = org.web3j.abi.FunctionReturnDecoder.decode(ethCall.result, function.outputParameters)
            parseNewContractResult(result)

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

    private fun parseNewContractResult(result: List<org.web3j.abi.datatypes.Type<*>>): Map<String, Any> {
        return try {
            // Parse the new contract result
            // getContractInfo returns: (address _buyer, address _seller, uint256 _amount, uint256 _expiryTimestamp, string _description, uint8 _currentState, uint256 _currentTimestamp)
            
            val buyer = (result[0] as Address).value
            val seller = (result[1] as Address).value
            val amount = (result[2] as Uint256).value
            val expiryTimestamp = (result[3] as Uint256).value.toLong()
            val description = (result[4] as Utf8String).value
            val state = (result[5] as org.web3j.abi.datatypes.generated.Uint8).value.toInt()
            val currentTimestamp = (result[6] as Uint256).value.toLong()
            
            // Map state values: 0=unfunded, 1=funded, 2=disputed, 3=resolved, 4=claimed
            val funded = state >= 1
            val disputed = state == 2
            val resolved = state == 3
            val claimed = state == 4
            
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
            logger.error("Error parsing new contract result", e)
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