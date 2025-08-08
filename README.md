# Chain Service - Conduit UCPI

A Kotlin/Spring Boot microservice that acts as a transaction relayer, paying gas fees for user-signed blockchain transactions and providing contract query functionality.

## Features

- **Transaction Relaying**: Pays gas fees for user-signed transactions
- **Contract Creation**: Handles escrow contract deployment
- **Dispute Management**: Supports raising and admin resolution of disputes
- **Admin Resolution**: Percentage-based dispute resolution for admins
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
# Blockchain Configuration
export RPC_URL=https://api.avax-test.network/ext/bc/C/rpc
export CHAIN_ID=43113
export USDC_CONTRACT_ADDRESS=0x5425890298aed601595a70AB815c96711a31Bc65
export CONTRACT_FACTORY_ADDRESS=0x...
export TRUSTED_FORWARDER_ADDRESS=0x...

# Relayer Wallet Configuration
export RELAYER_PRIVATE_KEY=0x...
export RELAYER_WALLET_ADDRESS=0x...

# Service Configuration
export SERVER_PORT=8978
export USER_SERVICE_URL=http://localhost:8080
export CONTRACT_SERVICE_URL=http://localhost:8080
export CORS_ALLOWED_ORIGINS=http://localhost:3000

# Fee Configuration
export CREATOR_FEE_USDC_X_1M=1000000

# Gas Configuration (optional)
export MIN_GAS_WEI=6
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
POST /api/admin/contracts/{contractId}/resolve
Content-Type: application/json
Cookie: {admin auth cookies}

{
    "buyerPercentage": 60.0,
    "sellerPercentage": 40.0,
    "resolutionNote": "Admin resolution: buyer provided evidence of delivery issues"
}
```

### 5. Get User Contracts
```http
GET /api/chain/contracts/{walletAddress}
```

### 6. Get Contract Details
```http
GET /api/chain/contract/{contractAddress}
```

Returns details for a specific contract. Non-admin users can only access contracts where they are the buyer or seller.

## API Documentation

The service provides comprehensive interactive API documentation:

- **Swagger UI**: `GET /swagger-ui.html` - Interactive API documentation with request/response examples
- **OpenAPI Spec**: `GET /api-docs` - Raw OpenAPI 3.0 specification in JSON format

### API Features

- **Comprehensive Examples**: All endpoints include request/response examples
- **Error Documentation**: Detailed error codes and messages
- **Authentication Info**: Documents admin vs user access requirements
- **Validation Rules**: Parameter validation and constraints documented
- **Response Schemas**: Complete response object definitions

### Admin Endpoints

The service includes dedicated admin endpoints with proper authentication:

- **Admin Resolution**: `POST /api/admin/contracts/{id}/resolve`
- **Access Control**: Validates admin privileges via forwarded authentication cookies
- **Comprehensive Validation**: Percentage validation, contract address validation
- **Detailed Logging**: All admin actions logged with resolution notes

## Health & Monitoring

- **Health Check**: `GET /actuator/health`
- **Application Info**: `GET /actuator/info`

The health endpoint monitors:
- Blockchain connectivity
- Relayer wallet balance
- Overall service status

## Admin Features

### Dispute Resolution

The service provides comprehensive dispute resolution capabilities:

- **Percentage-based Resolution**: Split funds between buyer and seller (e.g., 60%/40%)
- **Admin Authentication**: Requires admin privileges to resolve disputes
- **Resolution Notes**: Optional notes for documenting admin decisions
- **Validation**: Ensures percentages are valid (non-negative, sum to 100)
- **Smart Contract Integration**: Calls the contract's percentage-based resolution function

### Admin Endpoints

- `POST /api/admin/contracts/{id}/resolve` - Primary admin resolution endpoint
- Authentication via HTTP-only cookies forwarded from the frontend
- Comprehensive error handling and validation
- Transaction execution via service's relayer wallet (admin privileges)

## Configuration

The service uses `application.yml` for configuration with environment variable overrides. Key settings include:

- **Blockchain**: RPC URL, contract addresses, gas settings
- **Authentication**: User service integration for admin validation
- **CORS**: Allowed origins for cross-origin requests
- **Logging**: Configurable log levels for different components

## Security Features

- **Input validation and sanitization**
- **Transaction signature verification** 
- **Admin authentication** - Only admin users can resolve disputes
- **Role-based access control** - Different endpoints for different user types
- **Secure private key handling** via environment variables
- **CORS protection**
- **Rate limiting considerations**

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
src/main/kotlin/
├── com/utility/chainservice/                   # Generic utility (future separate repo)
│   ├── BlockchainRelayService.kt              # Core transaction relay logic
│   ├── AuthenticationProvider.kt              # Authentication interface
│   ├── HttpAuthenticationProvider.kt          # HTTP-based auth implementation
│   └── models/                                # Utility models
└── com/conduit/chainservice/                  # Project-specific (escrow)
    ├── ChainServiceApplication.kt             # Main application class
    ├── auth/                                  # Authentication & security
    │   ├── AuthenticationFilter.kt
    │   ├── SecurityConfig.kt
    │   └── UserServiceClient.kt
    ├── config/                                # Configuration classes
    │   ├── Web3Config.kt                      # Web3j and blockchain config
    │   ├── OpenApiConfig.kt                   # Swagger/OpenAPI setup
    │   └── UtilityServiceConfiguration.kt     # Bridge to generic utility
    ├── controller/                            # REST controllers
    │   ├── AdminController.kt                 # Admin-only endpoints
    │   └── HealthController.kt                # Health check endpoints
    ├── escrow/                                # Escrow plugin
    │   ├── EscrowServicePlugin.kt             # Plugin implementation
    │   ├── EscrowTransactionService.kt        # Escrow business logic
    │   ├── EscrowController.kt                # Escrow API endpoints
    │   └── models/                            # Escrow-specific models
    │       ├── EscrowModels.kt                # Request/response models
    │       └── ContractModels.kt              # Domain models
    ├── exception/                             # Error handling
    │   └── GlobalExceptionHandler.kt          # Global exception handler
    └── service/                               # Business logic services
        ├── ContractQueryService.kt            # Contract state queries
        ├── EventParsingService.kt             # Blockchain event processing
        ├── TransactionRelayService.kt         # Legacy service (delegates to new)
        └── ContractServiceClient.kt           # External service integration
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