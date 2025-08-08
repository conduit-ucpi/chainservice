package com.conduit.chainservice.config

import com.utility.chainservice.AuthenticationProvider
import com.utility.chainservice.HttpAuthenticationProvider
import com.utility.chainservice.BlockchainProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UtilityServiceConfiguration(
    private val blockchainProperties: BlockchainProperties,
    private val escrowProperties: EscrowProperties
) {

    @Bean
    fun rpcUrl(): String = blockchainProperties.rpcUrl

    @Bean
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
    fun usdcContractAddress(): String = escrowProperties.usdcContractAddress

    @Bean
    fun contractFactoryAddress(): String = escrowProperties.contractFactoryAddress

    @Bean
    fun creatorFee(): java.math.BigInteger = escrowProperties.creatorFee
}