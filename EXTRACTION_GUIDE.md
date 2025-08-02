# Blockchain Relay Utility Extraction Guide

This guide explains how to extract the `com.utility.chainservice` package to its own repository and update your project to use it as a dependency.

## What Was Created

I've created a new directory `blockchain-relay-utility/` that contains:

```
blockchain-relay-utility/
├── README.md                                    # Complete documentation
├── build.gradle.kts                            # Gradle build file with publishing
├── gradlew                                      # Gradle wrapper (copied from original)
├── gradle/                                      # Gradle wrapper files
└── src/
    ├── main/
    │   ├── kotlin/com/utility/chainservice/    # All generic utility code
    │   └── resources/
    │       └── META-INF/spring.factories        # Spring Boot auto-configuration
    └── test/
        ├── kotlin/com/utility/chainservice/    # Test files
        └── resources/application-test.yml       # Test configuration
```

## Step-by-Step Extraction Process

### 1. Create New Git Repository

```bash
cd blockchain-relay-utility
git init
git add .
git commit -m "Initial commit: Blockchain Transaction Relay Utility v1.0.0"

# Create repository on GitHub, then:
git remote add origin https://github.com/yourusername/blockchain-relay-utility.git
git branch -M main
git push -u origin main
```

### 2. Publish to Maven Repository (Optional)

If you want to publish to Maven Central or a private repository:

```bash
# Update build.gradle.kts with your repository details
./gradlew publish
```

For local development, you can use:
```bash
./gradlew publishToMavenLocal
```

### 3. Update Original Project

#### Option A: Use as Local Dependency (Development)

In your original project's `build.gradle.kts`, add:

```kotlin
dependencies {
    // Add the utility as a local dependency
    implementation(project(":blockchain-relay-utility"))
    
    // ... other dependencies
}

// Add to settings.gradle.kts:
include(":blockchain-relay-utility")
project(":blockchain-relay-utility").projectDir = file("blockchain-relay-utility")
```

#### Option B: Use as Published Dependency (Production)

```kotlin
dependencies {
    implementation("com.utility:blockchain-relay-utility:1.0.0")
    // ... other dependencies
}
```

### 4. Remove Generic Code from Original Project

Once the utility is working as a dependency, you can remove:

```bash
# Remove the generic utility package
rm -rf src/main/kotlin/com/utility/

# Update imports in your escrow code to use the new dependency
# The code should work as-is since package names remain the same
```

### 5. Update Application Configuration

Update your `ChainServiceApplication.kt`:

```kotlin
@SpringBootApplication
@EnableConfigurationProperties(BlockchainProperties::class, AuthProperties::class)
@ComponentScan(basePackages = [
    "com.conduit.chainservice",
    "com.utility.chainservice"  // This will auto-discover the utility
])
class ChainServiceApplication
```

Or remove the explicit component scan since the utility now has auto-configuration:

```kotlin
@SpringBootApplication
@EnableConfigurationProperties(BlockchainProperties::class, AuthProperties::class)
class ChainServiceApplication
```

## Configuration

The utility uses standard Spring Boot configuration. Your existing `application.yml` will work:

```yaml
blockchain:
  rpc-url: "${RPC_URL}"
  relayer:
    private-key: "${RELAYER_PRIVATE_KEY}"
    wallet-address: "${RELAYER_WALLET_ADDRESS}"
  gas:
    price-multiplier: 1.7
    minimum-gas-price-wei: 6

auth:
  user-service-url: "${USER_SERVICE_URL}"
  enabled: true
```

## Benefits of Extraction

1. **Reusability**: Use the utility in multiple blockchain projects
2. **Maintainability**: Single source of truth for blockchain relay functionality
3. **Testability**: Utility can be tested independently
4. **Versioning**: Track utility changes separately from business logic
5. **Distribution**: Easy to share with other teams/projects

## Example: Using in a New Project

Create a new Spring Boot project and:

1. Add dependency:
```kotlin
implementation("com.utility:blockchain-relay-utility:1.0.0")
```

2. Create your plugin:
```kotlin
@Component
class NFTServicePlugin : BlockchainServicePlugin {
    override fun getPluginName(): String = "nft-service"
    override fun getApiPrefix(): String = "/api/nft"
    // ... implement other methods
}
```

3. Create your controller:
```kotlin
@RestController
@RequestMapping("/api/nft")
class NFTController(private val nftServicePlugin: NFTServicePlugin) {
    // Your NFT-specific endpoints
}
```

4. Configure application:
```yaml
blockchain:
  rpc-url: "your-rpc-url"
  relayer:
    private-key: "your-key"
    wallet-address: "your-address"
```

That's it! The utility handles all blockchain interaction, gas management, and authentication.

## Testing the Extraction

1. Build the utility:
```bash
cd blockchain-relay-utility
./gradlew build
```

2. Test your original project still works:
```bash
cd ../  # back to original project
./gradlew test
```

Both should pass, confirming the extraction was successful.

## Troubleshooting

**Import errors**: Make sure package names remain `com.utility.chainservice`
**Bean conflicts**: The utility uses `@ConditionalOnMissingBean` to avoid conflicts
**Configuration issues**: Check that your `application.yml` has the required properties

## Next Steps

1. Extract the utility to its own repository
2. Set up CI/CD for the utility repository
3. Create versioning strategy (semantic versioning recommended)
4. Document plugin development guidelines
5. Create example projects showing different plugin implementations