package com.conduit.chainservice.config

import com.utility.chainservice.AuthenticationProvider
import com.utility.chainservice.HttpAuthenticationProvider
import com.utility.chainservice.BlockchainProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UtilityServiceConfiguration(
    // WARNING: NEVER log blockchainProperties directly - it contains sensitive privateKey!
    // Use ConfigurationLogger for safe logging of configuration values
    private val blockchainProperties: BlockchainProperties,
    private val escrowProperties: EscrowProperties
) {

    @Bean
    @Qualifier("rpcUrl")
    fun rpcUrl(): String = blockchainProperties.rpcUrl

    @Bean
    @Qualifier("relayerPrivateKey")
    fun relayerPrivateKey(): String = blockchainProperties.relayer.privateKey

    @Bean
    fun gasPriceMultiplier(): Double = blockchainProperties.gas.priceMultiplier

    @Bean
    fun gasLimitsConfig(): Map<String, Long> = mapOf(
        "createContract" to escrowProperties.limitCreateContract,
        "depositFunds" to escrowProperties.limitDeposit,
        "raiseDispute" to escrowProperties.limitDispute,
        "claimFunds" to escrowProperties.limitClaim,
        "resolveDispute" to escrowProperties.limitResolve,
        "approveUSDC" to escrowProperties.limitApproveUsdc
    )

    // Additional escrow-specific properties
    @Bean
    @Qualifier("usdcContractAddress")
    fun usdcContractAddress(): String = escrowProperties.usdcContractAddress

    @Bean
    @Qualifier("contractFactoryAddress")
    fun contractFactoryAddress(): String = escrowProperties.contractFactoryAddress

    @Bean
    fun creatorFee(): java.math.BigInteger = escrowProperties.creatorFee

    // Security configuration path for SecurityConfigurationService
    @Bean
    @Qualifier("securityConfigPath")
    fun securityConfigPath(): String = "./config/security-config.json"
}