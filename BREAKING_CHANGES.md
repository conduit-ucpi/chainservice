# Breaking Changes: USDC → Token Renaming

## Version: 2.0.0 (Multi-Token Support)

**STATUS: ✅ COMPLETE - All chainservice changes implemented and tested**

This document lists ALL breaking changes from renaming USDC-specific references to generic token references.

### Summary
The chainservice has been successfully refactored to support any ERC20 token (USDC, USDT, DAI, etc.) instead of being hardcoded to USDC only. This involved:
- Renaming 2 API endpoints
- Renaming 4 request/response models
- Renaming 1 environment variable
- Renaming 1 configuration property
- All tests passing (build successful)

### Multi-Token Architecture
- ✅ Smart contracts already support any ERC20 token via `tokenAddress` parameter
- ✅ Token address is now dynamically queried from each escrow contract
- ✅ Transaction verification works with any ERC20 token
- ✅ Batch queries return the correct token for each contract

---

## 🔴 API Endpoint Changes

### 1. Approve Token Endpoint
**OLD:** `POST /api/chain/approve-usdc`
**NEW:** `POST /api/chain/approve-token`

**Request Model Changed:**
- `ApproveUSDCRequest` → `ApproveTokenRequest`

**Response Model Changed:**
- `ApproveUSDCResponse` → `ApproveTokenResponse`

**Who Needs to Update:**
- Frontend (webapp, usdcbay)
- Any external services calling this endpoint

---

### 2. Transfer Token Endpoint
**OLD:** `POST /api/chain/transfer-usdc`
**NEW:** `POST /api/chain/transfer-token`

**Request Model Changed:**
- `TransferUSDCRequest` → `TransferTokenRequest`

**Response Model Changed:**
- `TransferUSDCResponse` → `TransferTokenResponse`

**Who Needs to Update:**
- Frontend (webapp, usdcbay)
- Any external services calling this endpoint

---

## 🟡 Configuration Changes

### Environment Variables

**OLD:** `GAS_LIMIT_APPROVE_USDC`
**NEW:** `GAS_LIMIT_APPROVE_TOKEN`

**Who Needs to Update:**
- GitHub Actions workflows (`.github/workflows/*.yml`)
- GitHub repository secrets/variables
- Docker compose files
- Deployment scripts

### Configuration Properties (application.yml)

**OLD:**
```yaml
escrow:
  limit-approve-usdc: 60000
```

**NEW:**
```yaml
escrow:
  limit-approve-token: 60000
```

**Who Needs to Update:**
- `chainservice/src/main/resources/application.yml`
- `chainservice/src/test/resources/application-test.yml`

---

## 🟢 Internal Changes (No External Impact)

### Method Names (Internal Only)
- `approveUSDCWithGasTransfer()` → `approveTokenWithGasTransfer()`
- `transferUSDCWithGasTransfer()` → `transferTokenWithGasTransfer()`

### Property Names (Internal Only)
- `limitApproveUsdc` → `limitApproveToken`

### Gas Limit Map Keys (Internal Only)
- `"approveUSDC"` → `"approveToken"`
- `"transferUSDC"` → `"transferToken"`

---

## 📋 Migration Checklist

### Chainservice ✅ COMPLETE
- [x] Update API endpoint paths
- [x] Rename request/response models
- [x] Update configuration properties
- [x] Update environment variable names
- [x] Update all tests (all tests passing)
- [x] Update OpenAPI documentation
- [x] Add deprecated wrapper for TransactionRelayService (backward compatibility)

### Frontend Services (webapp, usdcbay) - USER ACTION REQUIRED
- [ ] Update API calls from `/api/chain/approve-usdc` to `/api/chain/approve-token`
- [ ] Update API calls from `/api/chain/transfer-usdc` to `/api/chain/transfer-token`
- [ ] Update TypeScript interfaces:
  - `ApproveUSDCRequest` → `ApproveTokenRequest`
  - `ApproveUSDCResponse` → `ApproveTokenResponse`
  - `TransferUSDCRequest` → `TransferTokenRequest`
  - `TransferUSDCResponse` → `TransferTokenResponse`

### Infrastructure - USER ACTION REQUIRED
- [ ] Update GitHub Actions workflows to change:
  - `GAS_LIMIT_APPROVE_USDC` → `GAS_LIMIT_APPROVE_TOKEN`
- [ ] Update environment variables in GitHub secrets/variables:
  - Rename: `GAS_LIMIT_APPROVE_USDC` → `GAS_LIMIT_APPROVE_TOKEN`
- [ ] Update Docker configurations if they reference the environment variable
- [ ] Update deployment scripts if they reference the environment variable

### Testing - RECOMMENDED
- [ ] Test approve token endpoint with USDC contracts
- [ ] Test approve token endpoint with USDT contracts
- [ ] Test transfer token endpoint with USDC
- [ ] Test transfer token endpoint with USDT
- [ ] Verify all existing contracts still work correctly

---

## 🔄 Backward Compatibility

**NO BACKWARD COMPATIBILITY** - This is a breaking change requiring coordinated deployment:

1. Deploy chainservice with new endpoint names
2. Deploy frontend with updated API calls
3. Update environment variables

**Recommended Deployment Order:**
1. Update GitHub environment variables first
2. Deploy chainservice
3. Deploy frontend services immediately after

---

## 📞 Endpoints Summary

### Changed Endpoints
| Old Endpoint | New Endpoint | Method | Breaking |
|-------------|--------------|--------|----------|
| `/api/chain/approve-usdc` | `/api/chain/approve-token` | POST | ✅ YES |
| `/api/chain/transfer-usdc` | `/api/chain/transfer-token` | POST | ✅ YES |

### Unchanged Endpoints
| Endpoint | Method | Status |
|----------|--------|--------|
| `/api/chain/create-contract` | POST | ✅ No change |
| `/api/chain/deposit-funds` | POST | ✅ No change |
| `/api/chain/claim-funds` | POST | ✅ No change |
| `/api/chain/raise-dispute` | POST | ✅ No change |
| `/api/chain/resolve-dispute` | POST | ✅ No change |
| `/api/chain/batch-info` | POST | ✅ No change |
| `/api/chain/verify-and-webhook` | POST | ✅ No change |

---

## 🧪 Testing Strategy

### Pre-Deployment Testing
1. Run full test suite (all 198+ tests must pass)
2. Test approve-token endpoint manually with Postman
3. Test transfer-token endpoint manually with Postman
4. Verify OpenAPI documentation is updated

### Post-Deployment Testing
1. Create test escrow with USDC
2. Create test escrow with USDT
3. Approve tokens for both contracts
4. Transfer tokens for both contracts
5. Verify all existing contracts still function

---

## 📝 Notes

- The actual functionality hasn't changed - only naming
- All endpoints still work with USDC, USDT, or any ERC20 token
- This is purely a clarification of the API to reflect multi-token support
- The changes make the API more accurate and less misleading

---

**Generated:** 2025-11-03
**Version:** 2.0.0-multitoken
