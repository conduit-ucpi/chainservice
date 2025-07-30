# Chain Service API Reference

This document provides detailed API reference for the Conduit UCPI Chain Service, including the new admin dispute resolution functionality.

## Base URL

```
http://localhost:8978
```

## Authentication

- **User Endpoints**: Requires valid user authentication cookies
- **Admin Endpoints**: Requires admin-level authentication cookies
- **Public Endpoints**: No authentication required (health checks, documentation)

## Admin Endpoints

### Resolve Contract Dispute (Admin Only)

Resolves a disputed escrow contract by distributing funds according to specified percentages.

**Endpoint:** `POST /api/admin/contracts/{id}/resolve`

**Authentication:** Admin required (via HTTP-only cookies)

**Parameters:**
- `id` (path): Contract ID or contract address (must be 42-character hex string starting with 0x)

**Request Body:**
```json
{
  "buyerPercentage": 60.0,
  "sellerPercentage": 40.0,
  "resolutionNote": "Admin resolution: buyer provided evidence of delivery issues"
}
```

**Request Fields:**
- `buyerPercentage` (number, required): Percentage of funds to award to buyer (0-100)
- `sellerPercentage` (number, required): Percentage of funds to award to seller (0-100)
- `resolutionNote` (string, optional): Admin note documenting the resolution decision

**Validation Rules:**
- Both percentages must be non-negative
- Percentages must sum to exactly 100.0 (small precision errors ~0.01 are tolerated)
- Contract ID must be a valid Ethereum address format

**Response (200 OK):**
```json
{
  "success": true,
  "transactionHash": "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
  "error": null
}
```

**Response (400 Bad Request):**
```json
{
  "success": false,
  "transactionHash": null,
  "error": "Percentages must sum to 100"
}
```

**Response (403 Forbidden):**
```json
{
  "success": false,
  "transactionHash": null,
  "error": "Access denied - admin privileges required"
}
```

**Possible Errors:**
- `400`: Invalid percentages, contract address format, or transaction failure
- `403`: Non-admin user attempting to access admin endpoint
- `404`: Contract not found
- `500`: Internal server error or blockchain communication failure

## Enhanced User Endpoints

### Resolve Dispute (Enhanced)

The enhanced resolve dispute endpoint now supports both percentage-based and legacy single-recipient resolution.

**Endpoint:** `POST /api/chain/resolve-dispute`

**Authentication:** Admin required

#### Format 1: Percentage-based Resolution (Recommended)

**Request Body:**
```json
{
  "contractAddress": "0x1234567890abcdef1234567890abcdef12345678",
  "buyerPercentage": 60.0,
  "sellerPercentage": 40.0,
  "resolutionNote": "Percentage-based resolution"
}
```

#### Format 2: Legacy Single Recipient (Deprecated)

**Request Body:**
```json
{
  "contractAddress": "0x1234567890abcdef1234567890abcdef12345678",
  "recipientAddress": "0x9876543210fedcba9876543210fedcba98765432"
}
```

**Automatic Format Detection:**
- If `buyerPercentage` and `sellerPercentage` are provided → percentage-based resolution
- If `recipientAddress` is provided → legacy single recipient resolution
- If neither format is complete → validation error

**Response:** Same format as admin endpoint

## Existing Endpoints

### Create Contract
**Endpoint:** `POST /api/chain/create-contract`

### Raise Dispute
**Endpoint:** `POST /api/chain/raise-dispute`

### Claim Funds
**Endpoint:** `POST /api/chain/claim-funds`

### Deposit Funds
**Endpoint:** `POST /api/chain/deposit-funds`

### Approve USDC
**Endpoint:** `POST /api/chain/approve-usdc`

### Get Contract Details
**Endpoint:** `GET /api/chain/contract/{contractAddress}`

### Get User Contracts
**Endpoint:** `GET /api/chain/contracts/{walletAddress}`

### Get Gas Costs
**Endpoint:** `GET /api/chain/gas-costs`

## Health & Monitoring

### Health Check
**Endpoint:** `GET /actuator/health`

### Application Info
**Endpoint:** `GET /actuator/info`

## Interactive Documentation

### Swagger UI
**Endpoint:** `GET /swagger-ui.html`

Access the interactive API documentation with request/response examples and the ability to test endpoints directly.

### OpenAPI Specification
**Endpoint:** `GET /api-docs`

Download the complete OpenAPI 3.0 specification in JSON format.

## Smart Contract Integration

### Dispute Resolution Process

1. **Admin Decision**: Admin uses percentage-based resolution endpoint
2. **Validation**: Service validates percentages and admin privileges
3. **Blockchain Transaction**: Service calls smart contract's `resolveDispute(uint256, uint256)` function
4. **Fund Distribution**: Smart contract distributes funds according to percentages
5. **Response**: Service returns transaction hash and success status

### Smart Contract Function Called

```solidity
function resolveDispute(uint256 buyerPercentage, uint256 sellerPercentage) external onlyGasPayer
```

- **Access Control**: Only the service's relayer wallet (admin) can call
- **Validation**: Contract validates percentages sum to 100
- **Distribution**: Calculates amounts and transfers to buyer/seller
- **Events**: Emits `DisputeResolved` and `FundsClaimed` events

## Error Handling

### Common Error Patterns

**Validation Errors (400):**
```json
{
  "success": false,
  "transactionHash": null,
  "error": "Percentages cannot be negative"
}
```

**Authentication Errors (403):**
```json
{
  "success": false,
  "transactionHash": null,
  "error": "Access denied - admin privileges required"
}
```

**Blockchain Errors (400):**
```json
{
  "success": false,
  "transactionHash": null,
  "error": "Transaction failed: insufficient gas"
}
```

**Server Errors (500):**
```json
{
  "success": false,
  "transactionHash": null,
  "error": "Internal server error"
}
```

## Best Practices

### Admin Resolution

1. **Always provide resolution notes** for audit trails
2. **Validate percentages** before sending requests
3. **Check contract status** before attempting resolution
4. **Monitor transaction success** via returned transaction hash
5. **Handle errors gracefully** and provide user feedback

### Error Handling

1. **Check HTTP status codes** first
2. **Parse error messages** for user-friendly display
3. **Retry failed transactions** with exponential backoff
4. **Log admin actions** for compliance and auditing
5. **Validate input client-side** before API calls

### Security Considerations

1. **Secure admin authentication** via HTTP-only cookies
2. **Validate admin privileges** on every request
3. **Audit all admin actions** with detailed logging
4. **Rate limit admin endpoints** to prevent abuse
5. **Monitor for suspicious activity**