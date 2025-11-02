package com.conduit.chainservice.escrow.models

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigInteger

// Request models
data class CreateContractRequest(
    @field:NotBlank(message = "Token address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid token address format")
    val tokenAddress: String,
    
    @field:NotBlank(message = "Buyer address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid buyer address format")
    val buyer: String,
    
    @field:NotBlank(message = "Seller address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid seller address format")
    val seller: String,
    
    @field:NotNull(message = "Amount is required")
    @field:Positive(message = "Amount must be positive")
    val amount: BigInteger,
    
    @field:NotNull(message = "Expiry timestamp is required")
    val expiryTimestamp: Long,
    
    @field:NotBlank(message = "Description is required")
    @field:Size(max = 160, message = "Description must be 160 characters or less")
    val description: String,
    
    @field:NotBlank(message = "Contract service ID is required")
    val contractserviceId: String
)

data class RaiseDisputeRequest(
    @field:NotBlank(message = "Contract address is required")
    val contractAddress: String,
    
    @field:NotBlank(message = "User wallet address is required")
    val userWalletAddress: String,
    
    @field:NotBlank(message = "Signed transaction is required")
    val signedTransaction: String,
    
    val reason: String? = null,
    val refundPercent: Int? = null,
    val databaseId: String? = null
)

data class ClaimFundsRequest(
    @field:NotBlank(message = "Contract address is required")
    val contractAddress: String,
    
    @field:NotBlank(message = "User wallet address is required")
    val userWalletAddress: String,
    
    @field:NotBlank(message = "Signed transaction is required")
    val signedTransaction: String
)

data class DepositFundsRequest(
    @field:NotBlank(message = "Contract address is required")
    val contractAddress: String,
    
    @field:NotBlank(message = "User wallet address is required")
    val userWalletAddress: String,
    
    @field:NotBlank(message = "Signed transaction is required")
    val signedTransaction: String,
    
    val contractId: String? = null,
    
    val buyerEmail: String? = null,
    val sellerEmail: String? = null,
    val contractDescription: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val payoutDateTime: String? = null,
    val contractLink: String? = null
)

data class ResolveDisputeRequest(
    @field:NotBlank(message = "Contract address is required")
    val contractAddress: String,
    
    @field:NotBlank(message = "Product name is required")
    val productName: String,
    
    // For backward compatibility - if recipientAddress is provided, all funds go to this address
    val recipientAddress: String? = null,
    
    // For percentage-based resolution
    val buyerPercentage: Double? = null,
    val sellerPercentage: Double? = null,
    val resolutionNote: String? = null,
    
    val buyerEmail: String? = null,
    val sellerEmail: String? = null,
    val contractDescription: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val payoutDateTime: String? = null,
    val link: String? = null,
    
    val sellerActualAmount: String? = null,
    val buyerActualAmount: String? = null
)

data class ApproveTokenRequest(
    @field:NotBlank(message = "User wallet address is required")
    val userWalletAddress: String,

    @field:NotBlank(message = "Signed transaction is required")
    val signedTransaction: String
)

data class TransferTokenRequest(
    @field:NotBlank(message = "Recipient address is required")
    val recipientAddress: String,

    @field:NotBlank(message = "Amount is required")
    val amount: String,

    @field:NotBlank(message = "User wallet address is required")
    val userWalletAddress: String,

    @field:NotBlank(message = "Signed transaction is required")
    val signedTransaction: String
)

data class FundWalletRequest(
    @field:NotBlank(message = "Wallet address is required")
    val walletAddress: String,
    
    @field:NotNull(message = "Total amount needed is required")
    val totalAmountNeededWei: BigInteger
)

data class ClaimFundsAsGasPayerRequest(
    @field:NotBlank(message = "Contract address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid contract address format")
    val contractAddress: String
)

// Response models
data class CreateContractResponse(
    val success: Boolean,
    val transactionHash: String?,
    val contractAddress: String?,
    val error: String? = null
)

data class RaiseDisputeResponse(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)

data class ClaimFundsResponse(
    val success: Boolean,  
    val transactionHash: String?,
    val error: String? = null
)

data class DepositFundsResponse(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)

data class ResolveDisputeResponse(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)

data class ApproveTokenResponse(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)

data class TransferTokenResponse(
    val success: Boolean,
    val transactionHash: String?,
    val message: String,
    val error: String? = null
)

