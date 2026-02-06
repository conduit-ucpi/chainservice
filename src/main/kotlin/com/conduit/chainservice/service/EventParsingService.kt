package com.conduit.chainservice.service

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.model.ContractEvent
import com.conduit.chainservice.model.ContractEventHistory
import com.conduit.chainservice.model.EventType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.web3j.abi.EventEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.Log
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.net.SocketTimeoutException
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Service
class EventParsingService(
    private val web3j: Web3j,
    private val escrowProperties: EscrowProperties,
    private val circuitBreaker: RpcCircuitBreaker
) {

    private val logger = LoggerFactory.getLogger(EventParsingService::class.java)
    
    // Cache for block timestamps to avoid repeated queries
    private val blockTimestampCache = mutableMapOf<BigInteger, Long>()

    private suspend fun <T> retryWithBackoff(
        operation: String,
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        action: suspend () -> T
    ): T? {
        // Check circuit breaker before attempting
        val circuitStatus = circuitBreaker.getCircuitStatus()
        if (circuitStatus[operation]?.contains("OPEN") == true) {
            logger.warn("Circuit breaker is OPEN for operation: $operation, skipping request")
            return null
        }
        
        var attempt = 0
        var lastException: Exception? = null
        
        while (attempt < maxRetries) {
            try {
                val result = action()
                // Reset any failure tracking on success
                return result
            } catch (e: SocketTimeoutException) {
                attempt++
                lastException = e
                
                // Track failure in circuit breaker
                try {
                    circuitBreaker.executeWithCircuitBreaker(operation) {
                        throw e
                    }
                } catch (_: Exception) {
                    // Expected - we're just tracking the failure
                }
                
                if (attempt < maxRetries) {
                    val delayMs = initialDelayMs * (1L shl (attempt - 1)) // Exponential backoff
                    logger.warn("$operation failed with timeout on attempt $attempt/$maxRetries, retrying in ${delayMs}ms")
                    delay(delayMs)
                } else {
                    logger.error("$operation failed after $maxRetries attempts with timeout", e)
                }
            } catch (e: Exception) {
                // Track non-timeout failures too
                try {
                    circuitBreaker.executeWithCircuitBreaker(operation) {
                        throw e
                    }
                } catch (_: Exception) {
                    // Expected - we're just tracking the failure
                }
                
                logger.error("$operation failed with non-timeout exception", e)
                throw e
            }
        }
        
        return null
    }

    private val contractCreatedEvent = Event(
        "ContractCreated",
        listOf(
            TypeReference.create(Address::class.java, true),  // indexed contractAddress
            TypeReference.create(Address::class.java, true),  // indexed buyer
            TypeReference.create(Address::class.java, true),  // indexed seller
            TypeReference.create(Uint256::class.java, false), // amount
            TypeReference.create(Uint256::class.java, false), // expiryTimestamp
            TypeReference.create(Utf8String::class.java, false) // description
        )
    )

    private val disputeRaisedEvent = Event(
        "DisputeRaised",
        listOf(
            TypeReference.create(Uint256::class.java, false)  // timestamp
        )
    )

    private val disputeResolvedEvent = Event(
        "DisputeResolved", 
        listOf(
            TypeReference.create(Address::class.java, false), // recipient
            TypeReference.create(Uint256::class.java, false)  // timestamp
        )
    )

    private val fundsDepositedEvent = Event(
        "FundsDeposited",
        listOf(
            TypeReference.create(Address::class.java, false), // buyer
            TypeReference.create(Uint256::class.java, false), // amount
            TypeReference.create(Uint256::class.java, false)  // timestamp
        )
    )

    private val fundsClaimedEvent = Event(
        "FundsClaimed",
        listOf(
            TypeReference.create(Address::class.java, false), // recipient
            TypeReference.create(Uint256::class.java, false), // amount
            TypeReference.create(Uint256::class.java, false)  // timestamp
        )
    )

    suspend fun parseContractEvents(contractAddress: String): ContractEventHistory = coroutineScope {
        retryWithBackoff("parseContractEvents($contractAddress)") {
            logger.debug("Parsing events for contract: $contractAddress")

            // Optimization: Only search recent blocks (last 10,000 blocks)
            // This covers approximately 5.5 hours on Avalanche (2s block time)
            // Most contracts should have all their events within this range
            val currentBlock = web3j.ethBlockNumber().send().blockNumber
            val searchDepth = BigInteger.valueOf(10000)
            val fromBlock = if (currentBlock > searchDepth) {
                currentBlock.subtract(searchDepth)
            } else {
                BigInteger.ZERO
            }

            // Query in parallel chunks to speed up the process
            val chunkSize = BigInteger.valueOf(2000)
            val chunks = mutableListOf<Pair<BigInteger, BigInteger>>()
            
            var chunkStart = fromBlock
            while (chunkStart <= currentBlock) {
                val chunkEnd = minOf(chunkStart.add(chunkSize.subtract(BigInteger.ONE)), currentBlock)
                chunks.add(chunkStart to chunkEnd)
                chunkStart = chunkEnd.add(BigInteger.ONE)
            }

            // Process chunks in parallel
            val chunkJobs = chunks.map { (start, end) ->
                async {
                    try {
                        val filter = EthFilter(
                            org.web3j.protocol.core.DefaultBlockParameter.valueOf(start),
                            org.web3j.protocol.core.DefaultBlockParameter.valueOf(end),
                            contractAddress
                        )
                        
                        val logs = web3j.ethGetLogs(filter).send().logs
                        logs?.map { it as Log } ?: emptyList()
                    } catch (e: Exception) {
                        logger.debug("Failed to fetch logs for chunk $start-$end: ${e.message}")
                        emptyList<Log>()
                    }
                }
            }

            val allLogs = chunkJobs.awaitAll().flatten()

            val events = allLogs.mapNotNull { log ->
                parseLogToEvent(log)
            }.sortedBy { it.timestamp }

            ContractEventHistory(contractAddress, events)
        } ?: ContractEventHistory(contractAddress, emptyList())
    }

    suspend fun findContractsByParticipant(walletAddress: String): List<String> {
        return try {
            logger.info("Finding contracts for participant: $walletAddress")
            logger.debug("Factory contract address: ${escrowProperties.contractFactoryAddress}")

            val contractAddresses = mutableSetOf<String>()
            
            // Pad wallet address to 32 bytes (64 hex chars) for topic filtering
            val walletPaddedHex = "0x" + "0".repeat(24) + walletAddress.substring(2)
            logger.debug("Wallet padded for topic filtering: $walletPaddedHex")
            
            // Get current block number and search in chunks due to RPC 2048 block limit
            val currentBlock = web3j.ethBlockNumber().send().blockNumber
            val searchRange = BigInteger.valueOf(20000) // Search last 20k blocks
            val fromBlock = currentBlock.subtract(searchRange)
            val chunkSize = BigInteger.valueOf(2000) // Stay under 2048 limit
            
            logger.warn("Searching from block $fromBlock to $currentBlock in chunks of $chunkSize")
            
            val allEventLogs = mutableListOf<org.web3j.protocol.core.methods.response.Log>()
            
            // Search in chunks to avoid RPC block limit
            var chunkStart = fromBlock
            while (chunkStart <= currentBlock) {
                val chunkEnd = minOf(chunkStart.add(chunkSize), currentBlock)
                
                val chunkLogs = retryWithBackoff("ethGetLogs chunk $chunkStart-$chunkEnd") {
                    val chunkFilter = EthFilter(
                        org.web3j.protocol.core.DefaultBlockParameter.valueOf(chunkStart),
                        org.web3j.protocol.core.DefaultBlockParameter.valueOf(chunkEnd),
                        escrowProperties.contractFactoryAddress
                    )
                    
                    web3j.ethGetLogs(chunkFilter).send().logs ?: emptyList()
                }
                
                if (chunkLogs != null) {
                    allEventLogs.addAll(chunkLogs.map { it as org.web3j.protocol.core.methods.response.Log })
                    
                    if (chunkLogs.isNotEmpty()) {
                        logger.warn("Chunk $chunkStart-$chunkEnd: Found ${chunkLogs.size} events")
                    }
                } else {
                    logger.warn("Chunk $chunkStart-$chunkEnd failed after retries, skipping")
                }
                
                chunkStart = chunkEnd.add(BigInteger.ONE)
            }
            
            logger.warn("Total events from factory (any type): ${allEventLogs.size}")
            
            // Let's check if we can see recent transactions TO this factory
            logger.warn("Let's check recent transactions to the factory directly...")
            
            // Get recent blocks and check transactions
            for (i in 0..10) {
                val blockNum = currentBlock.subtract(BigInteger.valueOf(i.toLong()))
                try {
                    val block = web3j.ethGetBlockByNumber(
                        org.web3j.protocol.core.DefaultBlockParameter.valueOf(blockNum), 
                        true
                    ).send().block
                    
                    if (block != null) {
                        val factoryTxs = block.transactions.filter { tx ->
                            val transaction = tx as org.web3j.protocol.core.methods.response.Transaction
                            transaction.to?.equals(escrowProperties.contractFactoryAddress, ignoreCase = true) == true
                        }
                        
                        if (factoryTxs.isNotEmpty()) {
                            logger.warn("Block ${blockNum}: Found ${factoryTxs.size} transactions to factory")
                            factoryTxs.take(2).forEach { tx ->
                                val transaction = tx as org.web3j.protocol.core.methods.response.Transaction
                                logger.warn("TX: ${transaction.hash}, from: ${transaction.from}, input: ${transaction.input?.take(20)}...")
                                
                                // Get the transaction receipt to see events
                                val receipt = web3j.ethGetTransactionReceipt(transaction.hash).send().transactionReceipt
                                if (receipt.isPresent) {
                                    logger.warn("Receipt logs count: ${receipt.get().logs.size}")
                                    receipt.get().logs.take(2).forEach { log ->
                                        logger.warn("Log topics[0]: ${log.topics.getOrNull(0)}")
                                    }
                                }
                            }
                            break // Found some, stop searching
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Error checking block $blockNum: ${e.message}")
                }
            }
            
            // Show what events are actually being emitted
            allEventLogs.take(3).forEach { log ->
                val logEntry = log as Log
                logger.warn("Actual event signature: ${logEntry.topics.getOrNull(0)}")
                logger.warn("Event topics count: ${logEntry.topics.size}")
                logger.warn("Event data: ${logEntry.data}")
            }
            
            // Now let's see what signature we're expecting
            val expectedSignature = EventEncoder.encode(contractCreatedEvent)
            logger.warn("Expected ContractCreated signature: $expectedSignature")
            
            // Try to find events matching our expected signature
            val matchingEvents = allEventLogs.filter { log ->
                val logEntry = log as Log
                logEntry.topics.isNotEmpty() && logEntry.topics[0] == expectedSignature
            }
            logger.warn("Events matching our expected signature: ${matchingEvents.size}")
            

            // Now filter the events for buyer/seller matches using chunked approach
            suspend fun searchWalletEvents(topicPosition: Int, role: String): List<org.web3j.protocol.core.methods.response.Log> {
                val matchingLogs = mutableListOf<org.web3j.protocol.core.methods.response.Log>()
                
                var chunkStart = fromBlock
                while (chunkStart <= currentBlock) {
                    val chunkEnd = minOf(chunkStart.add(chunkSize), currentBlock)
                    
                    val chunkLogs = retryWithBackoff("ethGetLogs $role chunk $chunkStart-$chunkEnd") {
                        val filter = EthFilter(
                            org.web3j.protocol.core.DefaultBlockParameter.valueOf(chunkStart),
                            org.web3j.protocol.core.DefaultBlockParameter.valueOf(chunkEnd),
                            escrowProperties.contractFactoryAddress
                        )
                        
                        // Set up topics for filtering
                        val topics = mutableListOf<String?>(EventEncoder.encode(contractCreatedEvent))
                        topics.add(null) // contract address - any
                        
                        when (topicPosition) {
                            2 -> { // Buyer position
                                topics.add(walletPaddedHex) // buyer = wallet
                                topics.add(null) // seller = any
                            }
                            3 -> { // Seller position  
                                topics.add(null) // buyer = any
                                topics.add(walletPaddedHex) // seller = wallet
                            }
                        }
                        
                        filter.addOptionalTopics(*topics.toTypedArray())
                        
                        web3j.ethGetLogs(filter).send().logs ?: emptyList()
                    }
                    
                    if (chunkLogs != null) {
                        matchingLogs.addAll(chunkLogs.map { it as org.web3j.protocol.core.methods.response.Log })
                        
                        if (chunkLogs.isNotEmpty()) {
                            logger.debug("Chunk $chunkStart-$chunkEnd: Found ${chunkLogs.size} events where wallet is $role")
                        }
                    } else {
                        logger.warn("$role chunk $chunkStart-$chunkEnd failed after retries, skipping")
                    }
                    
                    chunkStart = chunkEnd.add(BigInteger.ONE)
                }
                
                return matchingLogs
            }
            
            // Query 1: Find contracts where wallet is the buyer (topic[2])
            val buyerLogs = searchWalletEvents(2, "buyer")
            logger.debug("Found ${buyerLogs.size} contracts where wallet is buyer")

            // Extract contract addresses from buyer logs
            buyerLogs.forEach { log ->
                try {
                    val logEntry = log as Log
                    if (logEntry.topics.size >= 2) {
                        val contractAddress = "0x" + logEntry.topics[1].substring(26)
                        contractAddresses.add(contractAddress)
                        logger.debug("Added contract $contractAddress (wallet as buyer)")
                    }
                } catch (e: Exception) {
                    logger.warn("Error parsing buyer log", e)
                }
            }

            // Query 2: Find contracts where wallet is the seller (topic[3])
            val sellerLogs = searchWalletEvents(3, "seller")
            logger.debug("Found ${sellerLogs.size} contracts where wallet is seller")

            // Extract contract addresses from seller logs
            sellerLogs.forEach { log ->
                try {
                    val logEntry = log as Log
                    if (logEntry.topics.size >= 2) {
                        val contractAddress = "0x" + logEntry.topics[1].substring(26)
                        contractAddresses.add(contractAddress)
                        logger.debug("Added contract $contractAddress (wallet as seller)")
                    }
                } catch (e: Exception) {
                    logger.warn("Error parsing seller log", e)
                }
            }

            logger.info("Found ${contractAddresses.size} total contracts for participant: $walletAddress")
            contractAddresses.toList()

        } catch (e: Exception) {
            logger.error("Error finding contracts for participant: $walletAddress", e)
            emptyList()
        }
    }

    suspend fun findAllContracts(): List<String> {
        return try {
            logger.info("Finding all contracts (admin access)")
            
            val contractAddresses = mutableSetOf<String>()
            
            // Get current block number and search in chunks due to RPC 2048 block limit
            val currentBlock = web3j.ethBlockNumber().send().blockNumber
            val searchRange = BigInteger.valueOf(20000) // Search last 20k blocks
            val fromBlock = currentBlock.subtract(searchRange)
            val chunkSize = BigInteger.valueOf(2000) // Stay under 2048 limit
            
            logger.warn("Searching from block $fromBlock to $currentBlock in chunks of $chunkSize")
            
            // Search for all ContractCreated events regardless of participants
            var chunkStart = fromBlock
            while (chunkStart <= currentBlock) {
                val chunkEnd = minOf(chunkStart.add(chunkSize), currentBlock)
                
                val chunkLogs = retryWithBackoff("ethGetLogs findAllContracts chunk $chunkStart-$chunkEnd") {
                    val chunkFilter = EthFilter(
                        org.web3j.protocol.core.DefaultBlockParameter.valueOf(chunkStart),
                        org.web3j.protocol.core.DefaultBlockParameter.valueOf(chunkEnd),
                        escrowProperties.contractFactoryAddress
                    )
                    
                    // Add the ContractCreated event signature
                    chunkFilter.addSingleTopic(EventEncoder.encode(contractCreatedEvent))
                    
                    web3j.ethGetLogs(chunkFilter).send().logs
                }
                
                if (chunkLogs != null) {
                    logger.debug("Found ${chunkLogs.size} ContractCreated events in chunk $chunkStart-$chunkEnd")
                    
                    // Extract contract addresses from the logs
                    chunkLogs.forEach { log ->
                        try {
                            if (log is Log) {
                                val contractAddress = "0x" + log.topics[1].substring(26)
                                contractAddresses.add(contractAddress)
                                logger.debug("Added contract $contractAddress")
                            }
                        } catch (e: Exception) {
                            logger.warn("Error parsing contract creation log", e)
                        }
                    }
                } else {
                    logger.warn("findAllContracts chunk $chunkStart-$chunkEnd failed after retries, skipping")
                }
                
                chunkStart = chunkEnd.add(BigInteger.ONE)
            }
            
            logger.info("Found ${contractAddresses.size} total contracts")
            contractAddresses.toList()
            
        } catch (e: Exception) {
            logger.error("Error finding all contracts", e)
            emptyList()
        }
    }

    private fun parseLogToEvent(log: Log): ContractEvent? {
        return try {
            val topics = log.topics
            if (topics.isEmpty()) return null

            val eventSignature = topics[0]
            
            when (eventSignature) {
                EventEncoder.encode(contractCreatedEvent) -> {
                    parseContractCreatedEvent(log)
                }
                EventEncoder.encode(fundsDepositedEvent) -> {
                    parseFundsDepositedEvent(log)
                }
                EventEncoder.encode(disputeRaisedEvent) -> {
                    parseDisputeRaisedEvent(log)
                }
                EventEncoder.encode(disputeResolvedEvent) -> {
                    parseDisputeResolvedEvent(log)
                }
                EventEncoder.encode(fundsClaimedEvent) -> {
                    parseFundsClaimedEvent(log)
                }
                else -> {
                    logger.debug("Unknown event signature: $eventSignature")
                    null
                }
            }

        } catch (e: Exception) {
            logger.warn("Error parsing log to event", e)
            null
        }
    }

    private fun parseContractCreatedEvent(log: Log): ContractEvent? {
        return try {
            val topics = log.topics
            val contractAddress = "0x" + topics[1].substring(26)
            val buyer = "0x" + topics[2].substring(26)
            val seller = "0x" + topics[3].substring(26)

            val nonIndexedValues = FunctionReturnDecoder.decode(
                log.data,
                contractCreatedEvent.nonIndexedParameters
            )

            val amount = (nonIndexedValues[0] as Uint256).value
            val expiryTimestamp = (nonIndexedValues[1] as Uint256).value

            val timestampSeconds = blockTimestampCache.getOrPut(log.blockNumber) {
                val blockInfo = web3j.ethGetBlockByNumber(
                    org.web3j.protocol.core.DefaultBlockParameter.valueOf(log.blockNumber.toString()),
                    false
                ).send()
                blockInfo.block.timestamp.toLong()
            }

            val timestamp = Instant.ofEpochSecond(timestampSeconds)

            ContractEvent(
                eventType = EventType.CONTRACT_CREATED,
                timestamp = timestamp,
                transactionHash = log.transactionHash,
                blockNumber = log.blockNumber,
                data = mapOf(
                    "contractAddress" to contractAddress,
                    "buyer" to buyer,
                    "seller" to seller,
                    "amount" to amount,
                    "expiryTimestamp" to expiryTimestamp
                )
            )

        } catch (e: Exception) {
            logger.error("Error parsing ContractCreated event", e)
            null
        }
    }

    private fun parseDisputeRaisedEvent(log: Log): ContractEvent? {
        return try {
            val nonIndexedValues = FunctionReturnDecoder.decode(
                log.data,
                disputeRaisedEvent.nonIndexedParameters
            )

            val eventTimestamp = (nonIndexedValues[0] as Uint256).value

            val timestampSeconds = blockTimestampCache.getOrPut(log.blockNumber) {
                val blockInfo = web3j.ethGetBlockByNumber(
                    org.web3j.protocol.core.DefaultBlockParameter.valueOf(log.blockNumber.toString()),
                    false
                ).send()
                blockInfo.block.timestamp.toLong()
            }

            val timestamp = Instant.ofEpochSecond(timestampSeconds)

            ContractEvent(
                eventType = EventType.DISPUTE_RAISED,
                timestamp = timestamp,
                transactionHash = log.transactionHash,
                blockNumber = log.blockNumber,
                data = mapOf(
                    "eventTimestamp" to eventTimestamp
                )
            )

        } catch (e: Exception) {
            logger.error("Error parsing DisputeRaised event", e)
            null
        }
    }

    private fun parseDisputeResolvedEvent(log: Log): ContractEvent? {
        return try {
            val nonIndexedValues = FunctionReturnDecoder.decode(
                log.data,
                disputeResolvedEvent.nonIndexedParameters
            )

            val recipient = (nonIndexedValues[0] as Address).value
            val eventTimestamp = (nonIndexedValues[1] as Uint256).value

            val timestampSeconds = blockTimestampCache.getOrPut(log.blockNumber) {
                val blockInfo = web3j.ethGetBlockByNumber(
                    org.web3j.protocol.core.DefaultBlockParameter.valueOf(log.blockNumber.toString()),
                    false
                ).send()
                blockInfo.block.timestamp.toLong()
            }

            val timestamp = Instant.ofEpochSecond(timestampSeconds)

            ContractEvent(
                eventType = EventType.DISPUTE_RESOLVED,
                timestamp = timestamp,
                transactionHash = log.transactionHash,
                blockNumber = log.blockNumber,
                data = mapOf(
                    "recipient" to recipient,
                    "eventTimestamp" to eventTimestamp
                )
            )

        } catch (e: Exception) {
            logger.error("Error parsing DisputeResolved event", e)
            null
        }
    }

    private fun parseFundsDepositedEvent(log: Log): ContractEvent? {
        return try {
            val nonIndexedValues = FunctionReturnDecoder.decode(
                log.data,
                fundsDepositedEvent.nonIndexedParameters
            )

            val buyer = (nonIndexedValues[0] as Address).value
            val amount = (nonIndexedValues[1] as Uint256).value
            val eventTimestamp = (nonIndexedValues[2] as Uint256).value

            val timestampSeconds = blockTimestampCache.getOrPut(log.blockNumber) {
                val blockInfo = web3j.ethGetBlockByNumber(
                    org.web3j.protocol.core.DefaultBlockParameter.valueOf(log.blockNumber.toString()),
                    false
                ).send()
                blockInfo.block.timestamp.toLong()
            }

            val timestamp = Instant.ofEpochSecond(timestampSeconds)

            ContractEvent(
                eventType = EventType.FUNDS_DEPOSITED,
                timestamp = timestamp,
                transactionHash = log.transactionHash,
                blockNumber = log.blockNumber,
                data = mapOf(
                    "buyer" to buyer,
                    "amount" to amount,
                    "eventTimestamp" to eventTimestamp
                )
            )

        } catch (e: Exception) {
            logger.error("Error parsing FundsDeposited event", e)
            null
        }
    }

    private fun parseFundsClaimedEvent(log: Log): ContractEvent? {
        return try {
            val nonIndexedValues = FunctionReturnDecoder.decode(
                log.data,
                fundsClaimedEvent.nonIndexedParameters
            )

            val recipient = (nonIndexedValues[0] as Address).value
            val amount = (nonIndexedValues[1] as Uint256).value
            val eventTimestamp = (nonIndexedValues[2] as Uint256).value

            val timestampSeconds = blockTimestampCache.getOrPut(log.blockNumber) {
                val blockInfo = web3j.ethGetBlockByNumber(
                    org.web3j.protocol.core.DefaultBlockParameter.valueOf(log.blockNumber.toString()),
                    false
                ).send()
                blockInfo.block.timestamp.toLong()
            }

            val timestamp = Instant.ofEpochSecond(timestampSeconds)

            ContractEvent(
                eventType = EventType.FUNDS_CLAIMED,
                timestamp = timestamp,
                transactionHash = log.transactionHash,
                blockNumber = log.blockNumber,
                data = mapOf(
                    "recipient" to recipient,
                    "amount" to amount,
                    "eventTimestamp" to eventTimestamp
                )
            )

        } catch (e: Exception) {
            logger.error("Error parsing FundsClaimed event", e)
            null
        }
    }
}