package com.conduit.chainservice.escrow.models

import com.conduit.chainservice.escrow.validation.ConditionalEmailFields
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigInteger

// Request models
data class CreateContractRequest(
    @field:NotBlank(message = "Buyer address is required")
    val buyer: String,
    
    @field:NotBlank(message = "Seller address is required")
    val seller: String,
    
    @field:NotNull(message = "Amount is required")
    @field:Positive(message = "Amount must be positive")
    val amount: BigInteger,
    
    @field:NotNull(message = "Expiry timestamp is required")
    val expiryTimestamp: Long,
    
    @field:NotBlank(message = "Description is required")
    val description: String
)

@ConditionalEmailFields
data class RaiseDisputeRequest(
    @field:NotBlank(message = "Contract address is required")
    val contractAddress: String,
    
    @field:NotBlank(message = "User wallet address is required")
    val userWalletAddress: String,
    
    @field:NotBlank(message = "Signed transaction is required")
    val signedTransaction: String,
    
    val buyerEmail: String? = null,
    val sellerEmail: String? = null,
    val contractDescription: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val payoutDateTime: String? = null,
    val link: String? = null,
    
    @field:NotBlank(message = "Product name is required")
    val productName: String,
    
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

@ConditionalEmailFields
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

data class ApproveUSDCRequest(
    @field:NotBlank(message = "User wallet address is required")
    val userWalletAddress: String,
    
    @field:NotBlank(message = "Signed transaction is required")
    val signedTransaction: String
)

data class TransferUSDCRequest(
    @field:NotBlank(message = "Recipient address is required")
    val recipientAddress: String,
    
    @field:NotBlank(message = "Amount is required")
    val amount: String,
    
    @field:NotBlank(message = "User wallet address is required")
    val userWalletAddress: String,
    
    @field:NotBlank(message = "Signed transaction is required")
    val signedTransaction: String
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

data class ApproveUSDCResponse(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)

data class TransferUSDCResponse(
    val success: Boolean,
    val transactionHash: String?,
    val message: String,
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
    
    // Email fields for notifications - required for admin resolving disputes
    @field:NotBlank(message = "Buyer email is required")
    val buyerEmail: String,
    
    @field:NotBlank(message = "Seller email is required")
    val sellerEmail: String,
    
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