data class FundWalletResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

// Cache invalidation models
data class InvalidateCacheRequest(
    @field:NotBlank(message = "Contract address is required")
    val contractAddress: String,
    
    val reason: String? = null
)

data class InvalidateCacheResponse(
    val success: Boolean,
    val message: String,
    val contractAddress: String,
    val cachesInvalidated: List<String>,
    val error: String? = null
)

data class BatchInvalidateCacheRequest(
    @field:NotNull(message = "Contract addresses are required")
    val contractAddresses: List<String>,
    
    val reason: String? = null
)

data class BatchInvalidateCacheResponse(
    val success: Boolean,
    val message: String,
    val totalRequested: Int,
    val totalInvalidated: Int,
    val failedAddresses: List<String>,
    val error: String? = null
)

data class GetContractsResponse(
    val contracts: List<ContractInfo>
)

data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: String
)

data class AdminResolveContractRequest(
    @field:NotNull(message = "Buyer percentage is required")
    val buyerPercentage: Double,

    @field:NotNull(message = "Seller percentage is required")
    val sellerPercentage: Double,

    val resolutionNote: String? = null,

    // Email fields for notifications - optional for wallet-only users (empty string indicates wallet-only)
    val buyerEmail: String? = null,

    val sellerEmail: String? = null,

    @field:NotBlank(message = "Contract description is required")
    val contractDescription: String,

    @field:NotBlank(message = "Product name is required")
    val productName: String,

    @field:NotBlank(message = "Amount is required")
    val amount: String,

    @field:NotBlank(message = "Currency is required")
    val currency: String,

    @field:NotBlank(message = "Payout date time is required")
    val payoutDateTime: String,

    @field:NotBlank(message = "Chain address is required")
    val chainAddress: String,

    val sellerActualAmount: String? = null,
    val buyerActualAmount: String? = null,
    val link: String? = null
)

data class AdminResolveContractResponse(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)

// Batch query models
data class BatchContractInfoRequest(
    @field:NotNull(message = "Contract addresses list is required")
    val contractAddresses: List<String>
) {
    init {
        require(contractAddresses.isNotEmpty()) { "Contract addresses list cannot be empty" }
        require(contractAddresses.size <= 100) { "Cannot query more than 100 contracts at once" }
        contractAddresses.forEach { address ->
            require(address.matches(Regex("^0x[a-fA-F0-9]{40}$"))) { "Invalid contract address format: $address" }
        }
    }
}

data class BatchContractInfoResponse(
    val contracts: Map<String, ContractInfoResult>,
    val totalRequested: Int,
    val totalSuccessful: Int,
    val totalFailed: Int,
    val timestamp: String
)

data class ContractInfoResult(
    val success: Boolean,
    val contractInfo: ContractInfo? = null,
    val error: String? = null
)

// Pure JSON response models for batch-info endpoint
data class BatchContractInfoJsonResponse(
    val contracts: Map<String, ContractInfoJson>,
    val errors: Map<String, String>
)

data class ContractInfoJson(
    val contractAddress: String,
    val status: String,
    val funded: Boolean,
    val balance: String,
    val buyer: String,
    val seller: String,
    val expiryTimestamp: Long,
    val amount: String,
    val tokenAddress: String,
    val exists: Boolean
)

// Verify and webhook models
data class VerifyWebhookRequest(
    @field:NotBlank(message = "Transaction hash is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{64}$", message = "Invalid transaction hash format")
    val transaction_hash: String,

    @field:NotBlank(message = "Contract address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid contract address format")
    val contract_address: String,

    @field:NotBlank(message = "Webhook URL is required")
    val webhook_url: String,

    @field:NotNull(message = "Order ID is required")
    @field:Positive(message = "Order ID must be positive")
    val order_id: Int,

    @field:NotNull(message = "Expected amount is required")
    @field:Positive(message = "Expected amount must be positive")
    val expected_amount: Double,

    @field:NotBlank(message = "Expected recipient is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid expected recipient address format")
    val expected_recipient: String,

    @field:NotBlank(message = "Merchant wallet is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid merchant wallet address format")
    val merchant_wallet: String
)

data class VerifyWebhookResponse(
    val success: Boolean,
    val message: String,
    val transaction_verified: Boolean? = null,
    val webhook_sent: Boolean? = null,
    val error: String? = null
)