# CLAUDE.md

## CRITICAL: Repository Boundaries

This Claude agent is **STRICTLY LIMITED** to the chainservice directory (`/Users/charliep/conduit-ucpi/chainservice`). 

### Agent Restrictions
- **NEVER navigate to or modify files outside this directory**
- **NEVER access parent directories** (../)
- **NEVER modify files in sibling services**
- **ONLY work within**: `/Users/charliep/conduit-ucpi/chainservice`

### Working Directory
Your working directory is: `/Users/charliep/conduit-ucpi/chainservice`
All file operations must be relative to this directory or use absolute paths within it.

### Integration Guidelines
When changes require updates to other services:
1. Document the required changes clearly
2. Return to the parent orchestrator agent
3. Let the parent agent delegate to the appropriate service agent

If asked to modify files outside this directory, respond:
"I cannot modify files outside the chainservice directory. Please use the parent orchestrator agent to coordinate changes across multiple services."

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

**Build the project:**
```bash
./gradlew build
```

**Run the application:**
```bash
./gradlew bootRun
```

**Run tests:**
```bash
./gradlew test
```

**Run tests with coverage:**
```bash
./gradlew test jacocoTestReport
```

**Build JAR and run directly:**
```bash
./gradlew build
java -jar build/libs/chainservice-1.0.0.jar
```

## Architecture Overview

This is a Kotlin/Spring Boot microservice that provides a **generic blockchain transaction relay utility** with a **pluggable escrow service**. The service pays gas fees for user-signed transactions and provides contract query functionality on the Avalanche network.

### New Architecture (Post-Refactoring)

**Generic Utility (`com.utility.chainservice`)**:
- `BlockchainRelayService` - Core blockchain transaction relaying and gas management
- `AuthenticationProvider` - Generic interface for user authentication
- `HttpAuthenticationProvider` - HTTP-based authentication implementation
- `RelayController` - Generic utility endpoints (`/api/relay/*`)
- Plugin system supporting different business logic implementations

**Escrow Plugin (`com.conduit.chainservice.escrow`)**:
- `EscrowServicePlugin` - Implements plugin interface for escrow functionality
- `EscrowTransactionService` - Escrow-specific business logic using generic services
- `EscrowController` - Escrow API endpoints (`/api/chain/*`)
- All existing escrow functionality preserved through plugin architecture

**Transaction Flow:**
- Users sign transactions client-side with their private keys
- This service receives the signed transaction, validates it, and relays it to the blockchain
- The generic utility handles gas transfer and transaction forwarding
- The escrow plugin provides business-specific logic (contract creation, disputes, etc.)
- The service's relayer wallet pays the gas fees

**Key Services:**
- `BlockchainRelayService` - Generic blockchain interaction and transaction relaying
- `EscrowTransactionService` - Escrow-specific operations using generic services
- `TransactionRelayService` - Legacy service that delegates to new architecture
- `ContractQueryService` - Reads contract state and history from blockchain
- `EventParsingService` - Parses blockchain events for contract status updates

**Configuration:**
- Environment-based configuration through `application.yml`
- Blockchain properties (RPC URL, contract addresses, gas settings)
- Authentication integration with external user service
- Plugin auto-discovery and initialization

### Package Structure

```
src/main/kotlin/
├── com/utility/chainservice/                   # Generic utility (future separate repo)
│   ├── BlockchainRelayService.kt              # Core transaction relay logic
│   ├── AuthenticationProvider.kt              # Authentication interface
│   ├── HttpAuthenticationProvider.kt          # HTTP-based auth implementation
│   ├── BlockchainConfiguration.kt             # Web3j and blockchain setup
│   ├── RelayController.kt                     # Generic utility endpoints
│   ├── models/
│   │   ├── TransactionResult.kt               # Common result models
│   │   ├── AuthenticationModels.kt            # Auth request/response models
│   │   └── RelayModels.kt                     # Generic relay models
│   └── plugin/
│       ├── BlockchainServicePlugin.kt         # Plugin interface
│       └── PluginConfiguration.kt             # Plugin config system
└── com/conduit/chainservice/                  # Project-specific (escrow)
    ├── ChainServiceApplication.kt             # Main application (updated)
    ├── escrow/                                # Escrow plugin
    │   ├── EscrowServicePlugin.kt             # Plugin implementation
    │   ├── EscrowTransactionService.kt        # Escrow business logic
    │   ├── EscrowController.kt                # Escrow API endpoints
    │   └── models/
    │       ├── EscrowModels.kt                # Escrow-specific models
    │       └── ContractModels.kt              # Contract data models
    ├── auth/                                  # Authentication & security
    │   ├── AuthProperties.kt
    │   ├── AuthenticationFilter.kt
    │   ├── SecurityConfig.kt
    │   └── UserServiceClient.kt
    ├── config/                                # Configuration classes
    │   ├── OpenApiConfig.kt                   # Swagger/OpenAPI setup
    │   ├── Web3Config.kt                      # Web3j & blockchain configuration
    │   └── UtilityServiceConfiguration.kt     # Bridge to generic utility
    ├── controller/                            # Legacy compatibility
    │   ├── ChainController.kt                 # Legacy endpoints (still functional)
    │   └── HealthController.kt                # Health checks and monitoring
    ├── exception/                             # Error handling
    │   └── GlobalExceptionHandler.kt
    ├── model/                                 # Legacy models
    │   ├── ApiModels.kt                       # Request/response DTOs
    │   └── ContractModels.kt                  # Domain models
    └── service/                               # Business logic services
        ├── ContractQueryService.kt            # Contract state queries
        ├── EventParsingService.kt             # Blockchain event processing
        ├── TransactionRelayService.kt         # Legacy service (delegates to new)
        └── ContractServiceClient.kt           # External service integration
```

