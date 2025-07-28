# CLAUDE.md

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

This is a Kotlin/Spring Boot microservice that acts as a blockchain transaction relayer for an escrow system. The service pays gas fees for user-signed transactions and provides contract query functionality on the Avalanche network.

### Core Components

**Transaction Flow:**
- Users sign transactions client-side with their private keys
- This service receives the signed transaction, validates it, and relays it to the blockchain
- The service's relayer wallet pays the gas fees
- Supports escrow contract creation, dispute raising/resolution, and fund claiming

**Key Services:**
- `TransactionRelayService` - Core blockchain interaction and transaction relaying
- `ContractQueryService` - Reads contract state and history from blockchain
- `EventParsingService` - Parses blockchain events for contract status updates

**Configuration:**
- Environment-based configuration through `application.yml`
- Blockchain properties (RPC URL, contract addresses, gas settings)
- Authentication integration with external user service

### Package Structure

```
src/main/kotlin/com/conduit/chainservice/
├── ChainServiceApplication.kt           # Main Spring Boot application
├── auth/                                # Authentication & security
│   ├── AuthProperties.kt
│   ├── AuthenticationFilter.kt
│   ├── SecurityConfig.kt
│   └── UserServiceClient.kt
├── config/                              # Configuration classes
│   ├── OpenApiConfig.kt                 # Swagger/OpenAPI setup
│   └── Web3Config.kt                    # Web3j & blockchain configuration
├── controller/                          # REST API endpoints
│   ├── ChainController.kt               # Main blockchain operations API
│   └── HealthController.kt              # Health checks and monitoring
├── exception/                           # Error handling
│   └── GlobalExceptionHandler.kt
├── model/                               # Data models
│   ├── ApiModels.kt                     # Request/response DTOs
│   └── ContractModels.kt                # Domain models
└── service/                             # Business logic
    ├── ContractQueryService.kt          # Contract state queries
    ├── EventParsingService.kt           # Blockchain event processing
    └── TransactionRelayService.kt       # Core transaction relaying
```

## Required Environment Variables

The application requires these environment variables to start:
- `RPC_URL` - Avalanche testnet RPC endpoint
- `USDC_CONTRACT_ADDRESS` - USDC token contract address
- `CONTRACT_FACTORY_ADDRESS` - Escrow contract factory address
- `TRUSTED_FORWARDER_ADDRESS` - MinimalForwarder contract address for EIP-2771 meta-transactions
- `RELAYER_PRIVATE_KEY` - Private key for gas-paying wallet (must be 0x-prefixed hex)
- `RELAYER_WALLET_ADDRESS` - Address of the relayer wallet
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
- Integration with external user service for request validation
- Transaction signature verification to ensure user authorization
- Admin-only endpoints for dispute resolution

**API Documentation:**
- Swagger UI available at `/swagger-ui.html`
- OpenAPI spec at `/api-docs`
- Health endpoints at `/actuator/health` and `/actuator/info`

**Blockchain Integration:**
- Uses Web3j library for Ethereum-compatible blockchain interaction
- Supports contract creation, fund operations, and dispute management
- Event parsing for contract status tracking
- Transaction receipt polling with configurable timeouts