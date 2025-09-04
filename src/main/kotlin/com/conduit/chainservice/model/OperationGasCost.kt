package com.conduit.chainservice.model

import java.math.BigInteger

data class OperationGasCost(
    val operation: String,
    val gasLimit: BigInteger,
    val gasPriceWei: BigInteger,
    val totalCostWei: BigInteger,
    val totalCostAvax: String = "0.000"
)