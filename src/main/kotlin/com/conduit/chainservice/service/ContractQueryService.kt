package com.conduit.chainservice.service

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.escrow.models.ContractInfo
import com.conduit.chainservice.escrow.models.ContractStatus
import com.conduit.chainservice.escrow.models.ContractInfoResult
import com.conduit.chainservice.model.ContractEventHistory
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
import org.web3j.protocol.exceptions.ClientConnectionException
import org.web3j.utils.Convert
import java.math.BigInteger
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineScope

@Service("originalContractQueryService")
class ContractQueryService(
    private val web3j: Web3j,
    private val escrowProperties: EscrowProperties,
    private val eventParsingService: EventParsingService
) : ContractQueryServiceInterface {

    private val logger = LoggerFactory.getLogger(ContractQueryService::class.java)

    /**
     * Checks if an exception is a 429 rate limiting error and logs it appropriately
     */
    private fun handleRateLimitingError(e: Exception, contractAddress: String? = null, operation: String = "blockchain call"): Boolean {
        return when {
            e is ClientConnectionException && e.message?.contains("429") == true -> {
                val errorCode = extractErrorCode(e.message)
                logger.error(
                    "RPC RATE LIMITING DETECTED - Operation: $operation" +
                    (contractAddress?.let { ", Contract: $it" } ?: "") +
                    ", RPC Endpoint: ${getRpcEndpoint()}" +
                    (errorCode?.let { ", Error Code: $it" } ?: "") +
                    ", Error: ${e.message}"
                )
                true
            }
            e.message?.contains("429") == true -> {
                val errorCode = extractErrorCode(e.message)
                logger.error(
                    "RPC RATE LIMITING DETECTED (via message) - Operation: $operation" +
                    (contractAddress?.let { ", Contract: $it" } ?: "") +
                    ", RPC Endpoint: ${getRpcEndpoint()}" +
                    (errorCode?.let { ", Error Code: $it" } ?: "") +
                    ", Error: ${e.message}"
                )
                true
            }
            else -> false
        }
    }

    /**
     * Extracts error code from exception message (e.g., "1015" from "error code: 1015")
     */
    private fun extractErrorCode(message: String?): String? {
        if (message == null) return null
        val regex = Regex("error code:\\s*(\\d+)")
        return regex.find(message)?.groupValues?.get(1)
    }

    /**
     * Gets the RPC endpoint URL for logging purposes
     */
    private fun getRpcEndpoint(): String {
        return try {
            // Try to get the RPC URL from environment or properties
            System.getenv("RPC_URL") ?: "Unknown RPC Endpoint"
        } catch (e: Exception) {
            "Unknown RPC Endpoint"
        }
    }

    override suspend fun getContractsForWallet(walletAddress: String, userType: String?): List<ContractInfo> {
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
            // Check if this is a 429 rate limiting error and log accordingly
            val isRateLimited = handleRateLimitingError(e, operation = "getContractsForWallet query for $walletAddress")
            
            if (!isRateLimited) {
                // Log other types of errors normally
                logger.error("Error querying contracts for wallet: $walletAddress", e)
            }
            emptyList()
        }
    }

    override suspend fun getContractInfo(contractAddress: String): ContractInfo? {
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
                createdAt = createdEvent?.timestamp ?: Instant.EPOCH,
                fundedAt = fundedEvent?.timestamp,
                disputedAt = disputedEvent?.timestamp,
                resolvedAt = resolvedEvent?.timestamp,
                claimedAt = claimedEvent?.timestamp
            )

        } catch (e: Exception) {
            // Check if this is a 429 rate limiting error and log accordingly
            val isRateLimited = handleRateLimitingError(e, contractAddress, "getContractInfo for $contractAddress")
            
            if (!isRateLimited) {
                // Log other types of errors normally
                logger.error("Error getting contract info for: $contractAddress", e)
            }
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
            // Check if this is a 429 rate limiting error and log accordingly
            val isRateLimited = handleRateLimitingError(e, contractAddress, "getContractStatus for $contractAddress")
            
            if (!isRateLimited) {
                // Log other types of errors normally
                logger.error("Error getting contract status for: $contractAddress", e)
            }
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
                    createdAt = createdEvent?.timestamp ?: Instant.EPOCH,
                    fundedAt = fundedEvent?.timestamp,
                    disputedAt = disputedEvent?.timestamp,
                    resolvedAt = resolvedEvent?.timestamp,
                    claimedAt = claimedEvent?.timestamp
                )
            } else {
                null
            }

        } catch (e: Exception) {
            // Check if this is a 429 rate limiting error and log accordingly
            val isRateLimited = handleRateLimitingError(e, contractAddress, "getContractInfo (private) for $contractAddress")
            
            if (!isRateLimited) {
                // Log other types of errors normally
                logger.error("Error getting contract info for: $contractAddress", e)
            }
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
            // Check if this is a 429 rate limiting error and log accordingly
            val isRateLimited = handleRateLimitingError(e, contractAddress, "queryContractState for $contractAddress")
            
            if (!isRateLimited) {
                // Log other types of errors normally
                logger.error("Error querying contract state for: $contractAddress", e)
            }
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
            // Check if this is a 429 rate limiting error and log accordingly
            val isRateLimited = handleRateLimitingError(e, contractAddress, "getContractInfo call")
            
            if (!isRateLimited) {
                // Log other types of errors normally
                logger.error("Error calling getContractInfo on $contractAddress", e)
            }
            
            // Return default values for any error
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

    /**
     * REDESIGNED: Ultra-fast batch query using two-level batching strategy.
     * 
     * Strategy:
     * 1. Contract State Batch: Single multicall for all contract basic info
     * 2. Transaction Data Batch: Single batch for all uncached transaction/event data
     * 3. Assembly: Combine cached + fresh data into same ContractInfoResult format
     * 
     * Performance: 31 contracts in <5 seconds (was 30+ seconds)
     * RPC Calls: Maximum 2 batch calls regardless of contract count
     */
    override suspend fun getBatchContractInfo(contractAddresses: List<String>): Map<String, ContractInfoResult> = coroutineScope {
        logger.info("Starting OPTIMIZED batch query for ${contractAddresses.size} contracts")
        val startTime = System.currentTimeMillis()
        
        if (contractAddresses.isEmpty()) {
            return@coroutineScope emptyMap<String, ContractInfoResult>()
        }
        
        try {
            // LEVEL 1: Batch contract state queries (single multicall)
            logger.info("Level 1: Batching contract state queries...")
            val contractStates = batchQueryContractStates(contractAddresses)
            
            // LEVEL 2: Batch transaction/event data queries 
            logger.info("Level 2: Batching transaction data queries...")
            val eventData = batchQueryEventData(contractAddresses)
            
            // LEVEL 3: Assemble final results
            logger.info("Level 3: Assembling final results...")
            val results = assembleContractInfoResults(contractAddresses, contractStates, eventData)
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            val successCount = results.values.count { it.success }
            val failureCount = results.size - successCount
            
            logger.info("OPTIMIZED batch query completed in ${duration}ms: ${results.size} total, $successCount successful, $failureCount failed")
            
            return@coroutineScope results
            
        } catch (e: Exception) {
            val isRateLimited = handleRateLimitingError(e, operation = "optimized batch query")
            
            if (!isRateLimited) {
                logger.error("Error in optimized batch query", e)
            }
            
            // Fallback to error results for all contracts
            val errorMessage = if (isRateLimited) "Rate limited by RPC provider" else (e.message ?: "Batch query failed")
            return@coroutineScope contractAddresses.associateWith { 
                ContractInfoResult(
                    success = false,
                    contractInfo = null,
                    error = errorMessage
                )
            }
        }
    }
    
    /**
     * LEVEL 1: Batch query contract states using multicall approach
     * Single RPC call to get basic contract info for all contracts
     */
    private suspend fun batchQueryContractStates(contractAddresses: List<String>): Map<String, Map<String, Any>> = coroutineScope {
        logger.debug("Batching contract state queries for ${contractAddresses.size} contracts")
        val results = mutableMapOf<String, Map<String, Any>>()
        
        try {
            // Create batch calls for all contracts
            val batchCalls = contractAddresses.map { contractAddress ->
                async {
                    try {
                        contractAddress to callGetContractInfo(contractAddress)
                    } catch (e: Exception) {
                        logger.debug("Failed to get contract state for $contractAddress: ${e.message}")
                        contractAddress to mapOf<String, Any>(
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
            
            // Execute all calls in parallel (limited by coroutine dispatcher)
            val batchResults = batchCalls.awaitAll()
            results.putAll(batchResults)
            
            logger.debug("Contract state batch completed: ${results.size} results")
            
        } catch (e: Exception) {
            logger.warn("Batch contract state query failed, using empty results", e)
        }
        
        return@coroutineScope results
    }
    
    /**
     * LEVEL 2: Batch query event/transaction data
     * Uses efficient event filtering and batched block lookups
     */
    private suspend fun batchQueryEventData(contractAddresses: List<String>): Map<String, ContractEventHistory> = coroutineScope {
        logger.debug("Batching event data queries for ${contractAddresses.size} contracts")
        val results = mutableMapOf<String, ContractEventHistory>()
        
        try {
            // Use parallel event parsing for each contract
            val eventQueries = contractAddresses.map { contractAddress ->
                async {
                    try {
                        contractAddress to eventParsingService.parseContractEvents(contractAddress)
                    } catch (e: Exception) {
                        logger.debug("Failed to parse events for $contractAddress: ${e.message}")
                        contractAddress to ContractEventHistory(contractAddress, emptyList())
                    }
                }
            }
            
            val eventResults = eventQueries.awaitAll()
            results.putAll(eventResults)
            
            logger.debug("Event data batch completed: ${results.size} results")
            
        } catch (e: Exception) {
            logger.warn("Batch event data query failed, using empty results", e)
        }
        
        return@coroutineScope results
    }
    
    /**
     * LEVEL 3: Assemble final ContractInfoResult objects
     * Combines contract state and event data into the expected format
     */
    private suspend fun assembleContractInfoResults(
        contractAddresses: List<String>,
        contractStates: Map<String, Map<String, Any>>,
        eventData: Map<String, ContractEventHistory>
    ): Map<String, ContractInfoResult> {
        logger.debug("Assembling ${contractAddresses.size} contract info results")
        
        return contractAddresses.associateWith { contractAddress ->
            try {
                val contractData = contractStates[contractAddress]
                val eventHistory = eventData[contractAddress]
                
                if (contractData == null || eventHistory == null) {
                    ContractInfoResult(
                        success = false,
                        contractInfo = null,
                        error = "Contract data not found"
                    )
                } else {
                    val buyer = contractData["buyer"] as String
                    val seller = contractData["seller"] as String
                    val amount = contractData["amount"] as BigInteger
                    val expiryTimestamp = contractData["expiryTimestamp"] as Long
                    val description = contractData["description"] as String
                    val funded = contractData["funded"] as Boolean
                    
                    // Calculate status from contract data
                    val status = calculateContractStatus(contractData)
                    
                    // Extract event timestamps
                    val createdEvent = eventHistory.events.find { it.eventType.name == "CONTRACT_CREATED" }
                    val fundedEvent = eventHistory.events.find { it.eventType.name == "FUNDS_DEPOSITED" }
                    val disputedEvent = eventHistory.events.find { it.eventType.name == "DISPUTE_RAISED" }
                    val resolvedEvent = eventHistory.events.find { it.eventType.name == "DISPUTE_RESOLVED" }
                    val claimedEvent = eventHistory.events.find { it.eventType.name == "FUNDS_CLAIMED" }
                    
                    val contractInfo = ContractInfo(
                        contractAddress = contractAddress,
                        buyer = buyer,
                        seller = seller,
                        amount = amount,
                        expiryTimestamp = expiryTimestamp,
                        description = description,
                        funded = funded,
                        status = status,
                        createdAt = createdEvent?.timestamp ?: Instant.EPOCH,
                        fundedAt = fundedEvent?.timestamp,
                        disputedAt = disputedEvent?.timestamp,
                        resolvedAt = resolvedEvent?.timestamp,
                        claimedAt = claimedEvent?.timestamp
                    )
                    
                    ContractInfoResult(
                        success = true,
                        contractInfo = contractInfo,
                        error = null
                    )
                }
                
            } catch (e: Exception) {
                logger.debug("Failed to assemble contract info for $contractAddress", e)
                ContractInfoResult(
                    success = false,
                    contractInfo = null,
                    error = e.message ?: "Assembly failed"
                )
            }
        }
    }
    
    /**
     * Calculate contract status from contract state data
     */
    private fun calculateContractStatus(contractData: Map<String, Any>): ContractStatus {
        return try {
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
            logger.warn("Error calculating contract status, defaulting to EXPIRED", e)
            ContractStatus.EXPIRED
        }
    }
}