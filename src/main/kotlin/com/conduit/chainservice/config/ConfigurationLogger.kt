package com.conduit.chainservice.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ConfigurationLogger(
    private val blockchainProperties: BlockchainProperties,
    private val escrowProperties: EscrowProperties
) {
    private val logger = LoggerFactory.getLogger(ConfigurationLogger::class.java)
    
    @PostConstruct
    fun logConfiguration() {
        logger.info("Loaded blockchain configuration:")
        logger.info("  - RPC URL: ${blockchainProperties.rpcUrl}")
        logger.info("  - Chain ID: ${blockchainProperties.chainId}")
        logger.info("  - Relayer wallet address: ${blockchainProperties.relayer.walletAddress}")
        logger.info("  - Gas payer contract: ${blockchainProperties.relayer.gasPayerContractAddress}")
        // NEVER log the private key!
        logger.info("  - Private key: [REDACTED]")
        
        logger.info("Loaded blockchain.gas configuration:")
        logger.info("  - Price multiplier: ${blockchainProperties.gas.priceMultiplier}")
        logger.info("  - Minimum gas price (wei): ${blockchainProperties.gas.minimumGasPriceWei}")
        logger.info("  - Max gas cost (wei): ${blockchainProperties.gas.maxGasCostWei}")
        logger.info("  - Max gas limit: ${blockchainProperties.gas.maxGasLimit}")
        logger.info("  - Max gas price multiplier: ${blockchainProperties.gas.maxGasPriceMultiplier}")
        
        logger.info("Loaded escrow configuration:")
        logger.info("  - USDC contract: ${escrowProperties.usdcContractAddress}")
        logger.info("  - Factory contract: ${escrowProperties.contractFactoryAddress}")
        logger.info("  - Creator fee: ${escrowProperties.creatorFee}")
        logger.info("  - Gas limits:")
        logger.info("    - Create contract: ${escrowProperties.limitCreateContract}")
        logger.info("    - Deposit: ${escrowProperties.limitDeposit}")
        logger.info("    - Dispute: ${escrowProperties.limitDispute}")
        logger.info("    - Claim: ${escrowProperties.limitClaim}")
        logger.info("    - Resolve: ${escrowProperties.limitResolve}")
        logger.info("    - Approve USDC: ${escrowProperties.limitApproveUsdc}")
        logger.info("  - Gas multiplier: ${escrowProperties.gasMultiplier}")
    }
}