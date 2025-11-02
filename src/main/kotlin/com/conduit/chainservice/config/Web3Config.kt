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


@Configuration
@EnableConfigurationProperties(BlockchainProperties::class, EscrowProperties::class)
class Web3Config(
    private val blockchainProperties: BlockchainProperties,
    private val escrowProperties: EscrowProperties
) {

    private val logger = LoggerFactory.getLogger(Web3Config::class.java)
    
    init {
        logger.debug("Web3Config initialized with gas price settings:")
        logger.debug("  - gasPriceMultiplier: ${blockchainProperties.gas.priceMultiplier}")
        logger.debug("  - minimumGasPriceWei: ${blockchainProperties.gas.minimumGasPriceWei}")
        logger.debug("  - escrow gas limits: ${escrowProperties}")
    }

    // Web3j bean is provided by Web3jTimeoutConfig with better timeout configuration

    @Bean
    @Primary
    fun relayerCredentials(): Credentials {
        logger.info("Creating relayer credentials for address: ${blockchainProperties.relayer.walletAddress}")
        return Credentials.create(blockchainProperties.relayer.privateKey)
    }

    @Bean("chainId")
    @Primary
    fun chainId(): Long {
        return blockchainProperties.chainId
    }

    @Bean
    @Primary
    fun escrowGasProvider(web3j: Web3j): ContractGasProvider {
        return object : ContractGasProvider {
            override fun getGasPrice(contractFunc: String?): BigInteger {
                return try {
                    val networkGasPrice = web3j.ethGasPrice().send().gasPrice
                    val multipliedPrice = networkGasPrice.multiply(
                        BigInteger.valueOf((blockchainProperties.gas.priceMultiplier * 100).toLong())
                    ).divide(BigInteger.valueOf(100))
                    
                    // Enforce minimum gas price
                    val minimumPrice = BigInteger.valueOf(blockchainProperties.gas.minimumGasPriceWei)
                    val finalPrice = maxOf(multipliedPrice, minimumPrice)
                    logger.debug("Gas price calculation for $contractFunc: networkPrice=$networkGasPrice, priceMultiplier=${blockchainProperties.gas.priceMultiplier}, multipliedPrice=$multipliedPrice, minimumPrice=$minimumPrice, finalPrice=$finalPrice")
                    finalPrice
                } catch (e: Exception) {
                    logger.warn("Failed to fetch gas price from network, using minimum: ${e.message}")
                    BigInteger.valueOf(blockchainProperties.gas.minimumGasPriceWei)
                }
            }

            override fun getGasPrice(): BigInteger = getGasPrice(null)

            override fun getGasLimit(contractFunc: String?): BigInteger {
                return when (contractFunc) {
                    "createContract" -> BigInteger.valueOf(escrowProperties.limitCreateContract)
                    "depositFunds" -> BigInteger.valueOf(escrowProperties.limitDeposit)
                    "raiseDispute" -> BigInteger.valueOf(escrowProperties.limitDispute)
                    "claimFunds" -> BigInteger.valueOf(escrowProperties.limitClaim)
                    "resolveDispute" -> BigInteger.valueOf(escrowProperties.limitResolve)
                    "approveToken" -> BigInteger.valueOf(escrowProperties.limitApproveToken)
                    else -> DefaultGasProvider.GAS_LIMIT
                }
            }

            override fun getGasLimit(): BigInteger = getGasLimit(null)
        }
    }
}