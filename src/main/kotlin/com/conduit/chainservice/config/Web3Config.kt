package com.conduit.chainservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger

@Configuration
@ConfigurationProperties(prefix = "blockchain")
data class BlockchainProperties(
    var rpcUrl: String = "",
    var usdcContractAddress: String = "",
    var contractFactoryAddress: String = "",
    var relayer: RelayerProperties = RelayerProperties(),
    var gas: GasProperties = GasProperties()
)

data class RelayerProperties(
    var privateKey: String = "",
    var walletAddress: String = ""
)

data class GasProperties(
    var limitCreateContract: Long = 500000,
    var limitDeposit: Long = 250000,
    var limitDispute: Long = 300000,
    var limitClaim: Long = 200000,
    var limitResolve: Long = 200000,
    var priceMultiplier: Double = 1.2
)

@Configuration
class Web3Config(private val blockchainProperties: BlockchainProperties) {

    @Bean
    fun web3j(): Web3j {
        return Web3j.build(HttpService(blockchainProperties.rpcUrl))
    }

    @Bean
    fun relayerCredentials(): Credentials {
        return Credentials.create(blockchainProperties.relayer.privateKey)
    }

    @Bean
    fun gasProvider(): ContractGasProvider {
        return object : ContractGasProvider {
            override fun getGasPrice(contractFunc: String?): BigInteger {
                return DefaultGasProvider.GAS_PRICE.multiply(
                    BigInteger.valueOf((blockchainProperties.gas.priceMultiplier * 100).toLong())
                ).divide(BigInteger.valueOf(100))
            }

            override fun getGasPrice(): BigInteger {
                return getGasPrice(null)
            }

            override fun getGasLimit(contractFunc: String?): BigInteger {
                return when (contractFunc) {
                    "createContract" -> BigInteger.valueOf(blockchainProperties.gas.limitCreateContract)
                    "depositFunds" -> BigInteger.valueOf(blockchainProperties.gas.limitDeposit)
                    "raiseDispute" -> BigInteger.valueOf(blockchainProperties.gas.limitDispute)
                    "claimFunds" -> BigInteger.valueOf(blockchainProperties.gas.limitClaim)
                    "resolveDispute" -> BigInteger.valueOf(blockchainProperties.gas.limitResolve)
                    else -> DefaultGasProvider.GAS_LIMIT
                }
            }

            override fun getGasLimit(): BigInteger {
                return getGasLimit(null)
            }
        }
    }
}