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
                "blockchain.relayer.wallet-address=0x1234567890123456789012345678901234567890",
                "blockchain.gas.price-multiplier=1.7",
                "blockchain.gas.minimum-gas-price-wei=6",
                
                // Escrow properties (kebab-case - flattened structure)
                "escrow.usdc-contract-address=0x5425890298aed601595a70AB815c96711a31Bc65",
                "escrow.contract-factory-address=0x1234567890123456789012345678901234567890",
                "escrow.creator-fee=1000000",
                "escrow.limit-create-contract=273000",
                "escrow.limit-deposit=86500",
                "escrow.limit-dispute=8800",
                "escrow.limit-claim=40800",
                "escrow.limit-resolve=68000",
                "escrow.limit-approve-usdc=60000",
                "escrow.gas-multiplier=1.11",
                
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
                    assertEquals("0x1234567890123456789012345678901234567890", blockchainProps.relayer.walletAddress)
                    assertEquals(1.7, blockchainProps.gas.priceMultiplier)
                    assertEquals(6L, blockchainProps.gas.minimumGasPriceWei)
                    
                    // Verify escrow properties
                    assertEquals("0x5425890298aed601595a70AB815c96711a31Bc65", escrowProps.usdcContractAddress)
                    assertEquals("0x1234567890123456789012345678901234567890", escrowProps.contractFactoryAddress)
                    assertEquals(java.math.BigInteger.valueOf(1000000), escrowProps.creatorFee)
                    assertEquals(273000L, escrowProps.limitCreateContract)
                    assertEquals(86500L, escrowProps.limitDeposit)
                    assertEquals(8800L, escrowProps.limitDispute)
                    assertEquals(40800L, escrowProps.limitClaim)
                    assertEquals(68000L, escrowProps.limitResolve)
                    assertEquals(60000L, escrowProps.limitApproveUsdc)
                    assertEquals(1.11, escrowProps.gasMultiplier, 0.001)
                    
                    // Verify auth properties
                    assertEquals("http://localhost:8080", authProps.userServiceUrl)
                    assertTrue(authProps.enabled)
                }
            }
    }
}