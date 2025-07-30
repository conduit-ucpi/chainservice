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
    val signedTransaction: String
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
    
    val contractId: String? = null
)

data class ResolveDisputeRequest(
    @field:NotBlank(message = "Contract address is required")
    val contractAddress: String,
    
    @field:NotBlank(message = "Recipient address is required")
    val recipientAddress: String
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