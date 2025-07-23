# Chain Service - Conduit UCPI

A Kotlin/Spring Boot microservice that acts as a transaction relayer, paying gas fees for user-signed blockchain transactions and providing contract query functionality.

## Features

- **Transaction Relaying**: Pays gas fees for user-signed transactions
- **Contract Creation**: Handles escrow contract deployment
- **Dispute Management**: Supports raising and resolving disputes
- **Fund Claims**: Enables sellers to claim escrowed funds
- **Contract Queries**: Provides contract status and history for users
- **Health Monitoring**: Built-in health checks and monitoring endpoints

## Technology Stack

- Kotlin
- Spring Boot 3.x
- Web3j for blockchain interaction
- Jackson for JSON processing
- Spring Security for validation

## Quick Start

### Prerequisites

- Java 17 or higher
- Gradle 7.x or higher
- Access to an Avalanche testnet RPC endpoint

### Environment Variables

Set the following environment variables:

```bash
export RPC_URL=https://api.avax-test.network/ext/bc/C/rpc
export USDC_CONTRACT_ADDRESS=0x5425890298aed601595a70AB815c96711a31Bc65
export CONTRACT_FACTORY_ADDRESS=0x...
export RELAYER_PRIVATE_KEY=0x...
export RELAYER_WALLET_ADDRESS=0x...
export SERVER_PORT=8978
export CORS_ALLOWED_ORIGINS=http://localhost:3000
```

### Building and Running

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Or run the JAR directly
java -jar build/libs/chainservice-1.0.0.jar
```

## API Endpoints

### 1. Create Contract
```http
POST /api/chain/create-contract
Content-Type: application/json

{
    "signedTransaction": "0x..."
}
```

### 2. Raise Dispute
```http
POST /api/chain/raise-dispute
Content-Type: application/json

{
    "signedTransaction": "0x...",
    "contractAddress": "0x..."
}
```

### 3. Claim Funds
```http
POST /api/chain/claim-funds
Content-Type: application/json

{
    "signedTransaction": "0x...",
    "contractAddress": "0x..."
}
```

### 4. Resolve Dispute (Admin Only)
```http
POST /api/chain/resolve-dispute
Content-Type: application/json

{
    "contractAddress": "0x...",
    "recipientAddress": "0x..."
}
```

### 5. Get User Contracts
```http
GET /api/chain/contracts/{walletAddress}
```

## API Documentation

The service provides interactive API documentation:

- **Swagger UI**: `GET /swagger-ui.html` - Interactive API documentation
- **OpenAPI Spec**: `GET /api-docs` - Raw OpenAPI 3.0 specification

## Health & Monitoring

- **Health Check**: `GET /actuator/health`
- **Application Info**: `GET /actuator/info`

The health endpoint monitors:
- Blockchain connectivity
- Relayer wallet balance
- Overall service status

## Configuration

The service uses `application.yml` for configuration with environment variable overrides. Key settings include:

- **Blockchain**: RPC URL, contract addresses, gas settings
- **CORS**: Allowed origins for cross-origin requests
- **Logging**: Configurable log levels for different components

## Security Features

- Input validation and sanitization
- Transaction signature verification
- Rate limiting considerations
- Secure private key handling via environment variables
- CORS protection

## Error Handling

The service includes comprehensive error handling:
- Validation errors return structured error responses
- Blockchain errors are properly caught and logged
- Network timeouts include retry logic
- Gas estimation failures use default limits

## Gas Management

- Monitors relayer wallet balance
- Configurable gas limits for different operations
- Gas price multiplier for reliable transaction inclusion
- Low balance warnings in health checks

## Development

### Project Structure

```
src/main/kotlin/com/conduit/chainservice/
├── ChainServiceApplication.kt           # Main application class
├── config/                             # Configuration classes
│   ├── Web3Config.kt                   # Web3j and blockchain config
│   └── WebConfig.kt                    # CORS configuration
├── controller/                         # REST controllers
│   ├── ChainController.kt              # Main API endpoints
│   └── HealthController.kt             # Health check endpoints
├── exception/                          # Error handling
│   └── GlobalExceptionHandler.kt       # Global exception handler
├── model/                              # Data models
│   ├── ApiModels.kt                    # Request/response models
│   └── ContractModels.kt               # Domain models
└── service/                            # Business logic
    ├── ContractQueryService.kt         # Contract state queries
    ├── EventParsingService.kt          # Blockchain event parsing
    └── TransactionRelayService.kt      # Transaction relaying
```

### Testing

```bash
# Run tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport
```

## Deployment

The service is designed for containerized deployment:

- Stateless service (no database required)
- Environment-based configuration
- Health check endpoints for load balancers
- Graceful shutdown handling

## Monitoring

The service provides metrics and logging for:
- Transaction relay success/failure rates
- Gas usage and costs
- Relayer wallet balance
- Response times
- Error rates by endpoint

## Support

For issues and questions, check the logs at the INFO level for transaction details and ERROR level for failures. The health endpoint provides real-time status of critical dependencies.