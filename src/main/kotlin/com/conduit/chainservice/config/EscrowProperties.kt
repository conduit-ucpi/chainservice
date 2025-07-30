package com.conduit.chainservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigInteger

@ConfigurationProperties(prefix = "blockchain")
data class EscrowProperties(
    var usdcContractAddress: String = "",
    var contractFactoryAddress: String = "",
    var creatorFee: BigInteger = BigInteger.ZERO,
    var gas: EscrowGasProperties = EscrowGasProperties()
)

data class EscrowGasProperties(
    var limitCreateContract: Long = 500000,
    var limitDeposit: Long = 74161, 
    var limitDispute: Long = 9633,
    var limitClaim: Long = 51702,
    var limitResolve: Long = 200000,
    var limitApproveUSDC: Long = 60000
)