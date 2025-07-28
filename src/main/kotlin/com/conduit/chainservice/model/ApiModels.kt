package com.conduit.chainservice.model

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigInteger

data class CreateContractRequest(
    @field:NotBlank(message = "Buyer address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid buyer address format")
    val buyer: String,
    
    @field:NotBlank(message = "Seller address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid seller address format")
    val seller: String,
    
    @field:Min(value = 1, message = "Amount must be positive")
    val amount: BigInteger,
    
    @field:Min(value = 1, message = "Expiry timestamp must be in the future")
    val expiryTimestamp: Long,
    
    @field:NotBlank(message = "Description is required")
    @field:Size(max = 160, message = "Description must be 160 characters or less")
    val description: String
)

data class CreateContractResponse(
    val success: Boolean,
    val transactionHash: String?,
    val contractAddress: String?,
    val error: String? = null
)

data class RaiseDisputeRequest(
    @field:NotBlank(message = "Contract address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid contract address format")
    val contractAddress: String,
    
    @field:NotBlank(message = "Signed transaction is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]+$", message = "Invalid signed transaction format")
    val signedTransaction: String
)

data class RaiseDisputeResponse(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)

data class ClaimFundsRequest(
    @field:NotBlank(message = "Contract address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid contract address format")
    val contractAddress: String,
    
    @field:NotBlank(message = "Signed transaction is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]+$", message = "Invalid signed transaction format")
    val signedTransaction: String
)

data class ClaimFundsResponse(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)

data class DepositFundsRequest(
    @field:NotBlank(message = "Contract address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid contract address format")
    val contractAddress: String,
    
    @field:NotBlank(message = "User wallet address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid user wallet address format")
    val userWalletAddress: String,
    
    @field:NotBlank(message = "Signed transaction is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]+$", message = "Invalid signed transaction format")
    val signedTransaction: String,
    
    val contractId: String? = null
)

data class DepositFundsResponse(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)

data class ResolveDisputeRequest(
    @field:NotBlank(message = "Contract address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid contract address format")
    val contractAddress: String,
    
    @field:NotBlank(message = "Recipient address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid recipient address format")
    val recipientAddress: String
)

data class ResolveDisputeResponse(
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

data class OperationGasCost(
    val operation: String,
    val gasLimit: Long,
    val gasPriceWei: BigInteger,
    val totalCostWei: BigInteger,
    val totalCostAvax: String
)

data class GasCostsResponse(
    val operations: List<OperationGasCost>,
    val timestamp: String
)

data class ApproveUSDCRequest(
    @field:NotBlank(message = "User wallet address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid user wallet address format")
    val userWalletAddress: String,
    
    @field:NotBlank(message = "Signed transaction is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]+$", message = "Invalid signed transaction format")
    val signedTransaction: String
)

data class ApproveUSDCResponse(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)

data class ContractServiceUpdateRequest(
    val chainAddress: String,
    val chainId: String,
    val buyerAddress: String
)

data class ContractServiceUpdateResponse(
    val id: String,
    val chainAddress: String,
    val chainId: String,
    val status: String
)