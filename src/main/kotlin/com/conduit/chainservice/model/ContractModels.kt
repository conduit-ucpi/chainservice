package com.conduit.chainservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigInteger
import java.time.Instant

data class ContractInfo(
    val contractAddress: String,
    val buyer: String,
    val seller: String,
    val amount: BigInteger,
    val expiryTimestamp: Long,
    val description: String,
    val status: ContractStatus,
    val createdAt: Instant,
    val disputedAt: Instant? = null,
    val resolvedAt: Instant? = null,
    val claimedAt: Instant? = null
)

enum class ContractStatus {
    @JsonProperty("ACTIVE")
    ACTIVE,
    
    @JsonProperty("EXPIRED")
    EXPIRED,
    
    @JsonProperty("DISPUTED")
    DISPUTED,
    
    @JsonProperty("RESOLVED")
    RESOLVED,
    
    @JsonProperty("CLAIMED")
    CLAIMED
}

data class TransactionResult(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)

data class ContractCreationResult(
    val success: Boolean,
    val transactionHash: String?,
    val contractAddress: String?,
    val error: String? = null
)

data class ContractEventHistory(
    val contractAddress: String,
    val events: List<ContractEvent>
)

data class ContractEvent(
    val eventType: EventType,
    val timestamp: Instant,
    val transactionHash: String,
    val blockNumber: BigInteger,
    val data: Map<String, Any> = emptyMap()
)

enum class EventType {
    CONTRACT_CREATED,
    DISPUTE_RAISED,
    DISPUTE_RESOLVED,
    FUNDS_CLAIMED
}