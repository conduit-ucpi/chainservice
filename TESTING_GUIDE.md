# Configuration Testing Guide

This document outlines the tests that should be implemented to prevent the configuration issues that were resolved in the chainservice integration with blockchain-relay-utility v0.0.1.

## Background

The original issue was caused by duplicate bean definitions and configuration property conflicts between the chainservice and the blockchain-relay-utility library. Both were trying to:

1. Create beans with the same names (`web3j`, `chainId`, `relayerCredentials`)
2. Bind to the same configuration prefix (`blockchain`)
3. Define conflicting `@ConfigurationProperties` classes

## Fixed Architecture

- **chainservice**: Uses `EscrowBlockchainProperties` with `@ConfigurationProperties(prefix = "blockchain")`
- **blockchain-relay-utility**: Uses its own `BlockchainProperties` with the same prefix
- **Bean naming**: Escrow-specific beans are prefixed (e.g., `escrowRelayerCredentials`)
- **Shared configuration**: Both read from the same `blockchain.*` properties in `application.yml`

## Recommended Tests

### 1. Context Loading Tests

Test that the Spring Boot application context loads without bean conflicts:

```kotlin
@SpringBootTest
@TestPropertySource(properties = [
    "blockchain.rpc-url=https://test-rpc.example.com",
    "blockchain.chain-id=43113",
    // ... other required properties
])
class ContextLoadingTest {
    @Test
    fun `application context loads successfully`() {
        // If this test passes, no bean conflicts occurred
        assertTrue(true)
    }
}
```

### 2. Configuration Property Binding Tests

Verify that configuration properties are properly loaded:

```kotlin
@SpringBootTest
class ConfigurationBindingTest {
    @Autowired
    private lateinit var escrowProperties: EscrowBlockchainProperties
    
    @Test
    fun `blockchain properties are bound correctly`() {
        assertEquals("https://test-rpc.example.com", escrowProperties.rpcUrl)
        assertEquals(43113L, escrowProperties.chainId)
        // ... test other properties
    }
}
```

### 3. Bean Uniqueness Tests

Ensure no duplicate or conflicting beans exist:

```kotlin
@SpringBootTest
class BeanUniquenessTest {
    @Autowired
    private lateinit var applicationContext: ApplicationContext
    
    @Test
    fun `only one Web3j bean exists`() {
        val web3jBeans = applicationContext.getBeansOfType(Web3j::class.java)
        assertEquals(1, web3jBeans.size)
    }
    
    @Test
    fun `utility and escrow beans coexist`() {
        assertTrue(applicationContext.containsBean("relayerCredentialsBean")) // from utility
        assertTrue(applicationContext.containsBean("escrowRelayerCredentials")) // from escrow
    }
}
```

### 4. Configuration Passthrough Tests

Verify that both services receive the same configuration values:

```kotlin
@SpringBootTest
class ConfigurationPassthroughTest {
    @Test
    fun `utility library reads same configuration as escrow service`() {
        // Test that both the utility library and escrow service
        // are using the same RPC URL, chain ID, etc.
    }
}
```

### 5. Error Detection Tests

Tests that would catch the original error if it resurfaces:

```kotlin
// This test would simulate adding conflicting bean definitions
class ConflictDetectionTest {
    @Test
    fun `duplicate BlockchainProperties would cause failure`() {
        // Test that demonstrates what would happen if someone
        // accidentally adds a conflicting @ConfigurationProperties class
    }
}
```

## Test Execution

Run these tests as part of the CI/CD pipeline:

```bash
./gradlew test --tests="*ConfigurationTest" --tests="*ContextLoadingTest"
```

## Preventing Future Issues

### Code Review Checklist

When reviewing changes to configuration:

- [ ] No new `@ConfigurationProperties` classes with `prefix = "blockchain"`
- [ ] No new beans with names: `web3j`, `chainId`, `relayerCredentials`, `gasProvider`
- [ ] Any new blockchain-related beans are prefixed with `escrow` or similar
- [ ] Tests validate that Spring context loads successfully

### Development Guidelines

1. **Bean Naming**: Always prefix escrow-specific beans with `escrow`
2. **Configuration Classes**: Use `EscrowBlockchainProperties` for escrow-specific config
3. **Testing**: Add context loading tests for any configuration changes
4. **Documentation**: Update this guide when adding new configuration

## Monitoring

### Build Pipeline Validation

The CI/CD should fail if:
- Spring context fails to load
- Duplicate bean names are detected
- Configuration properties aren't properly bound

### Runtime Monitoring

Consider adding health checks that verify:
- All required beans are present
- Configuration values are loaded correctly
- No bean conflicts exist at startup

## Troubleshooting

If similar configuration issues arise:

1. Check for duplicate `@ConfigurationProperties` classes
2. Verify bean names don't conflict between services
3. Ensure `@ComponentScan` isn't creating conflicts
4. Review Spring Boot auto-configuration logs for errors
5. Use `@ConditionalOnMissingBean` annotations where appropriate

## Example Error Messages

Watch for these error patterns:

```
Error processing condition on com.utility.chainservice.UtilityAutoConfiguration.chainIdBean
BeanDefinitionStoreException: Failed to parse configuration class
NoUniqueBeanDefinitionException: No qualifying bean of type 'Web3j' available
```

These typically indicate configuration conflicts between the chainservice and blockchain-relay-utility.