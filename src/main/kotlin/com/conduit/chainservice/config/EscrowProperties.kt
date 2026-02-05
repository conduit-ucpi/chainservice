package com.conduit.chainservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigInteger

@ConfigurationProperties(prefix = "escrow")
data class EscrowProperties(
    var contractFactoryAddress: String = "",
    var implementationAddress: String = "",
    var minCreatorFee: BigInteger = BigInteger.ZERO,
    var limitCreateContract: Long = 0,
    var limitDeposit: Long = 0,
    var limitDispute: Long = 0,
    var limitClaim: Long = 0,
    var limitResolve: Long = 0,
    var limitApproveToken: Long = 0,
    var gasMultiplier: Double = 0.0
)