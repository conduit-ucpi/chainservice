package com.conduit.chainservice.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger
import jakarta.annotation.PostConstruct

@ConfigurationProperties(prefix = "blockchain")
data class EscrowBlockchainProperties(
    var rpcUrl: String = "",
    var usdcContractAddress: String = "",
    var contractFactoryAddress: String = "",
    var chainId: Long = 43113,
    var creatorFee: BigInteger = BigInteger.ZERO,
    var relayer: RelayerProperties = RelayerProperties(),
    var gas: GasProperties = GasProperties()
)

data class RelayerProperties(
    var privateKey: String = "",
    var walletAddress: String = ""
)

data class GasProperties(
    var limitCreateContract: Long = 500000,
    var limitDeposit: Long = 74161, // 67419 + 10% buffer
    var limitDispute: Long = 9633, // 8757 + 10% buffer
    var limitClaim: Long = 51702, // 47002 + 10% buffer
    var limitResolve: Long = 200000,
    var limitApproveUSDC: Long = 60000, // Standard ERC20 approve gas limit
    var priceMultiplier: Double = 1.7,
    var minimumGasPriceWei: Long = 6 // Minimum gas price in wei
)

@Configuration
@EnableConfigurationProperties(EscrowBlockchainProperties::class)
class Web3Config(private val escrowBlockchainProperties: EscrowBlockchainProperties) {

    private val logger = LoggerFactory.getLogger(Web3Config::class.java)

    @PostConstruct
    fun validateConfiguration() {
        val errors = mutableListOf<String>()
        
        if (escrowBlockchainProperties.rpcUrl.isBlank()) {
            errors.add("RPC_URL environment variable is required")
        }
        
        if (escrowBlockchainProperties.usdcContractAddress.isBlank()) {
            errors.add("USDC_CONTRACT_ADDRESS environment variable is required")
        }
        
        if (escrowBlockchainProperties.contractFactoryAddress.isBlank()) {
            errors.add("CONTRACT_FACTORY_ADDRESS environment variable is required")
        }
        
        if (escrowBlockchainProperties.relayer.privateKey.isBlank()) {
            errors.add("RELAYER_PRIVATE_KEY environment variable is required")
        }
        
        if (escrowBlockchainProperties.relayer.walletAddress.isBlank()) {
            errors.add("RELAYER_WALLET_ADDRESS environment variable is required")
        }
        
        if (escrowBlockchainProperties.creatorFee < BigInteger.ZERO) {
            errors.add("CREATOR_FEE_USDC_X_1M environment variable must be non-negative")
        }
        
        if (errors.isNotEmpty()) {
            val errorMessage = "Configuration validation failed:\n" + errors.joinToString("\n- ", "- ")
            logger.error(errorMessage)
            throw IllegalStateException(errorMessage)
        }
        
        logger.info("Configuration validation successful")
    }

    // web3j bean is provided by blockchain-relay-utility

    // relayerCredentials bean is provided by blockchain-relay-utility
    @Bean
    fun escrowRelayerCredentials(): Credentials {
        try {
            val privateKey = escrowBlockchainProperties.relayer.privateKey
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

    // chainId bean is provided by blockchain-relay-utility  
    @Bean
    fun escrowChainId(web3j: Web3j): Long {
        return try {
            val chainIdResponse = web3j.ethChainId().send()
            if (chainIdResponse.hasError()) {
                logger.warn("Failed to retrieve chain ID from RPC: ${chainIdResponse.error.message}, using configured value: ${escrowBlockchainProperties.chainId}")
                escrowBlockchainProperties.chainId
            } else {
                val rpcChainId = chainIdResponse.chainId.toLong()
                logger.info("Retrieved chain ID from RPC: $rpcChainId")
                rpcChainId
            }
        } catch (e: Exception) {
            logger.warn("Error retrieving chain ID from RPC: ${e.message}, using configured value: ${escrowBlockchainProperties.chainId}")
            escrowBlockchainProperties.chainId
        }
    }

    @Bean
    fun gasProvider(web3j: Web3j): ContractGasProvider {
        return object : ContractGasProvider {
            override fun getGasPrice(contractFunc: String?): BigInteger {
                return try {
                    val networkGasPrice = web3j.ethGasPrice().send().gasPrice
                    val multipliedPrice = networkGasPrice.multiply(
                        BigInteger.valueOf((escrowBlockchainProperties.gas.priceMultiplier * 100).toLong())
                    ).divide(BigInteger.valueOf(100))
                    
                    // Enforce minimum gas price
                    val minimumPrice = BigInteger.valueOf(escrowBlockchainProperties.gas.minimumGasPriceWei)
                    maxOf(multipliedPrice, minimumPrice)
                } catch (e: Exception) {
                    logger.warn("Failed to fetch gas price from network, using minimum: ${e.message}")
                    BigInteger.valueOf(escrowBlockchainProperties.gas.minimumGasPriceWei)
                }
            }

            override fun getGasPrice(): BigInteger {
                return getGasPrice(null)
            }

            override fun getGasLimit(contractFunc: String?): BigInteger {
                return when (contractFunc) {
                    "createContract" -> BigInteger.valueOf(escrowBlockchainProperties.gas.limitCreateContract)
                    "depositFunds" -> BigInteger.valueOf(escrowBlockchainProperties.gas.limitDeposit)
                    "raiseDispute" -> BigInteger.valueOf(escrowBlockchainProperties.gas.limitDispute)
                    "claimFunds" -> BigInteger.valueOf(escrowBlockchainProperties.gas.limitClaim)
                    "resolveDispute" -> BigInteger.valueOf(escrowBlockchainProperties.gas.limitResolve)
                    "approveUSDC" -> BigInteger.valueOf(escrowBlockchainProperties.gas.limitApproveUSDC)
                    else -> DefaultGasProvider.GAS_LIMIT
                }
            }

            override fun getGasLimit(): BigInteger {
                return getGasLimit(null)
            }
        }
    }
}