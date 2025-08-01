package com.conduit.chainservice.escrow.models

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

data class RaiseDisputeRequest(
    @field:NotBlank(message = "Contract address is required")
    val contractAddress: String,
    
    @field:NotBlank(message = "User wallet address is required")
    val userWalletAddress: String,
    
    @field:NotBlank(message = "Signed transaction is required")
    val signedTransaction: String,
    
    @field:NotBlank(message = "Buyer email is required")
    val buyerEmail: String,
    
    @field:NotBlank(message = "Seller email is required")
    val sellerEmail: String,
    
    @field:NotBlank(message = "Contract description is required")
    val contractDescription: String,
    
    @field:NotBlank(message = "Amount is required")
    val amount: String,
    
    @field:NotBlank(message = "Currency is required")
    val currency: String,
    
    @field:NotBlank(message = "Payout date time is required")
    val payoutDateTime: String,
    
    @field:NotBlank(message = "Product name is required")
    val productName: String
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
    
    @field:NotBlank(message = "Buyer email is required")
    val buyerEmail: String,
    
    @field:NotBlank(message = "Seller email is required")
    val sellerEmail: String,
    
    @field:NotBlank(message = "Contract description is required")
    val contractDescription: String,
    
    @field:NotBlank(message = "Amount is required")
    val amount: String,
    
    @field:NotBlank(message = "Payout date time is required")
    val payoutDateTime: String,
    
    @field:NotBlank(message = "Contract link is required")
    val contractLink: String
)

data class ResolveDisputeRequest(
    @field:NotBlank(message = "Contract address is required")
    val contractAddress: String,
    
    // For backward compatibility - if recipientAddress is provided, all funds go to this address
    val recipientAddress: String? = null,
    
    // For percentage-based resolution
    val buyerPercentage: Double? = null,
    val sellerPercentage: Double? = null,
    val resolutionNote: String? = null,
    
    @field:NotBlank(message = "Buyer email is required")
    val buyerEmail: String,
    
    @field:NotBlank(message = "Seller email is required")
    val sellerEmail: String,
    
    @field:NotBlank(message = "Contract description is required")
    val contractDescription: String,
    
    @field:NotBlank(message = "Amount is required")
    val amount: String,
    
    @field:NotBlank(message = "Currency is required")
    val currency: String,
    
    @field:NotBlank(message = "Payout date time is required")
    val payoutDateTime: String,
    
    val sellerActualAmount: String? = null,
    val buyerActualAmount: String? = null
)

data class ApproveUSDCRequest(
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
    
    val resolutionNote: String? = null
)

data class AdminResolveContractResponse(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)