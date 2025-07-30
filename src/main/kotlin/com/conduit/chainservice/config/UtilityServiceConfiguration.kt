package com.conduit.chainservice.config

import com.conduit.chainservice.auth.AuthProperties
import com.utility.chainservice.AuthenticationProvider
import com.utility.chainservice.HttpAuthenticationProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UtilityServiceConfiguration(
    private val blockchainProperties: BlockchainProperties,
    private val authProperties: AuthProperties
) {

    @Bean
    fun rpcUrl(): String = blockchainProperties.rpcUrl

    @Bean
    fun relayerPrivateKey(): String = blockchainProperties.relayer.privateKey

    @Bean
    fun fallbackChainId(): Long = blockchainProperties.chainId

    @Bean
    fun gasPriceMultiplier(): Double = blockchainProperties.gas.priceMultiplier

    @Bean
    fun minimumGasPriceWei(): Long = blockchainProperties.gas.minimumGasPriceWei

    @Bean
    fun gasLimitsConfig(): Map<String, Long> = mapOf(
        "createContract" to blockchainProperties.gas.limitCreateContract,
        "depositFunds" to blockchainProperties.gas.limitDeposit,
        "raiseDispute" to blockchainProperties.gas.limitDispute,
        "claimFunds" to blockchainProperties.gas.limitClaim,
        "resolveDispute" to blockchainProperties.gas.limitResolve,
        "approveUSDC" to blockchainProperties.gas.limitApproveUSDC
    )

    @Bean
    fun authenticationProvider(): AuthenticationProvider {
        return HttpAuthenticationProvider(
            userServiceUrl = authProperties.userServiceUrl,
            enabled = authProperties.enabled
        )
    }
}