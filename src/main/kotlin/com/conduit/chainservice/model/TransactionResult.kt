package com.conduit.chainservice.model

data class TransactionResult(
    val success: Boolean,
    val transactionHash: String?,
    val contractAddress: String? = null,
    val error: String? = null
)