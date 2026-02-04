package com.conduit.chainservice.escrow

import com.conduit.chainservice.auth.ServiceApiKeyValidator
import com.conduit.chainservice.escrow.models.VoteResponse
import com.conduit.chainservice.escrow.models.VoteSubmitRequest
import io.swagger.v3.oas.annotations.Operation
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
@RequestMapping("/api/vote")
@Validated
@Tag(name = "Vote Service", description = "2-of-3 voting resolution system for dispute resolution")
class VoteController(
    private val voteService: VoteService,
    private val serviceApiKeyValidator: ServiceApiKeyValidator
) {

    private val logger = LoggerFactory.getLogger(VoteController::class.java)

    @PostMapping("/submit")
    @Operation(
        summary = "Submit Admin Vote",
        description = "Submits an admin vote on a disputed contract using the 2-of-3 voting mechanism. This endpoint requires service-to-service authentication via X-API-Key header and must be called from contractservice only."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200",
            description = "Vote submitted successfully",
            content = [Content(schema = Schema(implementation = VoteResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid request or vote submission failed",
            content = [Content(schema = Schema(implementation = VoteResponse::class))]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Authentication failed - missing or invalid X-API-Key header",
            content = [Content(schema = Schema(implementation = VoteResponse::class))]
        ),
        ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = [Content(schema = Schema(implementation = VoteResponse::class))]
        )
    ])
    fun submitVote(
        @RequestHeader("X-API-Key", required = false) apiKey: String?,
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = [Content(
                examples = [ExampleObject(
                    name = "Submit Vote Example",
                    value = """{"contractAddress": "0x1234567890abcdef1234567890abcdef12345678", "buyerPercentage": 60}"""
                )]
            )]
        )
        request: VoteSubmitRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<VoteResponse> {
        return try {
            // STEP 1: Validate service-to-service API key
            if (!serviceApiKeyValidator.validateContractServiceApiKey(apiKey)) {
                logger.warn("Vote submission rejected - invalid or missing X-API-Key header")
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    VoteResponse(
                        success = false,
                        transactionHash = null,
                        votedPercentage = null,
                        error = "Unauthorized - invalid or missing X-API-Key header"
                    )
                )
            }

            logger.info("Vote submission request received for contract: ${request.contractAddress}, buyerPercentage: ${request.buyerPercentage}%")

            // STEP 2: JWT authentication is still validated by AuthenticationFilter
            // The request must come from contractservice (service-to-service with user context)
            val userType = httpRequest.getAttribute("userType") as? String

            // Log who is making the request for audit purposes
            val userId = httpRequest.getAttribute("userId") as? String
            logger.info("Vote submission requested by userId: $userId, userType: $userType")

            // STEP 3: Submit the vote
            val result = runBlocking {
                voteService.submitVote(request)
            }

            val response = VoteResponse(
                success = result.success,
                transactionHash = result.transactionHash,
                votedPercentage = result.votedPercentage,
                error = result.error
            )

            if (result.success) {
                logger.info("Admin vote submitted successfully for contract ${request.contractAddress}: txHash=${result.transactionHash}, buyerPercentage=${request.buyerPercentage}%")
                ResponseEntity.ok(response)
            } else {
                logger.error("Vote submission failed for contract ${request.contractAddress}: ${result.error}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
            }

        } catch (e: Exception) {
            logger.error("Error in vote submission endpoint for contract: ${request.contractAddress}", e)
            val response = VoteResponse(
                success = false,
                transactionHash = null,
                votedPercentage = null,
                error = e.message ?: "Internal server error"
            )
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
        }
    }
}
