package com.conduit.chainservice.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class CreateContractRequest(
    @field:NotBlank(message = "Signed transaction is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]+$", message = "Invalid transaction hex format")
    val signedTransaction: String
)

data class CreateContractResponse(
    val success: Boolean,
    val transactionHash: String?,
    val contractAddress: String?,
    val error: String? = null
)

data class RaiseDisputeRequest(
    @field:NotBlank(message = "Signed transaction is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]+$", message = "Invalid transaction hex format")
    val signedTransaction: String,
    
    @field:NotBlank(message = "Contract address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid contract address format")
    val contractAddress: String
)

data class RaiseDisputeResponse(
    val success: Boolean,
    val transactionHash: String?,
    val error: String? = null
)

data class ClaimFundsRequest(
    @field:NotBlank(message = "Signed transaction is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]+$", message = "Invalid transaction hex format")
    val signedTransaction: String,
    
    @field:NotBlank(message = "Contract address is required")
    @field:Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid contract address format")
    val contractAddress: String
)

data class ClaimFundsResponse(
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