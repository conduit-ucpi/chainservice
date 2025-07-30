package com.conduit.chainservice.config

import com.utility.chainservice.AuthProperties
import com.utility.chainservice.BlockchainProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Integration test to validate that configuration properties load correctly
 * without requiring the full application context.
 */
class ApplicationConfigurationIntegrationTest {

    @TestConfiguration
    @EnableConfigurationProperties(BlockchainProperties::class, AuthProperties::class)
    class TestConfig

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfig::class.java)

    @Test
    fun `should load configuration properties successfully with required environment variables`() {
        contextRunner
            .withPropertyValues(
                "blockchain.rpcUrl=https://api.avax-test.network/ext/bc/C/rpc",
                "blockchain.chainId=43113",
                "blockchain.relayer.privateKey=0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef12",
                "blockchain.relayer.walletAddress=0x1234567890123456789012345678901234567890",
                "blockchain.gas.priceMultiplier=1.7",
                "blockchain.gas.minimumGasPriceWei=6",
                "auth.userServiceUrl=http://localhost:8080",
                "auth.enabled=true"
            )
            .run { context ->
                assertTrue(context.containsBean("blockchainProperties"))
                assertTrue(context.containsBean("authProperties"))
                
                val blockchainProps = context.getBean(BlockchainProperties::class.java)
                val authProps = context.getBean(AuthProperties::class.java)
                
                assertEquals("https://api.avax-test.network/ext/bc/C/rpc", blockchainProps.rpcUrl)
                assertEquals(43113L, blockchainProps.chainId)
                assertEquals("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef12", blockchainProps.relayer.privateKey)
                assertEquals("0x1234567890123456789012345678901234567890", blockchainProps.relayer.walletAddress)
                assertEquals(1.7, blockchainProps.gas.priceMultiplier)
                assertEquals(6L, blockchainProps.gas.minimumGasPriceWei)
                
                assertEquals("http://localhost:8080", authProps.userServiceUrl)
                assertTrue(authProps.enabled)
            }
    }

    @Test
    fun `should work with kebab-case property names from YAML`() {
        contextRunner
            .withPropertyValues(
                "blockchain.rpc-url=https://api.avax-test.network/ext/bc/C/rpc",
                "blockchain.chain-id=43113",
                "blockchain.relayer.private-key=0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef12",
                "blockchain.relayer.wallet-address=0x1234567890123456789012345678901234567890",
                "blockchain.gas.price-multiplier=1.7",
                "blockchain.gas.minimum-gas-price-wei=6",
                "auth.user-service-url=http://localhost:8080",
                "auth.enabled=true"
            )
            .run { context ->
                val blockchainProps = context.getBean(BlockchainProperties::class.java)
                val authProps = context.getBean(AuthProperties::class.java)
                
                // Spring Boot should handle kebab-case to camelCase conversion
                assertEquals("https://api.avax-test.network/ext/bc/C/rpc", blockchainProps.rpcUrl)
                assertEquals(43113L, blockchainProps.chainId)
                assertEquals("http://localhost:8080", authProps.userServiceUrl)
            }
    }

    @Test
    fun `should use default values when optional properties are not provided`() {
        contextRunner
            .withPropertyValues(
                "blockchain.rpcUrl=https://api.avax-test.network/ext/bc/C/rpc"
                // Omitting optional properties to test defaults
            )
            .run { context ->
                val blockchainProps = context.getBean(BlockchainProperties::class.java)
                val authProps = context.getBean(AuthProperties::class.java)
                
                assertEquals(43113L, blockchainProps.chainId) // Default value
                assertEquals(1.2, blockchainProps.gas.priceMultiplier) // Default value
                assertEquals(6L, blockchainProps.gas.minimumGasPriceWei) // Default value
                assertTrue(authProps.enabled) // Default value
            }
    }
}

