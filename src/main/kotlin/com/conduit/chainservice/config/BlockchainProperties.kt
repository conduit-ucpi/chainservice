package com.conduit.chainservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "blockchain")
data class BlockchainProperties(
    var rpcUrl: String = "",
    var chainId: Long = 43113,
    var relayer: RelayerProperties = RelayerProperties(),
    var timeout: TimeoutProperties = TimeoutProperties(),
    var gas: GasProperties = GasProperties()
)

data class RelayerProperties(
    var walletAddress: String = "",
    var gasPayerContractAddress: String = "0x0000000000000000000000000000000000000000",
    var privateKey: String = "",
    var useConfigFile: Boolean = false
)

data class TimeoutProperties(
    var connectSeconds: Long = 30,
    var readSeconds: Long = 60,
    var writeSeconds: Long = 60
)

data class GasProperties(
    var priceMultiplier: Double = 1.2,
    var minimumGasPriceWei: Long = 6,
    var maxGasLimit: Long = 1,
    var maxGasCostWei: Long = 1,
    var multiplier: Double = 1.11,
    var maxGasPriceMultiplier: Double = 3.0
)