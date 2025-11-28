package com.conduit.chainservice.escrow

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.escrow.models.*
import com.conduit.chainservice.escrow.validation.EmailFieldValidator
import com.conduit.chainservice.service.ContractQueryServiceInterface
import com.conduit.chainservice.service.ContractServiceClient
import com.conduit.chainservice.service.EmailServiceClient
import com.conduit.chainservice.service.GasPayerServiceClient
import com.conduit.chainservice.service.WebhookService
import com.conduit.chainservice.model.OperationGasCost
import com.conduit.chainservice.model.TransactionResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/chain")
@Validated
@Tag(name = "Escrow Service", description = "Blockchain escrow contract management and transaction relay API")
class EscrowController(
    private val escrowTransactionService: EscrowTransactionService,
    private val contractQueryService: ContractQueryServiceInterface,
    private val contractServiceClient: ContractServiceClient,
    private val emailServiceClient: EmailServiceClient,
    private val gasPayerServiceClient: GasPayerServiceClient,
    private val webhookService: WebhookService,
    private val escrowProperties: EscrowProperties
) {

    private val logger = LoggerFactory.getLogger(EscrowController::class.java)

    @Value("\${app.service-link}")
    private lateinit var serviceLink: String
    
    @Value("\${blockchain.chain-id}")
    private lateinit var chainId: String

    @PostMapping("/create-contract")
    @Operation(
        summary = "Create Escrow Contract",
        description = "Deploys a new escrow contract by calling the factory contract. The service pays the gas fees. Creator fee is determined by the CREATOR_FEE environment variable, except for 0.001 USDC contracts which have zero creator fee. The contract service will be notified of the successful deployment with the provided contractserviceId."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Contract created successfully",
            content = [Content(schema = Schema(implementation = CreateContractResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request or transaction failed",
            content = [Content(schema = Schema(implementation = CreateContractResponse::class))]
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = CreateContractResponse::class))]
        )
    ])
    fun createContract(
        @Valid @RequestBody 
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Create Contract Example",
                    value = """{"tokenAddress": "0xA0b86a33E6441A9A0d7fc0C7F3C0A0D3E6A0b86a", "buyer": "0x1234567890abcdef1234567890abcdef12345678", "seller": "0x9876543210fedcba9876543210fedcba98765432", "amount": "1000000", "expiryTimestamp": 1735689600, "description": "Product delivery escrow", "contractserviceId": "507f1f77bcf86cd799439011"}"""  
                )]
            )]
        )
        request: CreateContractRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<CreateContractResponse> {
        return try {
            logger.info("Create contract request received")
            
            val result = runBlocking {
                escrowTransactionService.createContract(
                    request.tokenAddress,
                    request.buyer,
                    request.seller,
                    request.amount,
                    request.expiryTimestamp,
                    request.description
                )
            }

            val response = CreateContractResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                contractAddress = result.contractAddress,
                error = result.error
            )

            if (result.success) {
                logger.info("Contract created successfully: ${result.contractAddress}")
                
                // Always notify contract service since contractserviceId is required
                if (result.contractAddress != null) {
                    try {
                        runBlocking {
                            contractServiceClient.notifyContractCreation(
                                contractId = request.contractserviceId,
                                contractHash = result.contractAddress,
                                request = httpRequest
                            ).block()
                        }
                        logger.info("Contract service notified of contract creation for contractserviceId: ${request.contractserviceId}")
                    } catch (e: Exception) {
                        // Log the error but don't fail the response since the blockchain transaction succeeded
                        logger.error("Failed to notify contract service of contract creation for contractserviceId: ${request.contractserviceId}", e)
                    }
                } else {
                    logger.error("Contract creation succeeded but no contract address returned, cannot notify contract service")
                }
                
                ResponseEntity.ok(response)
            } else {
                logger.error("Contract creation failed: ${result.error}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("Error in create contract endpoint", e)
            val response = CreateContractResponse(
                success = false,
                transactionHash = null,
                contractAddress = null,
                error = e.message ?: "Internal server error"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    @PostMapping("/raise-dispute")
    @Operation(
        summary = "Raise Dispute",
        description = "Raises a dispute on an existing escrow contract by relaying a user-signed transaction. The service provides gas money and forwards the user-signed transaction. The frontend must provide a transaction signed by the buyer or seller's wallet."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Dispute raised successfully",
            content = [Content(schema = Schema(implementation = RaiseDisputeResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request or transaction failed",
            content = [Content(schema = Schema(implementation = RaiseDisputeResponse::class))]
        )
    ])
    fun raiseDispute(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Raise Dispute Example",
                    value = """{"contractAddress": "0x1234567890abcdef1234567890abcdef12345678", "userWalletAddress": "0x9876543210fedcba9876543210fedcba98765432", "signedTransaction": "0xf86c8082520894...", "reason": "The product was not delivered as described", "refundPercent": 50, "databaseId": "507f1f77bcf86cd799439011"}"""
                )]
            )]
        )
        request: RaiseDisputeRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<RaiseDisputeResponse> {
        return try {
            logger.info("Raise dispute request received for contract: ${request.contractAddress}")
            
            val result = runBlocking {
                escrowTransactionService.raiseDisputeWithGasTransfer(request.userWalletAddress, request.signedTransaction)
            }

            val response = RaiseDisputeResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                error = result.error
            )

            if (result.success) {
                logger.info("Dispute raised successfully: ${result.transactionHash}")
                
                // Update contract service with dispute information if databaseId is provided
                if (request.databaseId != null) {
                    try {
                        runBlocking {
                            contractServiceClient.updateContractWithDispute(
                                contractId = request.databaseId,
                                reason = request.reason,
                                refundPercent = request.refundPercent,
                                request = httpRequest
                            ).block()
                        }
                        logger.info("Contract service updated with dispute information for contract ID: ${request.databaseId}")
                    } catch (e: Exception) {
                        // Log the error but don't fail the dispute response since the blockchain transaction succeeded
                        logger.error("Failed to update contract service with dispute information for contract ID: ${request.databaseId}", e)
                    }
                } else {
                    logger.info("No databaseId provided in dispute request, skipping contract service update")
                }
                
                ResponseEntity.ok(response)
            } else {
                logger.error("Dispute raising failed: ${result.error}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("Error in raise dispute endpoint", e)
            val response = RaiseDisputeResponse(
                success = false,
                transactionHash = null,
                error = e.message ?: "Internal server error"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    @PostMapping("/claim-funds")
    @Operation(
        summary = "Claim Funds",
        description = "Allows the seller to claim escrowed funds by relaying a user-signed transaction. The service provides gas money and forwards the user-signed transaction. The frontend must provide a transaction signed by the seller's wallet."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Funds claimed successfully",
            content = [Content(schema = Schema(implementation = ClaimFundsResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request or transaction failed",
            content = [Content(schema = Schema(implementation = ClaimFundsResponse::class))]
        )
    ])
    fun claimFunds(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Claim Funds Example",
                    value = """{"contractAddress": "0x1234567890abcdef1234567890abcdef12345678", "userWalletAddress": "0x9876543210fedcba9876543210fedcba98765432", "signedTransaction": "0xf86c8082520894..."}"""
                )]
            )]
        )
        request: ClaimFundsRequest
    ): ResponseEntity<ClaimFundsResponse> {
        return try {
            logger.info("Claim funds request received for contract: ${request.contractAddress}")
            
            val result = runBlocking {
                escrowTransactionService.claimFundsWithGasTransfer(request.userWalletAddress, request.signedTransaction)
            }

            val response = ClaimFundsResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                error = result.error
            )

            if (result.success) {
                logger.info("Funds claimed successfully: ${result.transactionHash}")
                ResponseEntity.ok(response)
            } else {
                logger.error("Funds claiming failed: ${result.error}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("Error in claim funds endpoint", e)
            val response = ClaimFundsResponse(
                success = false,
                transactionHash = null,
                error = e.message ?: "Internal server error"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    @PostMapping("/claim-funds-as-gas-payer")
    @Operation(
        summary = "Claim Funds as Gas Payer",
        description = "Allows anyone to request that the platform claim escrowed funds from an expired contract on behalf of the seller. This endpoint creates and signs the transaction using the gas payer's credentials. No authentication required since funds always go to the seller's address."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Funds claimed successfully",
            content = [Content(schema = Schema(implementation = ClaimFundsResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request or transaction failed",
            content = [Content(schema = Schema(implementation = ClaimFundsResponse::class))]
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = ClaimFundsResponse::class))]
        )
    ])
    fun claimFundsAsGasPayer(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Claim Funds as Gas Payer Example",
                    value = """{"contractAddress": "0x1234567890abcdef1234567890abcdef12345678"}"""
                )]
            )]
        )
        request: ClaimFundsAsGasPayerRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ClaimFundsResponse> {
        return try {
            logger.info("Claim funds as gas payer request received for contract: ${request.contractAddress}")

            val result = runBlocking {
                escrowTransactionService.claimFundsAsGasPayer(request.contractAddress)
            }

            val response = ClaimFundsResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                error = result.error
            )

            if (result.success) {
                logger.info("Funds claimed as gas payer successfully: ${result.transactionHash}")
                ResponseEntity.ok(response)
            } else {
                logger.error("Funds claiming as gas payer failed: ${result.error}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("Error in claim funds as gas payer endpoint", e)
            val response = ClaimFundsResponse(
                success = false,
                transactionHash = null,
                error = e.message ?: "Internal server error"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    @PostMapping("/fund-approved-contract")
    @Operation(
        summary = "Fund Approved Contract",
        description = "Deposits funds into an escrow contract that has already been approved by the buyer. The platform pays gas fees and executes the deposit using the gas payer's credentials. The buyer must have previously approved the contract to spend their tokens. After successful deposit, the contract service is notified."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Funds deposited successfully",
            content = [Content(schema = Schema(implementation = DepositFundsResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request or transaction failed",
            content = [Content(schema = Schema(implementation = DepositFundsResponse::class))]
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = DepositFundsResponse::class))]
        )
    ])
    fun fundApprovedContract(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Fund Approved Contract Example",
                    value = """{"contractHash": "0x1234567890abcdef1234567890abcdef12345678"}"""
                )]
            )]
        )
        request: FundApprovedContractRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<DepositFundsResponse> {
        return try {
            logger.info("Fund approved contract request received for contract: ${request.contractHash}")

            val result = runBlocking {
                escrowTransactionService.depositFundsAsGasPayer(request.contractHash)
            }

            val response = DepositFundsResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                error = result.error
            )

            if (result.success) {
                logger.info("Contract funded successfully: ${result.transactionHash}")

                // Notify contract service about the deposit
                try {
                    runBlocking {
                        contractServiceClient.notifyDeposit(
                            contractHash = request.contractHash,
                            request = httpRequest
                        ).block()
                    }
                    logger.info("Contract service notified of deposit for contractHash: ${request.contractHash}")
                } catch (e: Exception) {
                    // Log the error but don't fail the response since the blockchain transaction succeeded
                    logger.error("Failed to notify contract service of deposit for contractHash: ${request.contractHash}", e)
                }

                ResponseEntity.ok(response)
            } else {
                logger.error("Contract funding failed: ${result.error}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("Error in fund approved contract endpoint", e)
            val response = DepositFundsResponse(
                success = false,
                transactionHash = null,
                error = e.message ?: "Internal server error"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    @PostMapping("/deposit-funds")
    @Operation(
        summary = "Deposit Funds",
        description = "Deposits funds into an escrow contract by relaying a user-signed transaction. The frontend must provide a transaction signed by the buyer's wallet to authorize USDC transfer. The service pays the gas fees. Optionally, if a contractId is provided, the contract service will be notified of the successful deployment."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Funds deposited successfully",
            content = [Content(schema = Schema(implementation = DepositFundsResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request or transaction failed",
            content = [Content(schema = Schema(implementation = DepositFundsResponse::class))]
        )
    ])
    fun depositFunds(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [
                    ExampleObject(
                        name = "Deposit Funds Example with Contract ID",
                        value = """{"contractAddress": "0x1234567890abcdef1234567890abcdef12345678", "userWalletAddress": "0x9876543210fedcba9876543210fedcba98765432", "signedTransaction": "0xf86c8082520894...", "contractId": "507f1f77bcf86cd799439011"}"""
                    ),
                    ExampleObject(
                        name = "Deposit Funds Example without Contract ID",
                        value = """{"contractAddress": "0x1234567890abcdef1234567890abcdef12345678", "userWalletAddress": "0x9876543210fedcba9876543210fedcba98765432", "signedTransaction": "0xf86c8082520894..."}"""
                    )
                ]
            )]
        )
        request: DepositFundsRequest,
        httpServletRequest: HttpServletRequest
    ): ResponseEntity<DepositFundsResponse> {
        return try {
            logger.info("Deposit funds request received for contract: ${request.contractAddress}")
            
            // If contractId is provided, get contract from contract service and validate status
            if (request.contractId != null) {
                try {
                    val contractData = runBlocking {
                        contractServiceClient.getContract(request.contractId, httpServletRequest).block()
                    }
                    
                    if (contractData == null) {
                        logger.error("Contract not found with ID: ${request.contractId}")
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                            DepositFundsResponse(
                                success = false,
                                transactionHash = null,
                                error = "Contract not found with ID: ${request.contractId}"
                            )
                        )
                    }
                    
                    val status = contractData["state"] as? String
                    if (status != "OK") {
                        logger.error("Contract ${request.contractId} has invalid state: $status. Expected: OK")
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                            DepositFundsResponse(
                                success = false,
                                transactionHash = null,
                                error = "Contract state is '$status', expected 'OK'"
                            )
                        )
                    }
                    
                    // Update status to IN-PROCESS before proceeding
                    runBlocking {
                        contractServiceClient.updateContractStatus(
                            request.contractId,
                            "IN-PROCESS",
                            httpServletRequest
                        ).block()
                    }
                    logger.info("Updated contract ${request.contractId} state to IN-PROCESS")
                    
                } catch (e: Exception) {
                    logger.error("Failed to validate or update contract state for ID: ${request.contractId}", e)
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                        DepositFundsResponse(
                            success = false,
                            transactionHash = null,
                            error = "Failed to validate contract: ${e.message}"
                        )
                    )
                }
            }
            
            val result = runBlocking {
                escrowTransactionService.depositFundsWithGasTransfer(request.userWalletAddress, request.signedTransaction)
            }

            val response = DepositFundsResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                error = result.error
            )

            if (result.success) {
                logger.info("Funds deposited successfully: ${result.transactionHash}")
                
                // Update contract service with deployment details if contractId is provided
                if (request.contractId != null) {
                    try {
                        runBlocking {
                            contractServiceClient.updateContractWithDeployment(
                                contractId = request.contractId,
                                chainAddress = request.contractAddress,
                                chainId = chainId,
                                buyerAddress = request.userWalletAddress,
                                request = httpServletRequest
                            ).block()
                        }
                        logger.info("Contract service updated successfully for contract ID: ${request.contractId}")
                    } catch (e: Exception) {
                        // Log the error but don't fail the deposit response since the blockchain transaction succeeded
                        logger.error("Failed to update contract service for contract ID: ${request.contractId}", e)
                    }
                } else {
                    logger.info("No contractId provided in deposit request, skipping contract service update")
                }
                
                // Send payment notification email if all required fields are provided
                if (EmailFieldValidator.canSendPaymentNotificationEmail(
                    request.buyerEmail,
                    request.sellerEmail,
                    request.contractDescription,
                    request.amount,
                    request.payoutDateTime
                )) {
                    try {
                        runBlocking {
                            emailServiceClient.sendPaymentNotification(
                                sellerEmail = request.sellerEmail!!,
                                buyerEmail = request.buyerEmail!!,
                                contractDescription = request.contractDescription!!,
                                amount = request.amount!!,
                                currency = request.currency!!,
                                payoutDateTime = request.payoutDateTime!!,
                                contractLink = request.contractLink ?: "$serviceLink/contract/${request.contractAddress}",
                                httpRequest = httpServletRequest
                            ).block()
                        }
                        logger.info("Payment notification email sent successfully to seller: ${request.sellerEmail}")
                    } catch (e: Exception) {
                        // Log the error but don't fail the deposit response since the blockchain transaction succeeded
                        logger.error("Failed to send payment notification email to seller: ${request.sellerEmail}", e)
                    }
                } else {
                    logger.info("Email notification skipped for payment - missing required fields. Required: buyerEmail, sellerEmail, contractDescription, amount, payoutDateTime")
                }
                
                ResponseEntity.ok(response)
            } else {
                logger.error("Funds deposit failed: ${result.error}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("Error in deposit funds endpoint", e)
            val response = DepositFundsResponse(
                success = false,
                transactionHash = null,
                error = e.message ?: "Internal server error"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    // Internal method called by AdminController for dispute resolution
    // Not exposed as an external endpoint - use /api/admin/contracts/{id}/resolve instead
    fun resolveDispute(
        request: ResolveDisputeRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ResolveDisputeResponse> {
        return try {
            logger.info("Resolve dispute request received for contract: ${request.contractAddress}")
            logger.debug("Dispute resolution request details - buyerPercentage: ${request.buyerPercentage}%, sellerPercentage: ${request.sellerPercentage}%, resolutionNote: ${request.resolutionNote?.let { "present(${it.length} chars)" } ?: "null"}")
            logger.debug("Request has email fields: buyerEmail=${request.buyerEmail != null}, sellerEmail=${request.sellerEmail != null}, amount=${request.amount != null}, currency=${request.currency ?: "default(USDC)"}")
            
            val result = runBlocking {
                // Check if percentage-based resolution is requested
                if (request.buyerPercentage != null && request.sellerPercentage != null) {
                    // Validate percentages
                    val buyerPct = request.buyerPercentage!!
                    val sellerPct = request.sellerPercentage!!
                    
                    if (buyerPct < 0 || sellerPct < 0) {
                        return@runBlocking TransactionResult(
                            success = false,
                            transactionHash = null,
                            error = "Percentages cannot be negative"
                        )
                    }
                    
                    if (Math.abs(buyerPct + sellerPct - 100.0) > 0.01) {
                        return@runBlocking TransactionResult(
                            success = false,
                            transactionHash = null,
                            error = "Percentages must sum to 100"
                        )
                    }
                    
                    logger.info("Using percentage-based resolution: buyer=${buyerPct}%, seller=${sellerPct}%")
                    if (request.resolutionNote != null) {
                        logger.info("Resolution note: ${request.resolutionNote}")
                    }
                    
                    escrowTransactionService.resolveDisputeWithPercentages(request.contractAddress, buyerPct, sellerPct)
                } else if (request.recipientAddress != null) {
                    // Legacy single recipient resolution
                    logger.warn("Using deprecated single recipient resolution")
                    escrowTransactionService.resolveDispute(request.contractAddress, request.recipientAddress!!)
                } else {
                    TransactionResult(
                        success = false,
                        transactionHash = null,
                        error = "Either provide buyerPercentage+sellerPercentage or recipientAddress (deprecated)"
                    )
                }
            }

            val response = ResolveDisputeResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                error = result.error
            )

            if (result.success) {
                logger.info("Dispute resolved successfully: ${result.transactionHash}")

                // Send dispute resolved email notifications to individual users who have valid emails
                // Support wallet-only users by skipping emails when email field is blank/null
                logger.debug("Checking email availability for dispute resolution notification")

                // Check if we have all required non-email fields
                val hasRequiredFields = !request.amount.isNullOrBlank() &&
                                       !request.payoutDateTime.isNullOrBlank() &&
                                       !request.contractDescription.isNullOrBlank() &&
                                       !request.sellerActualAmount.isNullOrBlank() &&
                                       !request.buyerActualAmount.isNullOrBlank()

                if (!hasRequiredFields) {
                    logger.warn("Cannot send email notifications - missing required non-email fields")
                    logger.debug("  - amount: ${if (request.amount.isNullOrBlank()) "❌ MISSING" else "✓ valid"}")
                    logger.debug("  - payoutDateTime: ${if (request.payoutDateTime.isNullOrBlank()) "❌ MISSING" else "✓ valid"}")
                    logger.debug("  - contractDescription: ${if (request.contractDescription.isNullOrBlank()) "❌ MISSING" else "✓ valid"}")
                    logger.debug("  - sellerActualAmount: ${if (request.sellerActualAmount.isNullOrBlank()) "❌ MISSING" else "✓ valid"}")
                    logger.debug("  - buyerActualAmount: ${if (request.buyerActualAmount.isNullOrBlank()) "❌ MISSING" else "✓ valid"}")
                } else {
                    // Calculate percentages and prepare shared data
                    val sellerPercentage = request.sellerPercentage?.toString() ?: (if (request.buyerPercentage != null) (100.0 - request.buyerPercentage!!).toString() else "0")
                    val buyerPercentage = request.buyerPercentage?.toString() ?: (if (request.sellerPercentage != null) (100.0 - request.sellerPercentage!!).toString() else "0")
                    val validatedLink = request.link ?: "$serviceLink/contract/${request.contractAddress}"

                    var buyerEmailSent = false
                    var sellerEmailSent = false

                    // Send email to buyer if they have a valid email address
                    if (!request.buyerEmail.isNullOrBlank()) {
                        try {
                            logger.debug("Sending dispute resolved email to buyer: ${request.buyerEmail}")
                            runBlocking {
                                val buyerEmailResult = emailServiceClient.sendDisputeResolved(
                                    recipientEmail = request.buyerEmail!!,
                                    amount = request.amount!!,
                                    currency = request.currency ?: "USDC",
                                    buyerEmail = request.buyerEmail!!,
                                    sellerEmail = request.sellerEmail ?: "N/A",
                                    contractDescription = request.contractDescription!!,
                                    payoutDateTime = request.payoutDateTime!!,
                                    sellerPercentage = sellerPercentage,
                                    sellerActualAmount = request.sellerActualAmount!!,
                                    buyerPercentage = buyerPercentage,
                                    buyerActualAmount = request.buyerActualAmount!!,
                                    productName = request.productName,
                                    link = validatedLink,
                                    httpRequest = httpRequest
                                ).block()
                                logger.debug("Buyer email result: ${buyerEmailResult?.let { "success=${it.success}, messageId=${it.messageId}" } ?: "null"}")
                            }
                            buyerEmailSent = true
                            logger.info("Dispute resolved notification email sent to buyer")
                        } catch (e: Exception) {
                            logger.error("Failed to send dispute resolved email to buyer: ${request.buyerEmail}", e)
                        }
                    } else {
                        logger.debug("Skipping buyer email - wallet-only user (no email address)")
                    }

                    // Send email to seller if they have a valid email address
                    if (!request.sellerEmail.isNullOrBlank()) {
                        try {
                            logger.debug("Sending dispute resolved email to seller: ${request.sellerEmail}")
                            runBlocking {
                                val sellerEmailResult = emailServiceClient.sendDisputeResolved(
                                    recipientEmail = request.sellerEmail!!,
                                    amount = request.amount!!,
                                    currency = request.currency ?: "USDC",
                                    buyerEmail = request.buyerEmail ?: "N/A",
                                    sellerEmail = request.sellerEmail!!,
                                    contractDescription = request.contractDescription!!,
                                    payoutDateTime = request.payoutDateTime!!,
                                    sellerPercentage = sellerPercentage,
                                    sellerActualAmount = request.sellerActualAmount!!,
                                    buyerPercentage = buyerPercentage,
                                    buyerActualAmount = request.buyerActualAmount!!,
                                    productName = request.productName,
                                    link = validatedLink,
                                    httpRequest = httpRequest
                                ).block()
                                logger.debug("Seller email result: ${sellerEmailResult?.let { "success=${it.success}, messageId=${it.messageId}" } ?: "null"}")
                            }
                            sellerEmailSent = true
                            logger.info("Dispute resolved notification email sent to seller")
                        } catch (e: Exception) {
                            logger.error("Failed to send dispute resolved email to seller: ${request.sellerEmail}", e)
                        }
                    } else {
                        logger.debug("Skipping seller email - wallet-only user (no email address)")
                    }

                    // Log summary of email notifications
                    when {
                        buyerEmailSent && sellerEmailSent -> logger.info("Dispute resolved notification emails sent to both parties")
                        buyerEmailSent -> logger.info("Dispute resolved notification email sent to buyer only (seller is wallet-only)")
                        sellerEmailSent -> logger.info("Dispute resolved notification email sent to seller only (buyer is wallet-only)")
                        else -> logger.info("No dispute resolved notification emails sent (both parties are wallet-only users)")
                    }
                }

                ResponseEntity.ok(response)
            } else {
                logger.error("Dispute resolution failed: ${result.error}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("Error in resolve dispute endpoint", e)
            val response = ResolveDisputeResponse(
                success = false,
                transactionHash = null,
                error = e.message ?: "Internal server error"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    @GetMapping("/contract/{contractAddress}")
    @Operation(
        summary = "Get Contract Details",
        description = "Retrieves details of a specific escrow contract by its address. Non-admin users can only access contracts where they are the buyer or seller."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Contract details retrieved successfully",
            content = [Content(schema = Schema(implementation = ContractInfo::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid contract address format"
        ),
        ApiResponse(
            responseCode = "403",
            description = "Access denied - user is not authorized to view this contract"
        ),
        ApiResponse(
            responseCode = "404",
            description = "Contract not found"
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    ])
    fun getContract(
        @Parameter(
            description = "Ethereum contract address (42 character hex string starting with 0x)",
            example = "0x1234567890abcdef1234567890abcdef12345678"
        )
        @PathVariable contractAddress: String,
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        return try {
            val userType = request.getAttribute("userType") as? String
            val userId = request.getAttribute("userId") as? String
            
            logger.info("Get contract request received for contract: $contractAddress, userType: $userType, userId: $userId")

            if (!contractAddress.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
                return ResponseEntity.badRequest().body(
                    mapOf("error" to "Invalid contract address format")
                )
            }

            val contract = runBlocking {
                contractQueryService.getContractInfo(contractAddress)
            }

            if (contract == null) {
                logger.info("Contract not found: $contractAddress")
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    mapOf("error" to "Contract not found")
                )
            }

            // Check authorization: admins can see all contracts, others only their own
            if (userType != "admin") {
                // Get the user's wallet address from the user service
                val userWalletAddress = request.getAttribute("userWallet") as? String
                
                if (userWalletAddress == null || 
                    (!contract.buyer.equals(userWalletAddress, ignoreCase = true) && 
                     !contract.seller.equals(userWalletAddress, ignoreCase = true))) {
                    logger.warn("Access denied for user $userId to contract $contractAddress")
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        mapOf("error" to "Access denied - you are not authorized to view this contract")
                    )
                }
            }

            logger.info("Contract details retrieved successfully for: $contractAddress")
            ResponseEntity.ok(contract)

        } catch (e: Exception) {
            logger.error("Error in get contract endpoint", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf("error" to "Internal server error", "message" to (e.message ?: "Unknown error"))
            )
        }
    }

    @GetMapping("/contracts/{walletAddress}")
    @Operation(
        summary = "Get User Contracts",
        description = "Retrieves all escrow contracts associated with a specific wallet address (as buyer or seller)."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Contracts retrieved successfully",
            content = [Content(schema = Schema(implementation = GetContractsResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid wallet address format",
            content = [Content(schema = Schema(implementation = GetContractsResponse::class))]
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = GetContractsResponse::class))]
        )
    ])
    fun getContracts(
        @Parameter(
            description = "Ethereum wallet address (42 character hex string starting with 0x)",
            example = "0x1234567890abcdef1234567890abcdef12345678"
        )
        @PathVariable walletAddress: String,
        request: HttpServletRequest
    ): ResponseEntity<GetContractsResponse> {
        return try {
            val userType = request.getAttribute("userType") as? String
            
            logger.info("Get contracts request received for wallet: $walletAddress, userType: $userType")

            if (!walletAddress.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
                return ResponseEntity.badRequest().body(
                    GetContractsResponse(emptyList())
                )
            }

            val contracts = runBlocking {
                contractQueryService.getContractsForWallet(walletAddress, userType)
            }

            logger.info("Found ${contracts.size} contracts for wallet: $walletAddress")
            ResponseEntity.ok(GetContractsResponse(contracts))

        } catch (e: Exception) {
            logger.error("Error in get contracts endpoint", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                GetContractsResponse(emptyList())
            )
        }
    }

    @GetMapping("/gas-costs")
    @Operation(
        summary = "Get Gas Costs",
        description = "Returns current gas costs in nAVAX for all contract operations based on real-time network gas prices."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Gas costs retrieved successfully",
            content = [Content(schema = Schema(implementation = GasCostsResponse::class))]
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    ])
    fun getGasCosts(): ResponseEntity<GasCostsResponse> {
        return try {
            logger.info("Gas costs request received")
            
            val operationCosts = listOf(
                OperationGasCost("createContract", escrowProperties.limitCreateContract.toBigInteger(), 0.toBigInteger(), 0.toBigInteger()),
                OperationGasCost("approveToken", escrowProperties.limitApproveToken.toBigInteger(), 0.toBigInteger(), 0.toBigInteger()),
                OperationGasCost("depositFunds", escrowProperties.limitDeposit.toBigInteger(), 0.toBigInteger(), 0.toBigInteger()),
                OperationGasCost("raiseDispute", escrowProperties.limitDispute.toBigInteger(), 0.toBigInteger(), 0.toBigInteger()),
                OperationGasCost("claimFunds", escrowProperties.limitClaim.toBigInteger(), 0.toBigInteger(), 0.toBigInteger()),
                OperationGasCost("resolveDispute", escrowProperties.limitResolve.toBigInteger(), 0.toBigInteger(), 0.toBigInteger())
            )
            val response = GasCostsResponse(
                operations = operationCosts,
                timestamp = Instant.now().toString()
            )
            
            logger.info("Gas costs retrieved successfully for ${operationCosts.size} operations")
            ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("Error in gas costs endpoint", e)
            val errorResponse = ErrorResponse(
                error = "Internal server error",
                message = e.message ?: "Failed to retrieve gas costs",
                timestamp = Instant.now().toString()
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                GasCostsResponse(emptyList(), Instant.now().toString())
            )
        }
    }

    @PostMapping("/transfer-token")
    @Operation(
        summary = "Transfer Token",
        description = "Transfers ERC20 tokens to another address by relaying a user-signed transaction. The service provides gas money and forwards the user-signed transaction. The transaction must be a standard ERC20 transfer call signed by the user's wallet. Works with any ERC20 token (USDC, USDT, DAI, etc.)."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Token transfer successful",
            content = [Content(schema = Schema(implementation = TransferTokenResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request or transaction failed",
            content = [Content(schema = Schema(implementation = TransferTokenResponse::class))]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Authentication failed",
            content = [Content(schema = Schema(implementation = TransferTokenResponse::class))]
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = TransferTokenResponse::class))]
        )
    ])
    fun transferToken(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Transfer Token Example",
                    value = """{"recipientAddress": "0x1234567890abcdef1234567890abcdef12345678", "amount": "100.50", "userWalletAddress": "0x9876543210fedcba9876543210fedcba98765432", "signedTransaction": "0xf86c8082520894..."}"""
                )]
            )]
        )
        request: TransferTokenRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<TransferTokenResponse> {
        return try {
            logger.info("Transfer token request received from ${request.userWalletAddress} to ${request.recipientAddress}, amount: ${request.amount}")

            // Validate that the authenticated user matches the wallet address
            val authenticatedUserWallet = httpRequest.getAttribute("userWallet") as? String
            val userType = httpRequest.getAttribute("userType") as? String

            if (authenticatedUserWallet == null) {
                logger.error("No authenticated user wallet found in request")
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    TransferTokenResponse(
                        success = false,
                        transactionHash = null,
                        message = "Authentication required",
                        error = "No authenticated user wallet found"
                    )
                )
            }

            if (!authenticatedUserWallet.equals(request.userWalletAddress, ignoreCase = true)) {
                logger.error("User wallet mismatch - authenticated: $authenticatedUserWallet, requested: ${request.userWalletAddress}")
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    TransferTokenResponse(
                        success = false,
                        transactionHash = null,
                        message = "Authentication failed",
                        error = "User wallet address does not match authenticated user"
                    )
                )
            }

            val result = runBlocking {
                escrowTransactionService.transferTokenWithGasTransfer(request.userWalletAddress, request.signedTransaction)
            }

            val response = TransferTokenResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                message = if (result.success) "Token transfer successful" else "Token transfer failed",
                error = if (!result.success) result.error else null
            )

            if (result.success) {
                logger.info("Token transferred successfully: ${result.transactionHash}")
                ResponseEntity.ok(response)
            } else {
                logger.error("Token transfer failed: ${result.error}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("Error in transfer token endpoint", e)
            val response = TransferTokenResponse(
                success = false,
                transactionHash = null,
                message = "Internal server error",
                error = e.message ?: "Unknown error occurred"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    @PostMapping("/fund-wallet")
    @Operation(
        summary = "Fund Wallet",
        description = "Requests funding for a wallet address through the gas-payer service. The service determines the funding amount and method."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Wallet funding request processed",
            content = [Content(schema = Schema(implementation = FundWalletResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = [Content(schema = Schema(implementation = FundWalletResponse::class))]
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = FundWalletResponse::class))]
        )
    ])
    fun fundWallet(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Fund Wallet Example",
                    value = """{"walletAddress": "0x1234567890abcdef1234567890abcdef12345678", "totalAmountNeededWei": "1000000000000000000"}"""
                )]
            )]
        )
        request: FundWalletRequest
    ): ResponseEntity<FundWalletResponse> {
        return try {
            logger.info("Fund wallet request received for wallet: ${request.walletAddress}, amount: ${request.totalAmountNeededWei} wei")
            
            // Validate wallet address format
            if (!request.walletAddress.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
                return ResponseEntity.badRequest().body(
                    FundWalletResponse(
                        success = false,
                        message = "Invalid wallet address format",
                        error = "Wallet address must be a 42 character hex string starting with 0x"
                    )
                )
            }
            
            val result = runBlocking {
                gasPayerServiceClient.fundWallet(request.walletAddress, request.totalAmountNeededWei)
            }

            if (result.success) {
                logger.info("Wallet funding request successful: ${result.message}")
                ResponseEntity.ok(result)
            } else {
                logger.error("Wallet funding request failed: ${result.error}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result)
            }

        } catch (e: Exception) {
            logger.error("Error in fund wallet endpoint", e)
            val response = FundWalletResponse(
                success = false,
                message = "Internal server error",
                error = e.message ?: "Unknown error occurred"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    @PostMapping("/approve-token")
    @Operation(
        summary = "Approve Token Spending",
        description = "Approves an escrow contract to spend ERC20 tokens on behalf of the user. The service provides gas money and forwards the user-signed transaction. Works with any ERC20 token (USDC, USDT, DAI, etc.)."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Token approval successful",
            content = [Content(schema = Schema(implementation = ApproveTokenResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request or transaction failed",
            content = [Content(schema = Schema(implementation = ApproveTokenResponse::class))]
        )
    ])
    fun approveToken(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Approve Token Example",
                    value = """{"userWalletAddress": "0x9876543210fedcba9876543210fedcba98765432", "signedTransaction": "0xf86c8082520894..."}"""
                )]
            )]
        )
        request: ApproveTokenRequest
    ): ResponseEntity<ApproveTokenResponse> {
        return try {
            logger.info("Approve token request received for user: ${request.userWalletAddress}")

            val result = runBlocking {
                escrowTransactionService.approveTokenWithGasTransfer(request.userWalletAddress, request.signedTransaction)
            }

            val response = ApproveTokenResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                error = result.error
            )

            if (result.success) {
                logger.info("Token approved successfully: ${result.transactionHash}")
                ResponseEntity.ok(response)
            } else {
                logger.error("Token approval failed: ${result.error}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("Error in approve token endpoint", e)
            val response = ApproveTokenResponse(
                success = false,
                transactionHash = null,
                error = e.message ?: "Internal server error"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    @PostMapping("/contracts/batch-info")
    @Operation(
        summary = "Batch Query Contract Information",
        description = "Retrieves blockchain state for multiple contracts in a single efficient RPC batch request. Returns contract information including status, funding state, amounts, and timestamps. Supports up to 100 contracts per request with graceful partial failure handling."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Batch query completed (may include partial failures)",
            content = [Content(schema = Schema(implementation = BatchContractInfoJsonResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request (e.g., too many contracts, invalid addresses)",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))]
        )
    ])
    fun getBatchContractInfo(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [
                    ExampleObject(
                        name = "Batch Query Example",
                        value = """{"contractAddresses": ["0x1234567890abcdef1234567890abcdef12345678", "0x9876543210fedcba9876543210fedcba98765432", "0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef"]}"""
                    ),
                    ExampleObject(
                        name = "Single Contract Example",
                        value = """{"contractAddresses": ["0x1234567890abcdef1234567890abcdef12345678"]}"""
                    )
                ]
            )]
        )
        request: BatchContractInfoRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<*> {
        return try {
            val userType = httpRequest.getAttribute("userType") as? String
            val userId = httpRequest.getAttribute("userId") as? String
            
            logger.info("Batch contract info request received for ${request.contractAddresses.size} contracts, userType: $userType, userId: $userId")
            
            // Validate request
            if (request.contractAddresses.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    ErrorResponse(
                        error = "Bad Request",
                        message = "Contract addresses list cannot be empty",
                        timestamp = Instant.now().toString()
                    )
                )
            }
            
            if (request.contractAddresses.size > 100) {
                return ResponseEntity.badRequest().body(
                    ErrorResponse(
                        error = "Bad Request", 
                        message = "Cannot query more than 100 contracts at once",
                        timestamp = Instant.now().toString()
                    )
                )
            }
            
            // For unauthenticated requests (service-to-service calls), allow full access
            // For authenticated users, check permissions
            val allowedContracts = if (userType == null || userType == "admin") {
                // No auth (internal service call) or admin user - allow all contracts
                request.contractAddresses
            } else {
                // Regular authenticated user - check wallet address
                val userWalletAddress = httpRequest.getAttribute("userWallet") as? String
                
                if (userWalletAddress == null) {
                    logger.warn("No user wallet address found for batch request from user $userId")
                    return ResponseEntity.badRequest().body(
                        ErrorResponse(
                            error = "Bad Request",
                            message = "User wallet address not found",
                            timestamp = Instant.now().toString()
                        )
                    )
                }
                
                // Filter contracts by checking individual contract access
                // For efficiency in batch operations, we'll allow the request but mark unauthorized contracts as failed
                request.contractAddresses
            }
            
            val batchResults = runBlocking {
                contractQueryService.getBatchContractInfo(allowedContracts)
            }
            
            // For unauthenticated or admin users, return all results
            // For regular authenticated users, filter out contracts they don't have access to
            val filteredResults = if (userType == null || userType == "admin") {
                // No auth (internal service call) or admin user - return all results
                batchResults
            } else {
                // Regular authenticated user - filter by wallet address
                val userWalletAddress = httpRequest.getAttribute("userWallet") as? String
                batchResults.mapValues { (contractAddress, result) ->
                    if (result.success && result.contractInfo != null && userWalletAddress != null) {
                        val contract = result.contractInfo!!
                        if (contract.buyer.equals(userWalletAddress, ignoreCase = true) || 
                            contract.seller.equals(userWalletAddress, ignoreCase = true)) {
                            result
                        } else {
                            ContractInfoResult(
                                success = false,
                                contractInfo = null,
                                error = "Access denied - you are not authorized to view this contract"
                            )
                        }
                    } else {
                        result
                    }
                }
            }
            
            // Transform to pure JSON format
            val contractsMap = mutableMapOf<String, ContractInfoJson>()
            val errorsMap = mutableMapOf<String, String>()
            
            filteredResults.forEach { (contractAddress, result) ->
                if (result.success && result.contractInfo != null) {
                    val contract = result.contractInfo!!
                    // Use token address from contract info (no separate query needed)
                    // For old contracts without tokenAddress field, the decoder will default to USDC
                    val tokenAddress = contract.tokenAddress

                    contractsMap[contractAddress] = ContractInfoJson(
                        contractAddress = contractAddress,
                        status = contract.status.name,
                        funded = contract.funded,
                        balance = if (contract.funded) contract.amount.toString() else "0",
                        buyer = contract.buyer,
                        seller = contract.seller,
                        expiryTimestamp = contract.expiryTimestamp,
                        amount = contract.amount.toString(),
                        tokenAddress = tokenAddress,
                        exists = true
                    )
                } else {
                    errorsMap[contractAddress] = result.error ?: "Contract not found"
                }
            }
            
            val response = BatchContractInfoJsonResponse(
                contracts = contractsMap,
                errors = errorsMap
            )
            
            val totalRequested = request.contractAddresses.size
            val totalSuccessful = contractsMap.size
            val totalFailed = errorsMap.size
            
            logger.info("Batch contract info completed: $totalRequested requested, $totalSuccessful successful, $totalFailed failed")
            ResponseEntity.ok(response)
            
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid batch contract info request", e)
            val errorResponse = ErrorResponse(
                error = "Bad Request",
                message = e.message ?: "Invalid request parameters",
                timestamp = Instant.now().toString()
            )
            ResponseEntity.badRequest().body(errorResponse)
            
        } catch (e: Exception) {
            logger.error("Error in batch contract info endpoint", e)
            val errorResponse = ErrorResponse(
                error = "Internal Server Error",
                message = e.message ?: "Failed to process batch request",
                timestamp = Instant.now().toString()
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
        }
    }

    @PostMapping("/verify-and-webhook")
    @Operation(
        summary = "Verify Transaction and Send Webhook",
        description = "Verifies a blockchain transaction and sends a webhook to WordPress. Validates that the transaction exists, has sufficient confirmations, matches the expected recipient and amount, and is a USDC transfer. On successful verification, sends a webhook with status 'funded' to the specified WordPress URL."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Transaction verified and webhook sent successfully",
            content = [Content(schema = Schema(implementation = VerifyWebhookResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request or transaction verification failed",
            content = [Content(schema = Schema(implementation = VerifyWebhookResponse::class))]
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = VerifyWebhookResponse::class))]
        )
    ])
    fun verifyAndWebhook(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Verify and Webhook Example",
                    value = """{"transaction_hash": "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890", "contract_address": "0x1234567890abcdef1234567890abcdef12345678", "webhook_url": "https://example.com/webhook", "order_id": 123, "expected_amount": 100.00, "expected_recipient": "0x9876543210fedcba9876543210fedcba98765432", "merchant_wallet": "0x9876543210fedcba9876543210fedcba98765432"}"""
                )]
            )]
        )
        request: VerifyWebhookRequest
    ): ResponseEntity<VerifyWebhookResponse> {
        return try {
            logger.info("Verify and webhook request received for transaction: ${request.transaction_hash}")

            // Step 1: Verify the blockchain transaction
            val verificationResult = runBlocking {
                escrowTransactionService.verifyTransactionForWebhook(
                    transactionHash = request.transaction_hash,
                    contractAddress = request.contract_address,
                    expectedAmount = request.expected_amount,
                    expectedRecipient = request.expected_recipient,
                    merchantWallet = request.merchant_wallet
                )
            }

            if (!verificationResult.verified) {
                logger.error("Transaction verification failed: ${verificationResult.error}")
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    VerifyWebhookResponse(
                        success = false,
                        message = "Transaction verification failed",
                        transaction_verified = false,
                        webhook_sent = false,
                        error = verificationResult.error
                    )
                )
            }

            logger.info("Transaction ${request.transaction_hash} verified successfully")

            // Step 2: Send webhook to WordPress with contract_hash as primary identifier
            val webhookResult = runBlocking {
                webhookService.sendWebhook(
                    webhookUrl = request.webhook_url,
                    contractHash = request.contract_address, // Primary identifier: full contract address
                    orderId = request.order_id,              // Supplementary data for reference
                    transactionHash = request.transaction_hash,
                    amount = verificationResult.actualAmount ?: request.expected_amount
                )
            }

            if (!webhookResult.success) {
                logger.error("Webhook sending failed: ${webhookResult.error}")
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    VerifyWebhookResponse(
                        success = false,
                        message = "Transaction verified but webhook failed",
                        transaction_verified = true,
                        webhook_sent = false,
                        error = webhookResult.error
                    )
                )
            }

            logger.info("Webhook sent successfully to ${request.webhook_url}")

            ResponseEntity.ok(
                VerifyWebhookResponse(
                    success = true,
                    message = "Transaction verified and webhook sent successfully",
                    transaction_verified = true,
                    webhook_sent = true
                )
            )

        } catch (e: Exception) {
            logger.error("Error in verify and webhook endpoint", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                VerifyWebhookResponse(
                    success = false,
                    message = "Internal server error",
                    transaction_verified = false,
                    webhook_sent = false,
                    error = e.message ?: "Unknown error occurred"
                )
            )
        }
    }

    data class GasCostsResponse(
        val operations: List<OperationGasCost>,
        val timestamp: String
    )
}