## Required Environment Variables

The application requires these environment variables to start:
- `RPC_URL` - Avalanche testnet RPC endpoint
- `USDC_CONTRACT_ADDRESS` - USDC token contract address
- `CONTRACT_FACTORY_ADDRESS` - Escrow contract factory address
- `RELAYER_PRIVATE_KEY` - Private key for gas-paying wallet (must be 0x-prefixed hex)
- `RELAYER_WALLET_ADDRESS` - Address of the relayer wallet
- `CREATOR_FEE_USDC_X_1M` - Fee amount in USDC multiplied by 1 million (e.g., 1000000 = 1 USDC) to be paid to the creator on contract deposit (special case: 0.001 USDC contracts always have 0 creator fee)
- `USER_SERVICE_URL` - URL for user authentication service
- `CONTRACT_SERVICE_URL` - URL for contract service (default: http://localhost:8080)
- `SERVER_PORT` - Port for the service (default: 8978)
- `MIN_GAS_WEI` - Minimum gas price in wei (default: 6)

## Key Technical Details

**Gas Management:**
- Configurable gas limits per operation type (create, dispute, claim, etc.)
- Gas price multiplier (default 1.2x) for reliable transaction inclusion
- Health checks monitor relayer wallet balance

**Authentication:**
- **ALL authentication verification is delegated to web3userservice**
- No local authentication logic - this service only forwards auth headers
- Integration with external user service for request validation
- Transaction signature verification to ensure user authorization
- Admin-only endpoints for dispute resolution (validated by web3userservice)

**API Documentation:**
- Swagger UI available at `/swagger-ui.html`
- OpenAPI spec at `/api-docs`
- Health endpoints at `/actuator/health` and `/actuator/info`
- Generic utility endpoints at `/api/relay/*`
- Escrow plugin endpoints at `/api/chain/*` (unchanged from original)

**Plugin System:**
- Automatic plugin discovery and initialization
- Clean separation between generic blockchain functionality and business logic
- Easy to add new plugins for different use cases (NFT, DEX, etc.)
- Each plugin defines its own API endpoints and business rules

**Blockchain Integration:**
- Uses Web3j library for Ethereum-compatible blockchain interaction
- Generic gas management and transaction relaying
- Escrow plugin supports contract creation, fund operations, and dispute management
- Event parsing for contract status tracking
- Transaction receipt polling with configurable timeouts

**Testing Requirements:**
- Uses Mockito for testing, with standalone setup
- **CRITICAL**: A coding task is NOT complete until tests are written and pass
- Must run `./gradlew build` successfully after any code changes
- All new features must include unit tests
- Integration tests required for blockchain operations
- Test coverage should be maintained above 80%

**Git Commit Requirements:**
- **All git commit messages must be useful and descriptive**
- Explain what was changed and why, not just what files were touched
- Use present tense and imperative mood ("Add validation" not "Added validation")
- Include context about the business impact when relevant
- Avoid generic messages like "fix", "update", or "changes"

## Inter-Service Communication Standards

### DateTime Format
- **ALL datetime communication between services MUST use Unix timestamp format (seconds since epoch)**
- **Examples**: `expiryTimestamp: 1735689600`, `createdAt: 1705318200`
- **No exceptions**: ISO strings, formatted dates, or milliseconds

### Currency Format  
- **ALL currency amounts between services MUST be in microUSDC**
- **microUSDC = USDC × 1,000,000** (6 decimal places)
- **Examples**: $1.50 USDC = 1500000 microUSDC
- **Storage**: Use appropriate numeric types (Long for large amounts, Double for calculations)

### API Design Philosophy
- **NEVER make fields nullable for backward compatibility**
- **Fail early, not accept bad data** - let things break rather than silently accept incomplete requests
- **Required fields must be required** - use proper validation annotations
