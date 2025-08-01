package com.conduit.chainservice.escrow

import com.conduit.chainservice.escrow.models.*
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.ContractServiceClient
import com.conduit.chainservice.service.EmailServiceClient
import com.utility.chainservice.models.OperationGasCost
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
    private val contractQueryService: ContractQueryService,
    private val contractServiceClient: ContractServiceClient,
    private val escrowServicePlugin: EscrowServicePlugin,
    private val emailServiceClient: EmailServiceClient
) {

    private val logger = LoggerFactory.getLogger(EscrowController::class.java)
    
    @Value("\${blockchain.chain-id}")
    private lateinit var chainId: String

    @PostMapping("/create-contract")
    @Operation(
        summary = "Create Escrow Contract",
        description = "Deploys a new escrow contract by calling the factory contract. The service pays the gas fees. Creator fee is determined by the CREATOR_FEE environment variable, except for 0.001 USDC contracts which have zero creator fee."
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
                    value = """{"buyer": "0x1234567890abcdef1234567890abcdef12345678", "seller": "0x9876543210fedcba9876543210fedcba98765432", "amount": "1000000", "expiryTimestamp": 1735689600, "description": "Product delivery escrow"}"""  
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
                    value = """{"contractAddress": "0x1234567890abcdef1234567890abcdef12345678", "userWalletAddress": "0x9876543210fedcba9876543210fedcba98765432", "signedTransaction": "0xf86c8082520894..."}"""
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
                
                // Send dispute raised email notifications if email addresses are provided
                if (!request.buyerEmail.isNullOrBlank() && !request.sellerEmail.isNullOrBlank()) {
                    try {
                        // Send notification to both parties about the dispute
                        runBlocking {
                            // Notify buyer
                            emailServiceClient.sendDisputeRaised(
                                recipientEmail = request.buyerEmail!!,
                                buyerEmail = request.buyerEmail!!,
                                sellerEmail = request.sellerEmail!!,
                                amount = request.amount ?: "N/A",
                                currency = request.currency ?: "USDC",
                                contractDescription = request.contractDescription ?: "Escrow contract",
                                payoutDateTime = request.payoutDateTime ?: "N/A",
                                productName = request.productName ?: "Product/Service",
                                httpRequest = httpRequest
                            ).block()
                            
                            // Notify seller
                            emailServiceClient.sendDisputeRaised(
                                recipientEmail = request.sellerEmail!!,
                                buyerEmail = request.buyerEmail!!,
                                sellerEmail = request.sellerEmail!!,
                                amount = request.amount ?: "N/A",
                                currency = request.currency ?: "USDC",
                                contractDescription = request.contractDescription ?: "Escrow contract",
                                payoutDateTime = request.payoutDateTime ?: "N/A",
                                productName = request.productName ?: "Product/Service",
                                httpRequest = httpRequest
                            ).block()
                        }
                        logger.info("Dispute raised notification emails sent successfully to both parties")
                    } catch (e: Exception) {
                        // Log the error but don't fail the dispute response since the blockchain transaction succeeded
                        logger.error("Failed to send dispute raised notification emails", e)
                    }
                } else {
                    logger.info("Buyer or seller email not provided, skipping dispute raised notification")
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
                
                // Send payment notification email if seller email is provided
                if (!request.sellerEmail.isNullOrBlank() && !request.buyerEmail.isNullOrBlank()) {
                    try {
                        runBlocking {
                            emailServiceClient.sendPaymentNotification(
                                sellerEmail = request.sellerEmail!!,
                                buyerEmail = request.buyerEmail!!,
                                contractDescription = request.contractDescription ?: "Escrow contract",
                                amount = request.amount ?: "N/A",
                                payoutDateTime = request.payoutDateTime ?: "N/A",
                                contractLink = request.contractLink ?: "",
                                httpRequest = httpServletRequest
                            ).block()
                        }
                        logger.info("Payment notification email sent successfully to seller: ${request.sellerEmail}")
                    } catch (e: Exception) {
                        // Log the error but don't fail the deposit response since the blockchain transaction succeeded
                        logger.error("Failed to send payment notification email to seller: ${request.sellerEmail}", e)
                    }
                } else {
                    logger.info("Seller or buyer email not provided, skipping payment notification")
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

    @PostMapping("/resolve-dispute")
    @Operation(
        summary = "Resolve Dispute (Admin Only)",
        description = "Resolves a dispute by transferring funds to the specified recipient. This is an admin-only operation."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Dispute resolved successfully",
            content = [Content(schema = Schema(implementation = ResolveDisputeResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request or resolution failed",
            content = [Content(schema = Schema(implementation = ResolveDisputeResponse::class))]
        )
    ])
    fun resolveDispute(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [
                    ExampleObject(
                        name = "Percentage-based Resolution",
                        value = """{"contractAddress": "0x1234...abcd", "buyerPercentage": 60.0, "sellerPercentage": 40.0, "resolutionNote": "Admin resolution note"}"""
                    ),
                    ExampleObject(
                        name = "Legacy Single Recipient (deprecated)",
                        value = """{"contractAddress": "0x1234...abcd", "recipientAddress": "0x5678...efgh"}"""
                    )
                ]
            )]
        )
        request: ResolveDisputeRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ResolveDisputeResponse> {
        return try {
            logger.info("Resolve dispute request received for contract: ${request.contractAddress}")
            
            val result = runBlocking {
                // Check if percentage-based resolution is requested
                if (request.buyerPercentage != null && request.sellerPercentage != null) {
                    // Validate percentages
                    val buyerPct = request.buyerPercentage!!
                    val sellerPct = request.sellerPercentage!!
                    
                    if (buyerPct < 0 || sellerPct < 0) {
                        return@runBlocking com.utility.chainservice.models.TransactionResult(
                            success = false,
                            transactionHash = null,
                            error = "Percentages cannot be negative"
                        )
                    }
                    
                    if (Math.abs(buyerPct + sellerPct - 100.0) > 0.01) {
                        return@runBlocking com.utility.chainservice.models.TransactionResult(
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
                    com.utility.chainservice.models.TransactionResult(
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
                
                // Send dispute resolved email notifications if email addresses are provided
                if (!request.buyerEmail.isNullOrBlank() && !request.sellerEmail.isNullOrBlank()) {
                    try {
                        // Calculate actual amounts based on percentages (if provided)
                        val totalAmount = request.amount ?: "N/A"
                        val sellerPercentage = request.sellerPercentage?.toString() ?: (if (request.buyerPercentage != null) (100.0 - request.buyerPercentage!!).toString() else "N/A")
                        val buyerPercentage = request.buyerPercentage?.toString() ?: (if (request.sellerPercentage != null) (100.0 - request.sellerPercentage!!).toString() else "N/A")
                        
                        runBlocking {
                            // Notify buyer
                            emailServiceClient.sendDisputeResolved(
                                recipientEmail = request.buyerEmail!!,
                                amount = totalAmount,
                                currency = request.currency ?: "USDC",
                                buyerEmail = request.buyerEmail!!,
                                sellerEmail = request.sellerEmail!!,
                                contractDescription = request.contractDescription ?: "Escrow contract",
                                payoutDateTime = request.payoutDateTime ?: "N/A",
                                sellerPercentage = sellerPercentage,
                                sellerActualAmount = request.sellerActualAmount ?: "N/A",
                                buyerPercentage = buyerPercentage,
                                buyerActualAmount = request.buyerActualAmount ?: "N/A",
                                httpRequest = httpRequest
                            ).block()
                            
                            // Notify seller
                            emailServiceClient.sendDisputeResolved(
                                recipientEmail = request.sellerEmail!!,
                                amount = totalAmount,
                                currency = request.currency ?: "USDC",
                                buyerEmail = request.buyerEmail!!,
                                sellerEmail = request.sellerEmail!!,
                                contractDescription = request.contractDescription ?: "Escrow contract",
                                payoutDateTime = request.payoutDateTime ?: "N/A",
                                sellerPercentage = sellerPercentage,
                                sellerActualAmount = request.sellerActualAmount ?: "N/A",
                                buyerPercentage = buyerPercentage,
                                buyerActualAmount = request.buyerActualAmount ?: "N/A",
                                httpRequest = httpRequest
                            ).block()
                        }
                        logger.info("Dispute resolved notification emails sent successfully to both parties")
                    } catch (e: Exception) {
                        // Log the error but don't fail the dispute resolution response since the blockchain transaction succeeded
                        logger.error("Failed to send dispute resolved notification emails", e)
                    }
                } else {
                    logger.info("Buyer or seller email not provided, skipping dispute resolved notification")
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
            
            val operationCosts = escrowServicePlugin.getRelayService().getOperationGasCosts(
                escrowServicePlugin.getGasOperations()
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

    @PostMapping("/approve-usdc")
    @Operation(
        summary = "Approve USDC Spending",
        description = "Approves an escrow contract to spend USDC tokens on behalf of the user. The service provides gas money and forwards the user-signed transaction."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "USDC approval successful",
            content = [Content(schema = Schema(implementation = ApproveUSDCResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request or transaction failed",
            content = [Content(schema = Schema(implementation = ApproveUSDCResponse::class))]
        )
    ])
    fun approveUSDC(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Approve USDC Example",
                    value = """{"userWalletAddress": "0x9876543210fedcba9876543210fedcba98765432", "signedTransaction": "0xf86c8082520894..."}"""
                )]
            )]
        )
        request: ApproveUSDCRequest
    ): ResponseEntity<ApproveUSDCResponse> {
        return try {
            logger.info("Approve USDC request received for user: ${request.userWalletAddress}")
            
            val result = runBlocking {
                escrowTransactionService.approveUSDCWithGasTransfer(request.userWalletAddress, request.signedTransaction)
            }

            val response = ApproveUSDCResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                error = result.error
            )

            if (result.success) {
                logger.info("USDC approved successfully: ${result.transactionHash}")
                ResponseEntity.ok(response)
            } else {
                logger.error("USDC approval failed: ${result.error}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("Error in approve USDC endpoint", e)
            val response = ApproveUSDCResponse(
                success = false,
                transactionHash = null,
                error = e.message ?: "Internal server error"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }

    data class GasCostsResponse(
        val operations: List<OperationGasCost>,
        val timestamp: String
    )
}