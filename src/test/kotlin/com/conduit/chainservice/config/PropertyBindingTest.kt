package com.conduit.chainservice.config

import com.utility.chainservice.AuthProperties
import com.utility.chainservice.BlockchainProperties
import com.utility.chainservice.GasProperties
import com.utility.chainservice.RelayerProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySource
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertySource

/**
 * Tests to validate that YAML property names correctly bind to Kotlin property classes.
 * This prevents runtime failures due to kebab-case vs camelCase mismatches.
 */
class PropertyBindingTest {

    @Test
    fun `BlockchainProperties should bind correctly from YAML-style properties`() {
        val properties = mapOf(
            "blockchain.rpcUrl" to "https://api.avax-test.network/ext/bc/C/rpc",
            "blockchain.chainId" to "43113",
            "blockchain.relayer.privateKey" to "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef12",
            "blockchain.relayer.walletAddress" to "0x1234567890123456789012345678901234567890",
            "blockchain.gas.priceMultiplier" to "1.7",
            "blockchain.gas.minimumGasPriceWei" to "6"
        )

        val source = MapConfigurationPropertySource(properties)
        val binder = Binder(source)
        
        val blockchainProps = binder.bind("blockchain", BlockchainProperties::class.java).get()
        
        assertEquals("https://api.avax-test.network/ext/bc/C/rpc", blockchainProps.rpcUrl)
        assertEquals(43113L, blockchainProps.chainId)
        assertEquals("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef12", blockchainProps.relayer.privateKey)
        assertEquals("0x1234567890123456789012345678901234567890", blockchainProps.relayer.walletAddress)
        assertEquals(1.7, blockchainProps.gas.priceMultiplier)
        assertEquals(6L, blockchainProps.gas.minimumGasPriceWei)
    }

    @Test
    fun `AuthProperties should bind correctly from YAML-style properties`() {
        val properties = mapOf(
            "auth.userServiceUrl" to "http://localhost:8080/api/users",
            "auth.enabled" to "true"
        )

        val source = MapConfigurationPropertySource(properties)
        val binder = Binder(source)
        
        val authProps = binder.bind("auth", AuthProperties::class.java).get()
        
        assertEquals("http://localhost:8080/api/users", authProps.userServiceUrl)
        assertTrue(authProps.enabled)
    }

    @Test
    fun `should handle both kebab-case and camelCase property names correctly`() {
        // Spring Boot actually handles kebab-case to camelCase conversion automatically
        val kebabCaseProperties = mapOf(
            "blockchain.rpc-url" to "https://api.avax-test.network/ext/bc/C/rpc",
            "blockchain.chain-id" to "43113",
            "blockchain.relayer.private-key" to "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef12",
            "blockchain.relayer.wallet-address" to "0x1234567890123456789012345678901234567890",
            "blockchain.gas.price-multiplier" to "1.7",
            "blockchain.gas.minimum-gas-price-wei" to "6"
        )

        val source = MapConfigurationPropertySource(kebabCaseProperties)
        val binder = Binder(source)
        
        val blockchainProps = binder.bind("blockchain", BlockchainProperties::class.java).get()
        
        // Spring Boot should automatically convert kebab-case to camelCase
        assertEquals("https://api.avax-test.network/ext/bc/C/rpc", blockchainProps.rpcUrl)
        assertEquals(43113L, blockchainProps.chainId)
        assertEquals("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef12", blockchainProps.relayer.privateKey)
        assertEquals("0x1234567890123456789012345678901234567890", blockchainProps.relayer.walletAddress)
        assertEquals(1.7, blockchainProps.gas.priceMultiplier)
        assertEquals(6L, blockchainProps.gas.minimumGasPriceWei)
    }

    @Test
    fun `should validate required property names match between YAML keys and Kotlin properties`() {
        // Define the expected YAML property names that should work
        val expectedYamlProperties = setOf(
            "blockchain.rpcUrl",
            "blockchain.chainId", 
            "blockchain.relayer.privateKey",
            "blockchain.relayer.walletAddress",
            "blockchain.gas.priceMultiplier",
            "blockchain.gas.minimumGasPriceWei",
            "auth.userServiceUrl",
            "auth.enabled"
        )
        
        // Test that each expected property can be bound successfully
        expectedYamlProperties.forEach { propertyName ->
            val testValue = when {
                propertyName.contains("chainId") -> "43113"
                propertyName.contains("enabled") -> "true"
                propertyName.contains("priceMultiplier") -> "1.7"
                propertyName.contains("minimumGasPriceWei") -> "6"
                else -> "test-value"
            }
            
            val properties = mapOf(propertyName to testValue)
            val source = MapConfigurationPropertySource(properties)
            val binder = Binder(source)
            
            when {
                propertyName.startsWith("blockchain.") -> {
                    val result = binder.bind("blockchain", BlockchainProperties::class.java)
                    assertTrue(result.isBound, "Property $propertyName should bind successfully")
                }
                propertyName.startsWith("auth.") -> {
                    val result = binder.bind("auth", AuthProperties::class.java)
                    assertTrue(result.isBound, "Property $propertyName should bind successfully")
                }
            }
        }
    }
}