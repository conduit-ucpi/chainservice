package com.conduit.chainservice.controller

import com.conduit.chainservice.escrow.EscrowTransactionService
import com.conduit.chainservice.escrow.models.AdminResolveContractRequest
import com.conduit.chainservice.escrow.models.AdminResolveContractResponse
import com.conduit.chainservice.service.ContractQueryService
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
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin")
@Validated
@Tag(name = "Admin Operations", description = "Admin-only escrow contract operations")
class AdminController(
    private val escrowTransactionService: EscrowTransactionService,
    private val contractQueryService: ContractQueryService
) {

    private val logger = LoggerFactory.getLogger(AdminController::class.java)

    @PostMapping("/contracts/{id}/resolve")
    @Operation(
        summary = "Resolve Contract Dispute (Admin Only)",
        description = "Resolves a disputed escrow contract by distributing funds according to specified percentages. This is an admin-only operation that requires proper authentication."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Dispute resolved successfully",
            content = [Content(schema = Schema(implementation = AdminResolveContractResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request or resolution failed",
            content = [Content(schema = Schema(implementation = AdminResolveContractResponse::class))]
        ),
        ApiResponse(
            responseCode = "403",
            description = "Access denied - admin privileges required"
        ),
        ApiResponse(
            responseCode = "404",
            description = "Contract not found"
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = AdminResolveContractResponse::class))]
        )
    ])
    fun resolveContract(
        @Parameter(
            description = "Contract ID from the contract service",
            example = "507f1f77bcf86cd799439011"
        )
        @PathVariable id: String,
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Resolve Contract Example",
                    value = """{"buyerPercentage": 60.0, "sellerPercentage": 40.0, "resolutionNote": "Admin resolution: buyer provided evidence of delivery issues"}"""
                )]
            )]
        )
        request: AdminResolveContractRequest,
        httpServletRequest: HttpServletRequest
    ): ResponseEntity<AdminResolveContractResponse> {
        return try {
            logger.info("Admin resolve contract request received for contract ID: $id")
            
            // Verify admin access
            val userType = httpServletRequest.getAttribute("userType") as? String
            if (userType != "admin") {
                logger.warn("Non-admin user attempted to resolve contract: $id")
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    AdminResolveContractResponse(
                        success = false,
                        transactionHash = null,
                        error = "Access denied - admin privileges required"
                    )
                )
            }

            // Validate percentages
            val buyerPct = request.buyerPercentage
            val sellerPct = request.sellerPercentage
            
            if (buyerPct < 0 || sellerPct < 0) {
                return ResponseEntity.badRequest().body(
                    AdminResolveContractResponse(
                        success = false,
                        transactionHash = null,
                        error = "Percentages cannot be negative"
                    )
                )
            }
            
            if (Math.abs(buyerPct + sellerPct - 100.0) > 0.01) {
                return ResponseEntity.badRequest().body(
                    AdminResolveContractResponse(
                        success = false,
                        transactionHash = null,
                        error = "Percentages must sum to 100"
                    )
                )
            }

            // Get contract address from contract service using contract ID
            // For now, assume the ID is actually the contract address
            // TODO: Integrate with contract service to lookup contract address by ID
            val contractAddress = if (id.startsWith("0x") && id.length == 42) {
                id
            } else {
                // This would need to call the contract service to get the address
                logger.error("Contract ID to address lookup not implemented: $id")
                return ResponseEntity.badRequest().body(
                    AdminResolveContractResponse(
                        success = false,
                        transactionHash = null,
                        error = "Contract address lookup not implemented - please provide contract address directly"
                    )
                )
            }
            
            logger.info("Resolving dispute for contract: $contractAddress with buyer=${buyerPct}%, seller=${sellerPct}%")
            if (request.resolutionNote != null) {
                logger.info("Resolution note: ${request.resolutionNote}")
            }
            
            val result = runBlocking {
                escrowTransactionService.resolveDisputeWithPercentages(contractAddress, buyerPct, sellerPct)
            }

            val response = AdminResolveContractResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                error = result.error
            )

            if (result.success) {
                logger.info("Contract dispute resolved successfully: ${result.transactionHash}")
                ResponseEntity.ok(response)
            } else {
                logger.error("Contract dispute resolution failed: ${result.error}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("Error in admin resolve contract endpoint", e)
            val response = AdminResolveContractResponse(
                success = false,
                transactionHash = null,
                error = e.message ?: "Internal server error"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }
}