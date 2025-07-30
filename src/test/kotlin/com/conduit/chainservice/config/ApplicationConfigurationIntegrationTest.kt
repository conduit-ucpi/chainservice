package com.conduit.chainservice.config

import com.utility.chainservice.AuthProperties
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
    @EnableConfigurationProperties(EscrowProperties::class, AuthProperties::class)
    class TestConfig

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfig::class.java)

    @Test
    fun `should load configuration properties successfully with required environment variables`() {
        contextRunner
            .withPropertyValues(
                "escrow.usdcContractAddress=0x1234567890123456789012345678901234567890",
                "escrow.contractFactoryAddress=0x1234567890123456789012345678901234567890",
                "escrow.creatorFee=1000000",
                "escrow.gas.limitCreateContract=500000",
                "escrow.gas.limitDeposit=74161",
                "auth.userServiceUrl=http://localhost:8080",
                "auth.enabled=true"
            )
            .run { context ->
                // Check if we can get the beans by type
                assertNotNull(context.getBean(EscrowProperties::class.java))
                assertNotNull(context.getBean(AuthProperties::class.java))
                
                val escrowProps = context.getBean(EscrowProperties::class.java)
                val authProps = context.getBean(AuthProperties::class.java)
                
                assertEquals("0x1234567890123456789012345678901234567890", escrowProps.usdcContractAddress)
                assertEquals("0x1234567890123456789012345678901234567890", escrowProps.contractFactoryAddress)
                assertEquals(java.math.BigInteger.valueOf(1000000), escrowProps.creatorFee)
                assertEquals(500000L, escrowProps.gas.limitCreateContract)
                assertEquals(74161L, escrowProps.gas.limitDeposit)
                
                assertEquals("http://localhost:8080", authProps.userServiceUrl)
                assertTrue(authProps.enabled)
            }
    }

    @Test
    fun `should work with kebab-case property names from YAML`() {
        contextRunner
            .withPropertyValues(
                "escrow.usdc-contract-address=0x1234567890123456789012345678901234567890",
                "escrow.contract-factory-address=0x1234567890123456789012345678901234567890",
                "escrow.creator-fee=1000000",
                "escrow.gas.limit-create-contract=500000",
                "escrow.gas.limit-deposit=74161",
                "auth.user-service-url=http://localhost:8080",
                "auth.enabled=true"
            )
            .run { context ->
                val escrowProps = context.getBean(EscrowProperties::class.java)
                val authProps = context.getBean(AuthProperties::class.java)
                
                // Spring Boot should handle kebab-case to camelCase conversion
                assertEquals("0x1234567890123456789012345678901234567890", escrowProps.usdcContractAddress)
                assertEquals("0x1234567890123456789012345678901234567890", escrowProps.contractFactoryAddress)
                assertEquals("http://localhost:8080", authProps.userServiceUrl)
            }
    }

    @Test
    fun `should use default values when optional properties are not provided`() {
        contextRunner
            .withPropertyValues(
                "auth.userServiceUrl=http://localhost:8080"
                // Omitting optional properties to test defaults
            )
            .run { context ->
                val escrowProps = context.getBean(EscrowProperties::class.java)
                val authProps = context.getBean(AuthProperties::class.java)
                
                assertEquals(java.math.BigInteger.ZERO, escrowProps.creatorFee) // Default value
                assertEquals(500000L, escrowProps.gas.limitCreateContract) // Default value
                assertEquals(74161L, escrowProps.gas.limitDeposit) // Default value
                assertTrue(authProps.enabled) // Default value
            }
    }
}

