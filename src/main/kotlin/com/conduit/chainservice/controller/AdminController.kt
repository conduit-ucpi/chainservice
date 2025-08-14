package com.conduit.chainservice.controller

import com.conduit.chainservice.escrow.EscrowController
import com.conduit.chainservice.escrow.models.AdminResolveContractRequest
import com.conduit.chainservice.escrow.models.AdminResolveContractResponse
import com.conduit.chainservice.escrow.models.ResolveDisputeRequest
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.CacheInvalidationService
import com.conduit.chainservice.service.ContractServiceClient
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
    private val escrowController: EscrowController,
    private val contractQueryService: ContractQueryService,
    private val cacheInvalidationService: CacheInvalidationService,
    private val contractServiceClient: ContractServiceClient
) {

    private val logger = LoggerFactory.getLogger(AdminController::class.java)

    @PostMapping("/contracts/{id}/resolve")
    @Operation(
        summary = "Resolve Contract Dispute",
        description = "Resolves a disputed escrow contract by distributing funds according to specified percentages. This operation can be performed by admins, or by any authenticated user if both parties have mutually agreed on the same refund percentage."
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
            description = "Access denied - admin privileges required or mutual agreement not found"
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
            description = "Contract identifier (the actual chain address is provided in the request body)",
            example = "507f1f77bcf86cd799439011"
        )
        @PathVariable id: String,
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Resolve Contract Example",
                    value = """{"buyerPercentage": 60.0, "sellerPercentage": 40.0, "resolutionNote": "Admin resolution: buyer provided evidence of delivery issues", "buyerEmail": "buyer@example.com", "sellerEmail": "seller@example.com", "amount": "1000000", "currency": "USDC", "contractDescription": "Web development services", "productName": "Web development services", "chainAddress": "0x1234567890abcdef1234567890abcdef12345678", "payoutDateTime": "1722598500", "buyerActualAmount": "600000", "sellerActualAmount": "400000"}"""
                )]
            )]
        )
        request: AdminResolveContractRequest,
        httpServletRequest: HttpServletRequest
    ): ResponseEntity<AdminResolveContractResponse> {
        return try {
            logger.info("Admin resolve contract request received for contract ID: $id")
            
            // Check if both parties have mutually agreed first
            val disputeStatus = contractServiceClient.getDisputeStatus(id, httpServletRequest).block()
            val hasMutualAgreement = disputeStatus?.hasMutualAgreement() ?: false
            
            // Verify admin access (unless there's mutual agreement)
            val userType = httpServletRequest.getAttribute("userType") as? String
            if (!hasMutualAgreement && userType != "admin") {
                logger.warn("Non-admin user attempted to resolve contract without mutual agreement: $id")
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    AdminResolveContractResponse(
                        success = false,
                        transactionHash = null,
                        error = "Access denied - admin privileges required (no mutual agreement found)"
                    )
                )
            }
            
            if (hasMutualAgreement) {
                logger.info("Contract $id has mutual agreement: both parties agreed to ${disputeStatus?.sellerLatestRefundEntry}% refund")
            } else {
                logger.info("Contract $id requires admin resolution (no mutual agreement)")
            }

            // Validate percentages early to provide consistent error messages
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

            // Use the chain address provided in the request body
            val contractAddress = request.chainAddress
            
            // Validate the chain address format
            if (!contractAddress.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
                return ResponseEntity.badRequest().body(
                    AdminResolveContractResponse(
                        success = false,
                        transactionHash = null,
                        error = "Invalid chain address format"
                    )
                )
            }
            
            // Convert AdminResolveContractRequest to ResolveDisputeRequest to reuse existing logic
            val resolveDisputeRequest = ResolveDisputeRequest(
                contractAddress = contractAddress,
                productName = request.productName,
                recipientAddress = null, // Use percentage-based resolution
                buyerPercentage = request.buyerPercentage,
                sellerPercentage = request.sellerPercentage,
                resolutionNote = request.resolutionNote,
                buyerEmail = request.buyerEmail,
                sellerEmail = request.sellerEmail,
                contractDescription = request.contractDescription,
                amount = request.amount,
                currency = request.currency,
                payoutDateTime = request.payoutDateTime,
                sellerActualAmount = request.sellerActualAmount,
                buyerActualAmount = request.buyerActualAmount,
                link = request.link
            )
            
            logger.info("Delegating to escrow controller for dispute resolution")
            
            // Delegate to the existing escrow controller resolve dispute logic
            val chainResponse = escrowController.resolveDispute(resolveDisputeRequest, httpServletRequest)
            
            // Convert the response back to AdminResolveContractResponse
            val chainResponseBody = chainResponse.body
            val adminResponse = AdminResolveContractResponse(
                success = chainResponseBody?.success ?: false,
                transactionHash = chainResponseBody?.transactionHash,
                error = chainResponseBody?.error
            )
            
            // Preserve the HTTP status from the chain response
            ResponseEntity.status(chainResponse.statusCode).body(adminResponse)

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

    @GetMapping("/cache/stats")
    @Operation(
        summary = "Get Multi-Level Cache Statistics (Admin Only)", 
        description = "Returns comprehensive statistics for all cache levels to monitor selective invalidation effectiveness"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cache statistics retrieved successfully"),
        ApiResponse(responseCode = "403", description = "Access denied - admin privileges required")
    ])
    fun getCacheStats(httpServletRequest: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        return try {
            val userType = httpServletRequest.getAttribute("userType") as? String
            if (userType != "admin") {
                logger.warn("Non-admin user attempted to access cache stats")
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    mapOf("error" to "Access denied - admin privileges required")
                )
            }
            
            val stats = cacheInvalidationService.getCacheStats()
            ResponseEntity.ok(stats)
            
        } catch (e: Exception) {
            logger.error("Error retrieving cache statistics", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf("error" to (e.message ?: "Failed to retrieve cache statistics"))
            )
        }
    }

    @GetMapping("/cache/analysis")
    @Operation(
        summary = "Analyze Cache Effectiveness (Admin Only)",
        description = "Provides detailed analysis of cache effectiveness and selective invalidation status for debugging cache issues"
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Cache analysis completed successfully"),
        ApiResponse(responseCode = "403", description = "Access denied - admin privileges required")
    ])
    fun analyzeCacheEffectiveness(httpServletRequest: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        return try {
            val userType = httpServletRequest.getAttribute("userType") as? String
            if (userType != "admin") {
                logger.warn("Non-admin user attempted to access cache analysis")
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    mapOf("error" to "Access denied - admin privileges required")
                )
            }
            
            val analysis = cacheInvalidationService.analyzeCacheEffectiveness()
            ResponseEntity.ok(analysis)
            
        } catch (e: Exception) {
            logger.error("Error analyzing cache effectiveness", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf("error" to (e.message ?: "Failed to analyze cache effectiveness"))
            )
        }
    }
}