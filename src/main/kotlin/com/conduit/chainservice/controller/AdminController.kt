package com.conduit.chainservice.controller

import com.conduit.chainservice.escrow.EscrowController
import com.conduit.chainservice.escrow.models.*
import com.conduit.chainservice.service.ContractQueryService
import com.conduit.chainservice.service.CacheInvalidationService
import com.conduit.chainservice.service.StateAwareCacheInvalidationService
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
    private val stateAwareCacheInvalidationService: StateAwareCacheInvalidationService,
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

    @PostMapping("/cache/invalidate")
    @Operation(
        summary = "Force Cache Invalidation for Contract",
        description = "Forces cache invalidation for a specific contract address. This removes all cached data for the contract across all cache levels, forcing fresh blockchain queries on next access. The intelligent invalidation system will skip immutable contracts that cannot change. Available to all authenticated users."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Cache invalidated successfully",
            content = [Content(schema = Schema(implementation = InvalidateCacheResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid contract address format",
            content = [Content(schema = Schema(implementation = InvalidateCacheResponse::class))]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Authentication required"
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = InvalidateCacheResponse::class))]
        )
    ])
    fun invalidateContractCache(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Invalidate Cache Example",
                    value = """{"contractAddress": "0x1234567890abcdef1234567890abcdef12345678", "reason": "Force refresh after manual state change"}"""
                )]
            )]
        )
        request: InvalidateCacheRequest,
        httpServletRequest: HttpServletRequest
    ): ResponseEntity<InvalidateCacheResponse> {
        return try {
            // Just verify user is authenticated (any user can invalidate cache)
            val userId = httpServletRequest.getAttribute("userId") as? String
            val userType = httpServletRequest.getAttribute("userType") as? String
            
            if (userId == null) {
                logger.warn("Unauthenticated user attempted to invalidate cache for contract: ${request.contractAddress}")
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    InvalidateCacheResponse(
                        success = false,
                        message = "Authentication required",
                        contractAddress = request.contractAddress,
                        cachesInvalidated = emptyList(),
                        error = "User must be authenticated"
                    )
                )
            }
            
            logger.info("Cache invalidation request for contract: ${request.contractAddress}, user: $userId (type: $userType), reason: ${request.reason}")
            
            // Validate contract address format
            if (!request.contractAddress.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
                return ResponseEntity.badRequest().body(
                    InvalidateCacheResponse(
                        success = false,
                        message = "Invalid contract address format",
                        contractAddress = request.contractAddress,
                        cachesInvalidated = emptyList(),
                        error = "Contract address must be a 42 character hex string starting with 0x"
                    )
                )
            }
            
            // Track which caches were invalidated
            val invalidatedCaches = mutableListOf<String>()
            
            // Use state-aware invalidation (intelligent)
            stateAwareCacheInvalidationService.invalidateContractCacheIntelligently(
                contractAddress = request.contractAddress,
                operationType = "admin_force_invalidation",
                newStatus = null,
                transactionHash = null
            )
            
            // Track what was invalidated (this is approximate since the service doesn't return details)
            invalidatedCaches.addAll(listOf(
                "contractInfoMutable",
                "contractStateMutable",
                "transactionData"
            ))
            
            // Also use legacy service for complete invalidation (belt and suspenders approach)
            cacheInvalidationService.invalidateContractCache(
                contractAddress = request.contractAddress,
                operationType = "admin_force_invalidation_legacy",
                transactionHash = null
            )
            
            val response = InvalidateCacheResponse(
                success = true,
                message = "Cache invalidated successfully for contract ${request.contractAddress}",
                contractAddress = request.contractAddress,
                cachesInvalidated = invalidatedCaches,
                error = null
            )
            
            logger.info("Successfully invalidated cache for contract: ${request.contractAddress}, user: $userId, caches cleared: ${invalidatedCaches.joinToString()}")
            ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("Error invalidating cache for contract: ${request.contractAddress}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                InvalidateCacheResponse(
                    success = false,
                    message = "Failed to invalidate cache",
                    contractAddress = request.contractAddress,
                    cachesInvalidated = emptyList(),
                    error = e.message ?: "Internal server error"
                )
            )
        }
    }

    @PostMapping("/cache/invalidate/batch")
    @Operation(
        summary = "Force Cache Invalidation for Multiple Contracts (Admin Only)",
        description = "Forces cache invalidation for multiple contract addresses in a single operation. Useful for bulk cache refresh operations."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Batch cache invalidation completed",
            content = [Content(schema = Schema(implementation = BatchInvalidateCacheResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = [Content(schema = Schema(implementation = BatchInvalidateCacheResponse::class))]
        ),
        ApiResponse(
            responseCode = "403",
            description = "Access denied - admin privileges required"
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = BatchInvalidateCacheResponse::class))]
        )
    ])
    fun invalidateBatchContractCache(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Batch Invalidate Cache Example",
                    value = """{"contractAddresses": ["0x1234567890abcdef1234567890abcdef12345678", "0xabcdef1234567890abcdef1234567890abcdef12"], "reason": "Bulk refresh after system update"}"""
                )]
            )]
        )
        request: BatchInvalidateCacheRequest,
        httpServletRequest: HttpServletRequest
    ): ResponseEntity<BatchInvalidateCacheResponse> {
        return try {
            // Verify admin access
            val userType = httpServletRequest.getAttribute("userType") as? String
            if (userType != "admin") {
                logger.warn("Non-admin user attempted batch cache invalidation")
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    BatchInvalidateCacheResponse(
                        success = false,
                        message = "Access denied - admin privileges required",
                        totalRequested = request.contractAddresses.size,
                        totalInvalidated = 0,
                        failedAddresses = request.contractAddresses,
                        error = "Admin access required"
                    )
                )
            }
            
            logger.info("Admin batch cache invalidation request for ${request.contractAddresses.size} contracts, reason: ${request.reason}")
            
            // Validate all addresses first
            val invalidAddresses = request.contractAddresses.filterNot { 
                it.matches(Regex("^0x[a-fA-F0-9]{40}$")) 
            }
            
            if (invalidAddresses.isNotEmpty()) {
                return ResponseEntity.badRequest().body(
                    BatchInvalidateCacheResponse(
                        success = false,
                        message = "Invalid contract address format in batch",
                        totalRequested = request.contractAddresses.size,
                        totalInvalidated = 0,
                        failedAddresses = invalidAddresses,
                        error = "Some addresses have invalid format: ${invalidAddresses.joinToString()}"
                    )
                )
            }
            
            if (request.contractAddresses.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    BatchInvalidateCacheResponse(
                        success = false,
                        message = "No contract addresses provided",
                        totalRequested = 0,
                        totalInvalidated = 0,
                        failedAddresses = emptyList(),
                        error = "Contract addresses list cannot be empty"
                    )
                )
            }
            
            if (request.contractAddresses.size > 100) {
                return ResponseEntity.badRequest().body(
                    BatchInvalidateCacheResponse(
                        success = false,
                        message = "Too many contracts in batch",
                        totalRequested = request.contractAddresses.size,
                        totalInvalidated = 0,
                        failedAddresses = request.contractAddresses,
                        error = "Cannot invalidate more than 100 contracts at once"
                    )
                )
            }
            
            val failedAddresses = mutableListOf<String>()
            var successCount = 0
            
            // Process each contract
            request.contractAddresses.forEach { contractAddress ->
                try {
                    // Use both services for complete invalidation
                    stateAwareCacheInvalidationService.invalidateContractCacheIntelligently(
                        contractAddress = contractAddress,
                        operationType = "admin_batch_invalidation",
                        newStatus = null,
                        transactionHash = null
                    )
                    
                    cacheInvalidationService.invalidateContractCache(
                        contractAddress = contractAddress,
                        operationType = "admin_batch_invalidation_legacy",
                        transactionHash = null
                    )
                    
                    successCount++
                } catch (e: Exception) {
                    logger.error("Failed to invalidate cache for contract $contractAddress in batch", e)
                    failedAddresses.add(contractAddress)
                }
            }
            
            val response = BatchInvalidateCacheResponse(
                success = failedAddresses.isEmpty(),
                message = if (failedAddresses.isEmpty()) {
                    "Successfully invalidated cache for all ${request.contractAddresses.size} contracts"
                } else {
                    "Partially completed: $successCount succeeded, ${failedAddresses.size} failed"
                },
                totalRequested = request.contractAddresses.size,
                totalInvalidated = successCount,
                failedAddresses = failedAddresses,
                error = if (failedAddresses.isNotEmpty()) "Some contracts failed to invalidate" else null
            )
            
            logger.info("Batch cache invalidation completed: $successCount/${request.contractAddresses.size} successful")
            ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("Error in batch cache invalidation", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                BatchInvalidateCacheResponse(
                    success = false,
                    message = "Failed to process batch invalidation",
                    totalRequested = request.contractAddresses.size,
                    totalInvalidated = 0,
                    failedAddresses = request.contractAddresses,
                    error = e.message ?: "Internal server error"
                )
            )
        }
    }

    @DeleteMapping("/cache/all")
    @Operation(
        summary = "Clear All Contract Caches (Admin Only - Use with Caution)",
        description = "Completely clears all contract-related caches. This is a destructive operation that will cause temporary performance degradation as all data must be re-fetched from the blockchain. Use only in emergency situations."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "All caches cleared successfully"
        ),
        ApiResponse(
            responseCode = "403",
            description = "Access denied - admin privileges required"
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    ])
    fun clearAllCaches(
        @RequestParam(required = false) 
        @Parameter(description = "Reason for clearing all caches")
        reason: String?,
        httpServletRequest: HttpServletRequest
    ): ResponseEntity<Map<String, Any>> {
        return try {
            // Verify admin access
            val userType = httpServletRequest.getAttribute("userType") as? String
            if (userType != "admin") {
                logger.warn("Non-admin user attempted to clear all caches")
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    mapOf("error" to "Access denied - admin privileges required")
                )
            }
            
            val clearReason = reason ?: "Admin requested full cache clear"
            logger.warn("CLEARING ALL CACHES - Reason: $clearReason, Admin: ${httpServletRequest.getAttribute("userId")}")
            
            // Use legacy service to clear all caches
            cacheInvalidationService.invalidateAllContractCache(clearReason)
            
            val response = mapOf(
                "success" to true,
                "message" to "All contract caches have been cleared",
                "reason" to clearReason,
                "warning" to "This may cause temporary performance degradation as data is re-fetched from blockchain",
                "timestamp" to System.currentTimeMillis()
            )
            
            logger.info("All caches cleared successfully by admin")
            ResponseEntity.ok(response)
            
        } catch (e: Exception) {
            logger.error("Error clearing all caches", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "success" to false,
                    "error" to (e.message ?: "Failed to clear all caches")
                )
            )
        }
    }
}