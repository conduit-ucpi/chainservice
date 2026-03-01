# Audit: Hardcoded TypeReference Patterns in Codebase

**Date**: 2026-02-06
**Status**: ✅ COMPLETE - All issues identified

## Summary

Searched for hardcoded TypeReference patterns that could cause bugs similar to the `StringIndexOutOfBoundsException` we fixed in `ContractQueryService.decodeContractInfoResult()`.

## Findings

### ✅ SAFE: ABI-Driven Code (No Action Needed)

These use ABI files correctly:

1. **AbiLoader.kt** - All `TypeReference.create()` calls
   - ✅ **Status**: CORRECT - This is the central ABI parsing utility
   - These are dynamically created FROM the ABI files

2. **ContractQueryService.callGetContractInfo()** (line 298)
   - ✅ **Status**: FIXED - Uses `abiLoader.getContractInfoOutputTypes()`

3. **ContractQueryService.executeMulticall3()** (line 557)
   - ✅ **Status**: FIXED - Uses `abiLoader.getContractInfoOutputTypes()`

4. **EscrowTransactionService** - All function calls
   - ✅ **Status**: FIXED - Uses `abiLoader.buildFunction()`
   - Lines 147, 297, 556, 655, 750

### ⚠️ EXTERNAL CONTRACT: Multicall3 (Acceptable)

**File**: `ContractQueryService.kt`
**Lines**: 582-588, 629

```kotlin
// Line 582: Building aggregate3 function for Multicall3
val aggregate3Function = Function(
    "aggregate3",
    listOf(DynamicArray(Multicall3Call3::class.java, calls)),
    listOf(object : TypeReference<DynamicArray<Multicall3Result>>() {})
)

// Line 629: Decoding Multicall3 results
val outputTypes = listOf(object : TypeReference<DynamicArray<Multicall3Result>>() {})
```

**Assessment**:
- ⚠️ Hardcoded, but for **external standard contract** (Multicall3)
- ✅ **ACCEPTABLE** - Multicall3 is a stable, immutable contract
- 📝 **No ABI file** exists for Multicall3 in our resources
- 💡 **Recommendation**: Could add Multicall3 ABI for consistency, but low priority

### ❌ BUG FOUND: VoteService.kt

**File**: `VoteService.kt`
**Line**: 64-68

```kotlin
val function = Function(
    "submitResolutionVote",
    listOf(Uint256(buyerPercentage)),
    emptyList()
)
```

**Issues**:
1. ❌ **Hardcoded Function construction** (not using `abiLoader.buildFunction()`)
2. ❌ **Function doesn't exist in ABI!** `submitResolutionVote` is not in `EscrowContract.abi.json`
3. 💥 **This is likely dead/broken code** that will fail if ever called

**ABI Functions Available**:
```
[AMOUNT, BUYER, CREATOR_FEE, DESCRIPTION, EXPIRY_TIMESTAMP, FACTORY, GAS_PAYER,
 SELLER, USDC_TOKEN, canClaim, canDeposit, canDispute, claimFunds, createdAt,
 depositFunds, getContractInfo, initialize, isClaimed, isDisputed, isExpired,
 isFunded, raiseDispute, resolveDispute]
```

**Recommendation**:
- Either: Remove VoteService entirely (dead code)
- Or: Update to use actual contract function from ABI

## Action Items

### Priority 1: Fix VoteService
- [ ] Determine if `submitResolutionVote` functionality is still needed
- [ ] If yes: Update to use correct function from ABI via `abiLoader.buildFunction()`
- [ ] If no: Delete VoteService.kt (dead code)

### Priority 2 (Optional): Multicall3 Consistency
- [ ] Add `Multicall3.abi.json` to resources
- [ ] Update ContractQueryService to use ABI for Multicall3 (optional, low priority)

## Conclusion

✅ **Main bug fixed**: ContractQueryService now uses ABI-driven types
❌ **1 additional issue found**: VoteService has hardcoded function that doesn't exist in ABI
⚠️ **1 acceptable pattern**: Multicall3 (external contract, stable interface)

All escrow contract interactions now properly use ABI files except VoteService which appears to be dead code.
