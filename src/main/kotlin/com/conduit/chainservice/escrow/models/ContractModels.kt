package com.conduit.chainservice.escrow.models

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
    val funded: Boolean,
    val status: ContractStatus,
    val createdAt: Instant,
    val fundedAt: Instant? = null,
    val disputedAt: Instant? = null,
    val resolvedAt: Instant? = null,
    val claimedAt: Instant? = null
)

enum class ContractStatus {
    @JsonProperty("CREATED")
    CREATED,    // Contract created but not funded
    
    @JsonProperty("ACTIVE")
    ACTIVE,     // Funded and active
    
    @JsonProperty("EXPIRED")
    EXPIRED,    // Funded but expired
    
    @JsonProperty("DISPUTED")
    DISPUTED,   // Dispute raised
    
    @JsonProperty("RESOLVED")
    RESOLVED,   // Dispute resolved
    
    @JsonProperty("CLAIMED")
    CLAIMED     // Funds claimed
}

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
    FUNDS_DEPOSITED,
    DISPUTE_RAISED,
    DISPUTE_RESOLVED,
    FUNDS_CLAIMED
}