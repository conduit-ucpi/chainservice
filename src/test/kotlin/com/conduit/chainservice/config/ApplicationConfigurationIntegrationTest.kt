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
                "escrow.contractFactoryAddress=0x1234567890123456789012345678901234567890",
                "escrow.minCreatorFee=1000000",
                "escrow.limitCreateContract=273000",
                "escrow.limitDeposit=86500",
                "escrow.limitDispute=8800",
                "escrow.limitClaim=40800",
                "escrow.limitResolve=68000",
                "escrow.limitApproveToken=60000",
                "escrow.gasMultiplier=1.11",
                "auth.userServiceUrl=http://localhost:8080",
                "auth.enabled=true"
            )
            .run { context ->
                // Check if we can get the beans by type
                assertNotNull(context.getBean(EscrowProperties::class.java))
                assertNotNull(context.getBean(AuthProperties::class.java))

                val escrowProps = context.getBean(EscrowProperties::class.java)
                val authProps = context.getBean(AuthProperties::class.java)

                assertEquals("0x1234567890123456789012345678901234567890", escrowProps.contractFactoryAddress)
                assertEquals(java.math.BigInteger.valueOf(1000000), escrowProps.minCreatorFee)
                assertEquals(273000L, escrowProps.limitCreateContract)
                assertEquals(86500L, escrowProps.limitDeposit)
                assertEquals(8800L, escrowProps.limitDispute)
                assertEquals(40800L, escrowProps.limitClaim)
                assertEquals(68000L, escrowProps.limitResolve)
                assertEquals(60000L, escrowProps.limitApproveToken)
                assertEquals(1.11, escrowProps.gasMultiplier, 0.001)
                
                assertEquals("http://localhost:8080", authProps.userServiceUrl)
                assertTrue(authProps.enabled)
            }
    }

    @Test
    fun `should work with kebab-case property names from YAML`() {
        contextRunner
            .withPropertyValues(
                "escrow.contract-factory-address=0x1234567890123456789012345678901234567890",
                "escrow.min-creator-fee=1000000",
                "escrow.limit-create-contract=500000",
                "escrow.limit-deposit=74161",
                "escrow.gas-multiplier=1.2",
                "auth.user-service-url=http://localhost:8080",
                "auth.enabled=true"
            )
            .run { context ->
                val escrowProps = context.getBean(EscrowProperties::class.java)
                val authProps = context.getBean(AuthProperties::class.java)

                // Spring Boot should handle kebab-case to camelCase conversion
                assertEquals("0x1234567890123456789012345678901234567890", escrowProps.contractFactoryAddress)
                assertEquals("http://localhost:8080", authProps.userServiceUrl)
            }
    }

    @Test
    fun `should use application yml default values when env vars are not provided`() {
        contextRunner
            .withPropertyValues(
                // Simulating application.yml defaults (no env vars provided)
                "escrow.limitCreateContract=273000",
                "escrow.limitDeposit=86500", 
                "escrow.limitDispute=8800",
                "escrow.limitClaim=40800",
                "escrow.limitResolve=68000",
                "escrow.limitApproveToken=60000",
                "escrow.gasMultiplier=1.11",
                "auth.userServiceUrl=http://localhost:8080"
            )
            .run { context ->
                val escrowProps = context.getBean(EscrowProperties::class.java)
                val authProps = context.getBean(AuthProperties::class.java)
                
                assertEquals(java.math.BigInteger.ZERO, escrowProps.minCreatorFee) // Default value
                assertEquals(273000L, escrowProps.limitCreateContract) // Default value
                assertEquals(86500L, escrowProps.limitDeposit) // Default value
                assertEquals(8800L, escrowProps.limitDispute) // Default value
                assertEquals(40800L, escrowProps.limitClaim) // Default value
                assertEquals(68000L, escrowProps.limitResolve) // Default value
                assertEquals(60000L, escrowProps.limitApproveToken) // Default value
                assertEquals(1.11, escrowProps.gasMultiplier, 0.001) // Default value
                assertTrue(authProps.enabled) // Default value
            }
    }
}

