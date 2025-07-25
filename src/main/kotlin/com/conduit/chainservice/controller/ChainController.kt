package com.conduit.chainservice.controller

import com.conduit.chainservice.model.*
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.TransactionRelayService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/chain")
@Validated
@Tag(name = "Chain Service", description = "Blockchain transaction relay and contract management API")
class ChainController(
    private val transactionRelayService: TransactionRelayService,
    private val contractQueryService: ContractQueryService
) {

    private val logger = LoggerFactory.getLogger(ChainController::class.java)

    @PostMapping("/create-contract")
    @Operation(
        summary = "Create Escrow Contract",
        description = "Deploys a new escrow contract by calling the factory contract. The service pays the gas fees."
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
        request: CreateContractRequest
    ): ResponseEntity<CreateContractResponse> {
        return try {
            logger.info("Create contract request received")
            
            val result = runBlocking {
                transactionRelayService.createContract(
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
        description = "Raises a dispute on an existing escrow contract by relaying a user-signed transaction. The frontend must provide a transaction signed by the buyer or seller's wallet."
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
                    value = """{"contractAddress": "0x1234567890abcdef1234567890abcdef12345678", "signedTransaction": "0xf86c8082520894..."}"""
                )]
            )]
        )
        request: RaiseDisputeRequest
    ): ResponseEntity<RaiseDisputeResponse> {
        return try {
            logger.info("Raise dispute request received for contract: ${request.contractAddress}")
            
            val result = runBlocking {
                transactionRelayService.relayTransaction(request.signedTransaction)
            }

            val response = RaiseDisputeResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                error = result.error
            )

            if (result.success) {
                logger.info("Dispute raised successfully: ${result.transactionHash}")
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
        description = "Allows the seller to claim escrowed funds by relaying a user-signed transaction. The frontend must provide a transaction signed by the seller's wallet."
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
                    value = """{"contractAddress": "0x1234567890abcdef1234567890abcdef12345678", "signedTransaction": "0xf86c8082520894..."}"""
                )]
            )]
        )
        request: ClaimFundsRequest
    ): ResponseEntity<ClaimFundsResponse> {
        return try {
            logger.info("Claim funds request received for contract: ${request.contractAddress}")
            
            val result = runBlocking {
                transactionRelayService.relayTransaction(request.signedTransaction)
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
        description = "Deposits funds into an escrow contract by relaying a user-signed transaction. The frontend must provide a transaction signed by the buyer's wallet to authorize USDC transfer. The service pays the gas fees."
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
                examples = [ExampleObject(
                    name = "Deposit Funds Example",
                    value = """{"contractAddress": "0x1234567890abcdef1234567890abcdef12345678", "signedTransaction": "0xf86c8082520894..."}"""
                )]
            )]
        )
        request: DepositFundsRequest
    ): ResponseEntity<DepositFundsResponse> {
        return try {
            logger.info("Deposit funds request received for contract: ${request.contractAddress}")
            
            val result = runBlocking {
                transactionRelayService.relayTransaction(request.signedTransaction)
            }

            val response = DepositFundsResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                error = result.error
            )

            if (result.success) {
                logger.info("Funds deposited successfully: ${result.transactionHash}")
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
                examples = [ExampleObject(
                    name = "Resolve Dispute Example",
                    value = """{"contractAddress": "0x1234...abcd", "recipientAddress": "0x5678...efgh"}"""
                )]
            )]
        )
        request: ResolveDisputeRequest
    ): ResponseEntity<ResolveDisputeResponse> {
        return try {
            logger.info("Resolve dispute request received for contract: ${request.contractAddress}")
            
            val result = runBlocking {
                transactionRelayService.resolveDispute(request.contractAddress, request.recipientAddress)
            }

            val response = ResolveDisputeResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                error = result.error
            )

            if (result.success) {
                logger.info("Dispute resolved successfully: ${result.transactionHash}")
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
        @PathVariable walletAddress: String
    ): ResponseEntity<GetContractsResponse> {
        return try {
            logger.info("Get contracts request received for wallet: $walletAddress")

            if (!walletAddress.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
                return ResponseEntity.badRequest().body(
                    GetContractsResponse(emptyList())
                )
            }

            val contracts = runBlocking {
                contractQueryService.getContractsForWallet(walletAddress)
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
}