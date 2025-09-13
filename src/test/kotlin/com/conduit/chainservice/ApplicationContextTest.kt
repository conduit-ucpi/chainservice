package com.conduit.chainservice

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * Integration test that loads the full Spring Boot application context.
 * This test will catch @ConditionalOnMissingBean bean type deduction issues
 * and other bean creation problems that unit tests might miss.
 */
@SpringBootTest(classes = [ChainServiceApplication::class])
@TestPropertySource(properties = [
    // Required blockchain properties
    "blockchain.rpc-url=https://api.avax-test.network/ext/bc/C/rpc",
    "blockchain.chain-id=43113",
    "blockchain.relayer.private-key=0x1234567890123456789012345678901234567890123456789012345678901234",
    "blockchain.relayer.wallet-address=0x1234567890123456789012345678901234567890",
    "blockchain.gas.price-multiplier=1.7",
    "blockchain.gas.minimum-gas-price-wei=6",
    
    // Required escrow properties  
    "escrow.usdc-contract-address=0x5425890298aed601595a70AB815c96711a31Bc65",
    "escrow.contract-factory-address=0x1234567890123456789012345678901234567890",
    "escrow.min-creator-fee=1000000",
    
    // Auth properties
    "auth.user-service-url=http://localhost:8080",
    "auth.enabled=false", // Disable auth for testing
    
    // Contract service
    "contract-service.enabled=false" // Disable for testing
])
class ApplicationContextTest {

    @Test
    fun `application context should load successfully without bean creation errors`() {
        // If this test passes, it means:
        // 1. All @ConditionalOnMissingBean annotations work correctly
        // 2. No bean type deduction failures
        // 3. No circular dependencies
        // 4. All required properties are bound correctly
        // 5. All auto-configuration classes load properly
        
        // The test passes simply by successfully loading the Spring context
        // Any @ConditionalOnMissingBean issues would cause context startup to fail
    }
}