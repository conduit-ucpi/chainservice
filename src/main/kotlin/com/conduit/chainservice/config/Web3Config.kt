package com.conduit.chainservice.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger
import jakarta.annotation.PostConstruct

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

    private val logger = LoggerFactory.getLogger(Web3Config::class.java)

    @PostConstruct
    fun validateConfiguration() {
        val errors = mutableListOf<String>()
        
        if (blockchainProperties.rpcUrl.isBlank()) {
            errors.add("RPC_URL environment variable is required")
        }
        
        if (blockchainProperties.usdcContractAddress.isBlank()) {
            errors.add("USDC_CONTRACT_ADDRESS environment variable is required")
        }
        
        if (blockchainProperties.contractFactoryAddress.isBlank()) {
            errors.add("CONTRACT_FACTORY_ADDRESS environment variable is required")
        }
        
        if (blockchainProperties.relayer.privateKey.isBlank()) {
            errors.add("RELAYER_PRIVATE_KEY environment variable is required")
        }
        
        if (blockchainProperties.relayer.walletAddress.isBlank()) {
            errors.add("RELAYER_WALLET_ADDRESS environment variable is required")
        }
        
        if (errors.isNotEmpty()) {
            val errorMessage = "Configuration validation failed:\n" + errors.joinToString("\n- ", "- ")
            logger.error(errorMessage)
            throw IllegalStateException(errorMessage)
        }
        
        logger.info("Configuration validation successful")
    }

    @Bean
    fun web3j(): Web3j {
        return Web3j.build(HttpService(blockchainProperties.rpcUrl))
    }

    @Bean
    fun relayerCredentials(): Credentials {
        try {
            val privateKey = blockchainProperties.relayer.privateKey
            if (privateKey.isBlank()) {
                throw IllegalArgumentException("RELAYER_PRIVATE_KEY is empty or not set")
            }
            
            // Validate private key format
            if (!privateKey.startsWith("0x") || privateKey.length != 66) {
                throw IllegalArgumentException("RELAYER_PRIVATE_KEY must be a 64-character hex string prefixed with 0x")
            }
            
            return Credentials.create(privateKey)
        } catch (e: Exception) {
            logger.error("Failed to create relayer credentials: ${e.message}")
            throw IllegalStateException("Invalid RELAYER_PRIVATE_KEY: ${e.message}", e)
        }
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