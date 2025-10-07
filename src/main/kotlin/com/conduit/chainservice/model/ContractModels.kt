package com.conduit.chainservice.model

import com.fasterxml.jackson.annotation.JsonProperty
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.StaticStruct
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

/**
 * Web3j-compatible struct for Multicall3 Call3 type
 * Represents: struct Call3 { address target; bool allowFailure; bytes callData; }
 */
class Multicall3Call3(
    val target: org.web3j.abi.datatypes.Address,
    val allowFailure: Bool,
    val callData: DynamicBytes
) : StaticStruct(target, allowFailure, callData) {

    companion object {
        @JvmStatic
        fun create(target: String, allowFailure: Boolean, callData: ByteArray): Multicall3Call3 {
            return Multicall3Call3(
                org.web3j.abi.datatypes.Address(target),
                Bool(allowFailure),
                DynamicBytes(callData)
            )
        }
    }
}

/**
 * Web3j-compatible struct for Multicall3 Result type
 * Represents: struct Result { bool success; bytes returnData; }
 */
class Multicall3Result(
    val success: Bool,
    val returnData: DynamicBytes
) : StaticStruct(success, returnData) {

    companion object {
        @JvmStatic
        fun create(success: Boolean, returnData: ByteArray): Multicall3Result {
            return Multicall3Result(
                Bool(success),
                DynamicBytes(returnData)
            )
        }
    }
}