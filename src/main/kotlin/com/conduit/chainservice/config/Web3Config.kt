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
class Web3Config(
    private val gasLimitsConfig: Map<String, Long>,
    private val gasPriceMultiplier: Double,
    private val minimumGasPriceWei: Long
) {

    private val logger = LoggerFactory.getLogger(Web3Config::class.java)
    
    init {
        logger.debug("Web3Config initialized with gas price settings:")
        logger.debug("  - gasPriceMultiplier: $gasPriceMultiplier")
        logger.debug("  - minimumGasPriceWei: $minimumGasPriceWei")
        logger.debug("  - gasLimits: $gasLimitsConfig")
    }

    // Configuration validation is now handled by UtilityAutoConfiguration

    // web3j bean is provided by blockchain-relay-utility

    // relayerCredentials bean is provided by blockchain-relay-utility

    // chainId bean is provided by blockchain-relay-utility

    @Bean
    @Primary
    fun escrowGasProvider(web3j: Web3j): ContractGasProvider {
        return object : ContractGasProvider {
            override fun getGasPrice(contractFunc: String?): BigInteger {
                return try {
                    val networkGasPrice = web3j.ethGasPrice().send().gasPrice
                    val multipliedPrice = networkGasPrice.multiply(
                        BigInteger.valueOf((gasPriceMultiplier * 100).toLong())
                    ).divide(BigInteger.valueOf(100))
                    
                    // Enforce minimum gas price
                    val minimumPrice = BigInteger.valueOf(minimumGasPriceWei)
                    val finalPrice = maxOf(multipliedPrice, minimumPrice)
                    logger.debug("Gas price calculation for $contractFunc: networkPrice=$networkGasPrice, priceMultiplier=$gasPriceMultiplier, multipliedPrice=$multipliedPrice, minimumPrice=$minimumPrice, finalPrice=$finalPrice")
                    finalPrice
                } catch (e: Exception) {
                    logger.warn("Failed to fetch gas price from network, using minimum: ${e.message}")
                    BigInteger.valueOf(minimumGasPriceWei)
                }
            }

            override fun getGasPrice(): BigInteger = getGasPrice(null)

            override fun getGasLimit(contractFunc: String?): BigInteger {
                return when (contractFunc) {
                    "createContract" -> BigInteger.valueOf(gasLimitsConfig["createContract"] ?: 500000)
                    "depositFunds" -> BigInteger.valueOf(gasLimitsConfig["depositFunds"] ?: 250000)
                    "raiseDispute" -> BigInteger.valueOf(gasLimitsConfig["raiseDispute"] ?: 300000)
                    "claimFunds" -> BigInteger.valueOf(gasLimitsConfig["claimFunds"] ?: 200000)
                    "resolveDispute" -> BigInteger.valueOf(gasLimitsConfig["resolveDispute"] ?: 200000)
                    "approveUSDC" -> BigInteger.valueOf(gasLimitsConfig["approveUSDC"] ?: 60000)
                    else -> DefaultGasProvider.GAS_LIMIT
                }
            }

            override fun getGasLimit(): BigInteger = getGasLimit(null)
        }
    }
}