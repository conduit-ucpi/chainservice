package com.conduit.chainservice.config

import com.conduit.chainservice.auth.AuthProperties
import com.utility.chainservice.AuthenticationProvider
import com.utility.chainservice.HttpAuthenticationProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UtilityServiceConfiguration(
    private val escrowBlockchainProperties: EscrowBlockchainProperties,
    private val authProperties: AuthProperties
) {

    @Bean
    fun rpcUrl(): String = escrowBlockchainProperties.rpcUrl

    @Bean
    fun relayerPrivateKey(): String = escrowBlockchainProperties.relayer.privateKey

    @Bean
    fun fallbackChainId(): Long = escrowBlockchainProperties.chainId

    @Bean
    fun gasPriceMultiplier(): Double = escrowBlockchainProperties.gas.priceMultiplier

    @Bean
    fun minimumGasPriceWei(): Long = escrowBlockchainProperties.gas.minimumGasPriceWei

    @Bean
    fun gasLimitsConfig(): Map<String, Long> = mapOf(
        "createContract" to escrowBlockchainProperties.gas.limitCreateContract,
        "depositFunds" to escrowBlockchainProperties.gas.limitDeposit,
        "raiseDispute" to escrowBlockchainProperties.gas.limitDispute,
        "claimFunds" to escrowBlockchainProperties.gas.limitClaim,
        "resolveDispute" to escrowBlockchainProperties.gas.limitResolve,
        "approveUSDC" to escrowBlockchainProperties.gas.limitApproveUSDC
    )

    // authenticationProvider bean is provided by blockchain-relay-utility
}