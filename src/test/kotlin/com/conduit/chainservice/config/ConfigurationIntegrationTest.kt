package com.conduit.chainservice.config

import com.utility.chainservice.AuthProperties
import com.utility.chainservice.BlockchainProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * Integration test to verify that all configuration properties bind correctly
 * with kebab-case YAML properties, matching the production application.yml format.
 */
class ConfigurationIntegrationTest {

    @TestConfiguration
    @EnableConfigurationProperties(EscrowProperties::class, BlockchainProperties::class, AuthProperties::class)
    class TestConfig

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfig::class.java)

    @Test
    fun `should bind all properties correctly with kebab-case YAML format matching production`() {
        contextRunner
            .withPropertyValues(
                // Blockchain properties (kebab-case matching production application.yml)
                "blockchain.rpc-url=https://api.avax-test.network/ext/bc/C/rpc",
                "blockchain.chain-id=43113",
                "blockchain.relayer.private-key=0x1234567890123456789012345678901234567890123456789012345678901234",
                "blockchain.relayer.wallet-address=0x1234567890123456789012345678901234567890",
                "blockchain.gas.price-multiplier=1.7",
                "blockchain.gas.minimum-gas-price-wei=6",
                
                // Escrow properties (kebab-case)
                "escrow.usdc-contract-address=0x5425890298aed601595a70AB815c96711a31Bc65",
                "escrow.contract-factory-address=0x1234567890123456789012345678901234567890",
                "escrow.creator-fee=1000000",
                "escrow.gas.limit-create-contract=500000",
                "escrow.gas.limit-deposit=74161",
                "escrow.gas.limit-dispute=9633",
                "escrow.gas.limit-claim=51702",
                "escrow.gas.limit-resolve=200000",
                "escrow.gas.limit-approve-usdc=60000",
                
                // Auth properties (kebab-case)
                "auth.user-service-url=http://localhost:8080",
                "auth.enabled=true"
            )
            .run { context ->
                assertDoesNotThrow { 
                    // Verify all beans can be created without conflicts
                    val blockchainProps = context.getBean(BlockchainProperties::class.java)
                    val escrowProps = context.getBean(EscrowProperties::class.java)
                    val authProps = context.getBean(AuthProperties::class.java)
                    
                    // Verify blockchain properties
                    assertEquals("https://api.avax-test.network/ext/bc/C/rpc", blockchainProps.rpcUrl)
                    assertEquals(43113L, blockchainProps.chainId)
                    assertEquals("0x1234567890123456789012345678901234567890123456789012345678901234", blockchainProps.relayer.privateKey)
                    assertEquals("0x1234567890123456789012345678901234567890", blockchainProps.relayer.walletAddress)
                    assertEquals(1.7, blockchainProps.gas.priceMultiplier)
                    assertEquals(6L, blockchainProps.gas.minimumGasPriceWei)
                    
                    // Verify escrow properties
                    assertEquals("0x5425890298aed601595a70AB815c96711a31Bc65", escrowProps.usdcContractAddress)
                    assertEquals("0x1234567890123456789012345678901234567890", escrowProps.contractFactoryAddress)
                    assertEquals(java.math.BigInteger.valueOf(1000000), escrowProps.creatorFee)
                    assertEquals(500000L, escrowProps.gas.limitCreateContract)
                    assertEquals(74161L, escrowProps.gas.limitDeposit)
                    
                    // Verify auth properties
                    assertEquals("http://localhost:8080", authProps.userServiceUrl)
                    assertTrue(authProps.enabled)
                }
            }
    }
}