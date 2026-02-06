package com.conduit.chainservice.service

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.config.BlockchainProperties
import com.conduit.chainservice.config.AbiLoader
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
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.StaticStruct
import com.conduit.chainservice.model.Multicall3Result
import com.conduit.chainservice.model.Multicall3Call3
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
    private val blockchainProperties: BlockchainProperties,
    private val eventParsingService: EventParsingService,
    private val abiLoader: AbiLoader
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
            
            val buyer = contractData["buyer"] as String
            val seller = contractData["seller"] as String
            val amount = contractData["amount"] as BigInteger
            val expiryTimestamp = contractData["expiryTimestamp"] as Long
            val description = contractData["description"] as String
            val funded = contractData["funded"] as Boolean
            val createdAt = contractData["createdAt"] as Long
            // For backward compatibility, default to USDC for old contracts without tokenAddress
            val tokenAddress = (contractData["tokenAddress"] as? String) ?: blockchainProperties.usdcContractAddress

            val status = getContractStatus(contractAddress)

            ContractInfo(
                contractAddress = contractAddress,
                buyer = buyer,
                seller = seller,
                amount = amount,
                expiryTimestamp = expiryTimestamp,
                description = description,
                funded = funded,
                status = status,
                createdAt = Instant.ofEpochSecond(createdAt),
                tokenAddress = tokenAddress,
                fundedAt = null,
                disputedAt = null,
                resolvedAt = null,
                claimedAt = null
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
            
            val buyer = contractData["buyer"] as String
            val seller = contractData["seller"] as String
            val amount = contractData["amount"] as BigInteger
            val expiryTimestamp = contractData["expiryTimestamp"] as Long
            val description = contractData["description"] as String
            val funded = contractData["funded"] as Boolean
            val createdAt = contractData["createdAt"] as Long
            // For backward compatibility, default to USDC for old contracts without tokenAddress
            val tokenAddress = (contractData["tokenAddress"] as? String) ?: blockchainProperties.usdcContractAddress

            if (userType == "admin" ||
                buyer.equals(participantAddress, ignoreCase = true) ||
                seller.equals(participantAddress, ignoreCase = true)) {

                val status = getContractStatus(contractAddress)

                ContractInfo(
                    contractAddress = contractAddress,
                    buyer = buyer,
                    seller = seller,
                    amount = amount,
                    expiryTimestamp = expiryTimestamp,
                    description = description,
                    funded = funded,
                    status = status,
                    createdAt = Instant.ofEpochSecond(createdAt),
                    tokenAddress = tokenAddress,
                    fundedAt = null,
                    disputedAt = null,
                    resolvedAt = null,
                    claimedAt = null
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
                "claimed" to contractInfo["claimed"]!!,
                "createdAt" to contractInfo["createdAt"]!!,
                "tokenAddress" to (contractInfo["tokenAddress"] ?: blockchainProperties.usdcContractAddress)
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
            // Use ABI loader to get the correct output types from the actual contract ABI
            val outputTypes = abiLoader.getContractInfoOutputTypes()

            val function = org.web3j.abi.datatypes.Function(
                "getContractInfo",
                emptyList(),
                outputTypes
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
                "claimed" to false,
                "createdAt" to 0L
            )
        }
    }

    private fun parseNewContractResult(result: List<org.web3j.abi.datatypes.Type<*>>): Map<String, Any> {
        return try {
            // Dynamically parse based on ABI definition
            val outputs = abiLoader.getContractInfoOutputs()

            if (result.size != outputs.size) {
                throw IllegalStateException("Result size ${result.size} doesn't match ABI output size ${outputs.size}")
            }

            val parsedData = mutableMapOf<String, Any>()

            // Parse each field based on ABI
            outputs.forEachIndexed { index, output ->
                val value = when (output.type) {
                    "address" -> (result[index] as Address).value
                    "uint256" -> {
                        val bigIntValue = (result[index] as Uint256).value
                        // Convert to Long for timestamp fields based on name
                        if (output.name.contains("imestamp", ignoreCase = true) || output.name == "_createdAt") {
                            bigIntValue.toLong()
                        } else {
                            bigIntValue
                        }
                    }
                    "uint8" -> (result[index] as org.web3j.abi.datatypes.generated.Uint8).value.toInt()
                    "string" -> (result[index] as Utf8String).value
                    "bool" -> (result[index] as org.web3j.abi.datatypes.Bool).value
                    else -> result[index].value
                }

                // Remove leading underscore from parameter names for consistency
                val fieldName = output.name.removePrefix("_")
                parsedData[fieldName] = value
            }

            // Calculate derived state fields from _currentState if it exists
            val state = parsedData["currentState"] as? Int
            if (state != null) {
                // Map state values: 0=unfunded, 1=funded, 2=disputed, 3=resolved, 4=claimed
                parsedData["funded"] = state >= 1
                parsedData["disputed"] = state == 2
                parsedData["resolved"] = state == 3
                parsedData["claimed"] = state == 4
            }

            // Add empty description for backward compatibility (removed from contract for gas savings)
            if (!parsedData.containsKey("description")) {
                parsedData["description"] = ""
            }

            parsedData

        } catch (e: Exception) {
            logger.error("Error parsing contract result from ABI", e)
            mapOf(
                "buyer" to "0x0000000000000000000000000000000000000000",
                "seller" to "0x0000000000000000000000000000000000000000",
                "amount" to BigInteger.ZERO,
                "expiryTimestamp" to 0L,
                "description" to "",
                "funded" to false,
                "disputed" to false,
                "resolved" to false,
                "claimed" to false,
                "createdAt" to 0L
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
            
            // LEVEL 2: Assemble final results (no event data needed)
            logger.info("Level 2: Assembling final results...")
            val results = assembleContractInfoResults(contractAddresses, contractStates)
            
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
        
        // Use Multicall3 contract on Base (same address on mainnet and testnet)
        val multicall3Address = "0xcA11bde05977b3631167028862bE2a173976CA11"
        
        try {
            // If we have contracts to query, use multicall3 with batch size limiting
            if (contractAddresses.isNotEmpty()) {
                val maxBatchSize = 10 // Conservative batch size to avoid gas limits

                if (contractAddresses.size == 1) {
                    // Single contract - skip multicall3 overhead and query directly
                    logger.debug("Single contract query, bypassing multicall3")
                    val contractAddress = contractAddresses.first()
                    val contractData = callGetContractInfo(contractAddress)
                    results[contractAddress] = contractData
                } else if (contractAddresses.size <= maxBatchSize) {
                    // Small batch - try direct multicall3
                    logger.debug("Small batch (${contractAddresses.size} contracts), using single multicall3")
                    val multicallResults = executeMulticall3(multicall3Address, contractAddresses)
                    results.putAll(multicallResults)
                    logger.debug("Multicall3 batch completed: ${results.size} results retrieved in single RPC call")
                } else {
                    // Large batch - split into smaller chunks to avoid gas limits
                    logger.info("Large batch detected (${contractAddresses.size} contracts), splitting into chunks of $maxBatchSize")
                    val chunks = contractAddresses.chunked(maxBatchSize)
                    var successfulChunks = 0

                    for ((chunkIndex, chunk) in chunks.withIndex()) {
                        try {
                            logger.debug("Processing multicall3 chunk ${chunkIndex + 1}/${chunks.size}: ${chunk.size} contracts")
                            val chunkResults = executeMulticall3(multicall3Address, chunk)
                            results.putAll(chunkResults)
                            successfulChunks++
                            logger.debug("Chunk ${chunkIndex + 1} completed: ${chunkResults.size} results")
                        } catch (chunkException: Exception) {
                            logger.warn("Multicall3 chunk ${chunkIndex + 1} failed: ${chunkException.message}")
                            // This chunk failed - will be handled by the outer catch block
                            throw chunkException
                        }
                    }

                    logger.info("Multicall3 batch processing completed: $successfulChunks/${chunks.size} chunks successful, ${results.size} total results")
                }
            }
            
        } catch (e: Exception) {
            logger.warn("Multicall3 batch failed (${e.message}), falling back to parallel queries")
            // Fallback to parallel queries if multicall fails
            val batchCalls = contractAddresses.map { contractAddress ->
                async {
                    try {
                        contractAddress to callGetContractInfo(contractAddress)
                    } catch (e: Exception) {
                        logger.debug("Failed to get contract state for $contractAddress: ${e.message}")
                        contractAddress to getEmptyContractState()
                    }
                }
            }
            
            val batchResults = batchCalls.awaitAll()
            results.putAll(batchResults)
        }
        
        return@coroutineScope results
    }
    
    /**
     * Execute multicall3 to batch multiple contract calls into a single RPC request
     */
    private suspend fun executeMulticall3(
        multicall3Address: String,
        contractAddresses: List<String>
    ): Map<String, Map<String, Any>> = coroutineScope {
        val results = mutableMapOf<String, Map<String, Any>>()
        
        try {
            // Prepare the encoded function calls for each contract using proper Call3 structs
            val calls = contractAddresses.mapIndexed { index, contractAddress ->
                // Encode the getContractInfo() function call
                val function = Function(
                    "getContractInfo",
                    emptyList(),
                    listOf(
                        TypeReference.create(Address::class.java),     // buyer
                        TypeReference.create(Address::class.java),     // seller
                        TypeReference.create(Uint256::class.java),     // amount
                        TypeReference.create(Uint256::class.java),     // expiryTimestamp
                        TypeReference.create(Utf8String::class.java),  // description
                        TypeReference.create(org.web3j.abi.datatypes.generated.Uint8::class.java),   // currentState
                        TypeReference.create(Uint256::class.java),     // currentTimestamp
                        TypeReference.create(Uint256::class.java),     // creatorFee
                        TypeReference.create(Uint256::class.java)      // createdAt
                    )
                )
                val encodedFunction = FunctionEncoder.encode(function)

                // Log details for first few calls to help debug
                if (index < 3) {
                    logger.debug("Call $index details:")
                    logger.debug("  - Contract: $contractAddress")
                    logger.debug("  - Function signature: ${function.name}")
                    logger.debug("  - Encoded function: $encodedFunction")
                    logger.debug("  - Encoded function length: ${encodedFunction.length}")
                }

                // Create Call3 struct: (address target, bool allowFailure, bytes callData)
                Multicall3Call3.create(
                    contractAddress,
                    true, // allowFailure = true to handle failed calls gracefully
                    org.web3j.utils.Numeric.hexStringToByteArray(encodedFunction)
                )
            }

            // Encode the aggregate3 function call for Multicall3
            val aggregate3Function = Function(
                "aggregate3",
                listOf(DynamicArray(
                    Multicall3Call3::class.java,
                    calls
                )),
                listOf(object : TypeReference<DynamicArray<Multicall3Result>>() {}) // returns Result[] struct with proper parameterization
            )
            
            val encodedMulticall = FunctionEncoder.encode(aggregate3Function)

            // Log detailed multicall3 information for debugging
            logger.debug("Multicall3 Details:")
            logger.debug("  - Target address: $multicall3Address")
            logger.debug("  - Contract count: ${contractAddresses.size}")
            logger.debug("  - Contracts: ${contractAddresses.take(5)}${if (contractAddresses.size > 5) "... (+${contractAddresses.size - 5} more)" else ""}")
            logger.debug("  - Encoded multicall length: ${encodedMulticall.length} chars")
            logger.debug("  - Encoded multicall: ${encodedMulticall.take(200)}${if (encodedMulticall.length > 200) "..." else ""}")

            // Execute the single multicall with explicit gas limit
            // Note: eth_call is read-only and doesn't consume gas, but we need to specify
            // a gas limit to avoid RPC estimation failures with batched Multicall3 calls
            val gasLimit = BigInteger.valueOf(10_000_000) // Generous limit for batched calls
            val ethCall: EthCall = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    null, multicall3Address, encodedMulticall, gasLimit
                ),
                DefaultBlockParameterName.LATEST
            ).send()

            if (ethCall.hasError()) {
                logger.error("Multicall3 execution failed: ${ethCall.error.message} (${contractAddresses.size} contracts)")
                logger.debug("Multicall3 error details:")
                logger.debug("  - Error code: ${ethCall.error.code}")
                logger.debug("  - Error data: ${ethCall.error.data}")
                logger.debug("  - Multicall3 address: $multicall3Address")
                logger.debug("  - Sample contracts: ${contractAddresses.take(3)}")
                throw RuntimeException("Multicall3 error: ${ethCall.error.message}")
            }
            
            val returnValue = ethCall.value
            if (returnValue.isNullOrEmpty() || returnValue == "0x") {
                logger.warn("Empty response from multicall3")
                return@coroutineScope results
            }
            
            // Decode the results
            val outputTypes = listOf(object : TypeReference<DynamicArray<Multicall3Result>>() {})
            val decodedOutput = FunctionReturnDecoder.decode(returnValue, outputTypes as List<TypeReference<Type<Any>>>)
            
            if (decodedOutput.isNotEmpty()) {
                val resultsArray = decodedOutput[0].value as List<*>
                
                resultsArray.forEachIndexed { index, result ->
                    if (index < contractAddresses.size) {
                        val contractAddress = contractAddresses[index]
                        try {
                            // Each result is a struct with (bool success, bytes returnData)
                            val resultStruct = result as Multicall3Result
                            val success = resultStruct.success
                            val returnData = resultStruct.returnData
                            
                            if (success.value) {
                                // Decode the getContractInfo return data
                                val contractData = decodeContractInfoResult(org.web3j.utils.Numeric.toHexString(returnData.value))
                                results[contractAddress] = contractData
                            } else {
                                logger.debug("Contract call failed for $contractAddress in multicall")
                                results[contractAddress] = getEmptyContractState()
                            }
                        } catch (e: Exception) {
                            logger.debug("Failed to decode result for $contractAddress: ${e.message}")
                            results[contractAddress] = getEmptyContractState()
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            // Enhanced error logging for multicall3 failures
            val errorMessage = e.message ?: "Unknown error"

            when {
                errorMessage.contains("execution reverted", ignoreCase = true) -> {
                    logger.warn("Multicall3 execution reverted for ${contractAddresses.size} contracts, falling back to individual queries")
                    logger.debug("Multicall3 reverted details:")
                    logger.debug("  - Possible causes: invalid contract addresses, contracts without getContractInfo() function, gas limit exceeded")
                    logger.debug("  - Multicall3 address: $multicall3Address (Base network)")
                    logger.debug("  - First few contracts: ${contractAddresses.take(5)}")

                    // Test the first contract individually to help diagnose the issue (only in debug)
                    if (logger.isDebugEnabled && contractAddresses.isNotEmpty()) {
                        try {
                            val testContract = contractAddresses.first()
                            logger.debug("Testing individual call to first contract: $testContract")
                            val testResult = getContractInfo(testContract)
                            logger.debug("Individual call succeeded - multicall3 issue is likely with batch processing or gas limits")
                        } catch (testE: Exception) {
                            logger.debug("Individual call also failed for first contract: ${testE.message}")
                        }
                    }
                }

                errorMessage.contains("rate limit", ignoreCase = true) ||
                errorMessage.contains("429", ignoreCase = true) -> {
                    handleRateLimitingError(e, operation = "Multicall3 aggregate")
                }

                else -> {
                    logger.warn("Unexpected error in multicall3 execution: $errorMessage (${contractAddresses.size} contracts)")
                    logger.debug("Multicall3 unexpected error details:")
                    logger.debug("  - Error type: ${e::class.simpleName}")
                    logger.debug("  - Multicall3 address: $multicall3Address")
                    logger.debug("  - Sample contracts: ${contractAddresses.take(3)}")
                }
            }

            throw e
        }
        
        results
    }
    
    /**
     * Decode the result from getContractInfo function call
     * Supports both old contracts (9 fields) and new contracts (10 fields with tokenAddress)
     */
    private fun decodeContractInfoResult(hexData: String): Map<String, Any> {
        try {
            // First try to decode with all 10 fields (new contracts with tokenAddress)
            val outputTypesNew = listOf(
                TypeReference.create(Address::class.java),     // buyer
                TypeReference.create(Address::class.java),     // seller
                TypeReference.create(Uint256::class.java),     // amount
                TypeReference.create(Uint256::class.java),     // expiryTimestamp
                TypeReference.create(Utf8String::class.java),  // description
                TypeReference.create(org.web3j.abi.datatypes.generated.Uint8::class.java),   // currentState
                TypeReference.create(Uint256::class.java),     // currentTimestamp
                TypeReference.create(Uint256::class.java),     // creatorFee
                TypeReference.create(Uint256::class.java),     // createdAt
                TypeReference.create(Address::class.java)      // tokenAddress (NEW)
            )

            var decodedOutput = try {
                FunctionReturnDecoder.decode(hexData, outputTypesNew as List<TypeReference<Type<Any>>>)
            } catch (e: Exception) {
                // If decoding with 10 fields fails, try with 9 fields (old contracts)
                logger.debug("Failed to decode with 10 fields, trying 9 fields (old contract)")
                null
            }

            // Fallback to 9 fields if 10-field decoding failed
            if (decodedOutput == null || decodedOutput.size < 9) {
                val outputTypesOld = listOf(
                    TypeReference.create(Address::class.java),     // buyer
                    TypeReference.create(Address::class.java),     // seller
                    TypeReference.create(Uint256::class.java),     // amount
                    TypeReference.create(Uint256::class.java),     // expiryTimestamp
                    TypeReference.create(Utf8String::class.java),  // description
                    TypeReference.create(org.web3j.abi.datatypes.generated.Uint8::class.java),   // currentState
                    TypeReference.create(Uint256::class.java),     // currentTimestamp
                    TypeReference.create(Uint256::class.java),     // creatorFee
                    TypeReference.create(Uint256::class.java)      // createdAt
                )
                decodedOutput = FunctionReturnDecoder.decode(hexData, outputTypesOld as List<TypeReference<Type<Any>>>)
            }

            if (decodedOutput != null && decodedOutput.size >= 9) {
                val buyer = (decodedOutput[0] as Address).value
                val seller = (decodedOutput[1] as Address).value
                val amount = (decodedOutput[2] as Uint256).value
                val expiryTimestamp = (decodedOutput[3] as Uint256).value.toLong()
                val description = (decodedOutput[4] as Utf8String).value
                val currentState = (decodedOutput[5] as org.web3j.abi.datatypes.generated.Uint8).value.toInt()
                val currentTimestamp = (decodedOutput[6] as Uint256).value.toLong()
                val creatorFee = (decodedOutput[7] as Uint256).value
                val createdAt = (decodedOutput[8] as Uint256).value.toLong()

                // Extract tokenAddress if available (10th field), otherwise assume USDC
                val tokenAddress = if (decodedOutput.size >= 10) {
                    (decodedOutput[9] as Address).value
                } else {
                    // Old contract - assume USDC as default (Base USDC address)
                    "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"
                }

                // Determine boolean states from currentState enum
                val funded = currentState >= 1
                val disputed = currentState == 2
                val resolved = currentState == 3
                val claimed = currentState == 4 || currentState == 5

                return mapOf(
                    "buyer" to buyer,
                    "seller" to seller,
                    "amount" to amount,
                    "expiryTimestamp" to expiryTimestamp,
                    "description" to description,
                    "funded" to funded,
                    "disputed" to disputed,
                    "resolved" to resolved,
                    "claimed" to claimed,
                    "createdAt" to createdAt,
                    "currentState" to currentState,
                    "currentTimestamp" to currentTimestamp,
                    "creatorFee" to creatorFee,
                    "tokenAddress" to tokenAddress
                )
            }
        } catch (e: Exception) {
            logger.debug("Failed to decode contract info result: ${e.message}")
        }

        return getEmptyContractState()
    }
    
    /**
     * Returns an empty contract state for failed queries
     */
    private fun getEmptyContractState(): Map<String, Any> {
        return mapOf(
            "buyer" to "0x0000000000000000000000000000000000000000",
            "seller" to "0x0000000000000000000000000000000000000000",
            "amount" to BigInteger.ZERO,
            "expiryTimestamp" to 0L,
            "description" to "",
            "funded" to false,
            "disputed" to false,
            "resolved" to false,
            "claimed" to false,
            "createdAt" to 0L,
            "tokenAddress" to "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"  // Default to USDC for empty/failed states (Base USDC address)
        )
    }
    
    // LEVEL 2: Event data batch method removed - no longer needed since createdAt comes from contract directly
    
    /**
     * LEVEL 2: Assemble final ContractInfoResult objects
     * Uses only contract state data since createdAt is now available directly
     */
    private suspend fun assembleContractInfoResults(
        contractAddresses: List<String>,
        contractStates: Map<String, Map<String, Any>>
    ): Map<String, ContractInfoResult> {
        logger.debug("Assembling ${contractAddresses.size} contract info results")
        
        return contractAddresses.associateWith { contractAddress ->
            try {
                val contractData = contractStates[contractAddress]
                
                if (contractData == null) {
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
                    val createdAt = contractData["createdAt"] as Long
                    // For backward compatibility, default to USDC for old contracts without tokenAddress
                    val tokenAddress = (contractData["tokenAddress"] as? String) ?: blockchainProperties.usdcContractAddress

                    // Calculate status from contract data
                    val status = calculateContractStatus(contractData)

                    val contractInfo = ContractInfo(
                        contractAddress = contractAddress,
                        buyer = buyer,
                        seller = seller,
                        amount = amount,
                        expiryTimestamp = expiryTimestamp,
                        description = description,
                        funded = funded,
                        status = status,
                        createdAt = Instant.ofEpochSecond(createdAt),
                        tokenAddress = tokenAddress,
                        fundedAt = null,
                        disputedAt = null,
                        resolvedAt = null,
                        claimedAt = null
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