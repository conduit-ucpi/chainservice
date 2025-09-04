package com.conduit.chainservice.model

import com.fasterxml.jackson.annotation.JsonProperty

data class SignedTransactionRequest(
    @JsonProperty("userWalletAddress")
    val userWalletAddress: String,
    
    @JsonProperty("signedTransactionHex")
    val signedTransactionHex: String
)

data class SignedTransactionResponse(
    @JsonProperty("success")
    val success: Boolean,
    
    @JsonProperty("transactionHash")
    val transactionHash: String? = null,
    
    @JsonProperty("contractAddress")
    val contractAddress: String? = null,
    
    @JsonProperty("error")
    val error: String? = null
)