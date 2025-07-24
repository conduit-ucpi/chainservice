package com.conduit.chainservice.service

import com.conduit.chainservice.config.BlockchainProperties
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
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.Log
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.time.Instant

@Service
class EventParsingService(
    private val web3j: Web3j,
    private val blockchainProperties: BlockchainProperties
) {

    private val logger = LoggerFactory.getLogger(EventParsingService::class.java)

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

    suspend fun parseContractEvents(contractAddress: String): ContractEventHistory {
        return try {
            logger.info("Parsing events for contract: $contractAddress")

            val filter = EthFilter(
                DefaultBlockParameterName.EARLIEST,
                DefaultBlockParameterName.LATEST,
                contractAddress
            )

            val logs = web3j.ethGetLogs(filter).send().logs

            val events = logs.mapNotNull { log ->
                parseLogToEvent(log as Log)
            }.sortedBy { it.timestamp }

            ContractEventHistory(contractAddress, events)

        } catch (e: Exception) {
            logger.error("Error parsing events for contract: $contractAddress", e)
            ContractEventHistory(contractAddress, emptyList())
        }
    }

    suspend fun findContractsByParticipant(walletAddress: String): List<String> {
        return try {
            logger.info("Finding contracts for participant: $walletAddress")

            val contractAddresses = mutableSetOf<String>()

            val filter = EthFilter(
                DefaultBlockParameterName.EARLIEST,
                DefaultBlockParameterName.LATEST,
                blockchainProperties.contractFactoryAddress
            )

            filter.addSingleTopic(EventEncoder.encode(contractCreatedEvent))

            val logs = web3j.ethGetLogs(filter).send().logs

            for (log in logs) {
                val logEntry = log as Log
                try {
                    val topics = logEntry.topics
                    if (topics.size >= 4) {
                        val contractAddress = "0x" + topics[1].substring(26)
                        val buyer = "0x" + topics[2].substring(26)
                        val seller = "0x" + topics[3].substring(26)
                        
                        if (buyer.equals(walletAddress, ignoreCase = true) || 
                            seller.equals(walletAddress, ignoreCase = true)) {
                            contractAddresses.add(contractAddress)
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Error parsing contract created log", e)
                }
            }

            contractAddresses.toList()

        } catch (e: Exception) {
            logger.error("Error finding contracts for participant: $walletAddress", e)
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
            val description = (nonIndexedValues[2] as Utf8String).value

            val blockInfo = web3j.ethGetBlockByNumber(
                org.web3j.protocol.core.DefaultBlockParameter.valueOf(log.blockNumber.toString()),
                false
            ).send()

            val timestamp = Instant.ofEpochSecond(
                blockInfo.block.timestamp.toLong()
            )

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
                    "expiryTimestamp" to expiryTimestamp,
                    "description" to description
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

            val blockInfo = web3j.ethGetBlockByNumber(
                org.web3j.protocol.core.DefaultBlockParameter.valueOf(log.blockNumber.toString()),
                false
            ).send()

            val timestamp = Instant.ofEpochSecond(
                blockInfo.block.timestamp.toLong()
            )

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

            val blockInfo = web3j.ethGetBlockByNumber(
                org.web3j.protocol.core.DefaultBlockParameter.valueOf(log.blockNumber.toString()),
                false
            ).send()

            val timestamp = Instant.ofEpochSecond(
                blockInfo.block.timestamp.toLong()
            )

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

            val blockInfo = web3j.ethGetBlockByNumber(
                org.web3j.protocol.core.DefaultBlockParameter.valueOf(log.blockNumber.toString()),
                false
            ).send()

            val timestamp = Instant.ofEpochSecond(
                blockInfo.block.timestamp.toLong()
            )

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

            val blockInfo = web3j.ethGetBlockByNumber(
                org.web3j.protocol.core.DefaultBlockParameter.valueOf(log.blockNumber.toString()),
                false
            ).send()

            val timestamp = Instant.ofEpochSecond(
                blockInfo.block.timestamp.toLong()
            )

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