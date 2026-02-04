package com.conduit.chainservice.escrow

import com.conduit.chainservice.escrow.models.VoteResponse
import com.conduit.chainservice.escrow.models.VoteSubmitRequest
import com.conduit.chainservice.service.StateAwareCacheInvalidationService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.utils.Numeric
import java.math.BigInteger

@Service
class VoteService(
    private val web3j: Web3j,
    private val relayerCredentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val chainId: Long,
    private val gasPayerServiceClient: com.conduit.chainservice.service.GasPayerServiceClient,
    private val cacheInvalidationService: StateAwareCacheInvalidationService,
    @Value("\${escrow.gas-multiplier}") private val gasMultiplier: Double
) {
    private val logger = LoggerFactory.getLogger(VoteService::class.java)

    /**
     * Submits an admin vote on a disputed contract using the 2-of-3 voting mechanism.
     * This is called by contractservice when a logged-in party reaches agreement off-chain.
     * The smart contract will handle all validation (disputed state, consensus checking, etc.)
     */
    suspend fun submitVote(request: VoteSubmitRequest): VoteResponse {
        return try {
            val contractAddress = request.contractAddress
            val buyerPercentage = BigInteger.valueOf(request.buyerPercentage.toLong())

            logger.info("Submitting admin vote for contract: $contractAddress, buyerPercentage: ${request.buyerPercentage}%")

            // Validate percentage is within valid range (0-100)
            if (request.buyerPercentage < 0 || request.buyerPercentage > 100) {
                logger.error("Invalid buyer percentage: ${request.buyerPercentage}. Must be between 0 and 100.")
                return VoteResponse(
                    success = false,
                    transactionHash = null,
                    votedPercentage = null,
                    error = "Buyer percentage must be between 0 and 100"
                )
            }

            // Get current nonce for the relayer (admin) account
            val nonce = web3j.ethGetTransactionCount(
                relayerCredentials.address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount

            val gasPrice = gasProvider.getGasPrice("submitResolutionVote")

            // Build function call for submitResolutionVote(uint256 _buyerPercentage)
            val function = Function(
                "submitResolutionVote",
                listOf(Uint256(buyerPercentage)),
                emptyList()
            )

            val functionData = FunctionEncoder.encode(function)

            // Estimate gas with buffer
            val gasLimit = estimateGasWithBuffer(
                relayerCredentials.address,
                contractAddress,
                functionData,
                "submitResolutionVote"
            )

            logger.debug("Vote submission gas settings: gasPrice=$gasPrice, gasLimit=$gasLimit")

            // Create raw transaction
            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                contractAddress,
                BigInteger.ZERO,
                functionData
            )

            // Sign transaction with admin credentials
            val signedTransaction = org.web3j.crypto.TransactionEncoder.signMessage(
                rawTransaction,
                chainId,
                relayerCredentials
            )

            // Send the signed transaction via gas-payer-service
            val signedTransactionHex = Numeric.toHexString(signedTransaction)
            val gasPayerResult = gasPayerServiceClient.processTransactionWithGasTransfer(
                relayerCredentials.address, // Use relayer address as userWalletAddress since we're paying our own gas
                signedTransactionHex,
                "submitResolutionVote",
                gasLimit
            )

            if (!gasPayerResult.success) {
                logger.error("Vote submission failed via gas-payer-service: ${gasPayerResult.error}")
                return VoteResponse(
                    success = false,
                    transactionHash = gasPayerResult.transactionHash,
                    votedPercentage = null,
                    error = gasPayerResult.error
                )
            }

            val txHash = gasPayerResult.transactionHash!!
            logger.info("Vote submission transaction sent via gas-payer-service: $txHash")

            // Wait for transaction receipt
            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                logger.info("Admin vote submitted successfully for contract $contractAddress with buyerPercentage: ${request.buyerPercentage}%")

                // Invalidate cache - the contract might reach consensus and resolve
                cacheInvalidationService.invalidateContractCacheIntelligently(
                    contractAddress = contractAddress,
                    operationType = "submitResolutionVote",
                    newStatus = com.conduit.chainservice.escrow.models.ContractStatus.DISPUTED, // May change to RESOLVED if consensus reached
                    transactionHash = txHash
                )

                VoteResponse(
                    success = true,
                    transactionHash = txHash,
                    votedPercentage = request.buyerPercentage
                )
            } else {
                logger.error("Vote submission transaction failed. Status: ${receipt?.status}")
                VoteResponse(
                    success = false,
                    transactionHash = txHash,
                    votedPercentage = null,
                    error = "Vote submission transaction failed"
                )
            }

        } catch (e: Exception) {
            logger.error("Error submitting vote for contract: ${request.contractAddress}", e)
            VoteResponse(
                success = false,
                transactionHash = null,
                votedPercentage = null,
                error = e.message ?: "Unknown error occurred while submitting vote"
            )
        }
    }

    /**
     * Estimates gas for a transaction with a safety buffer
     * Falls back to configured gas limit if estimation fails
     */
    private fun estimateGasWithBuffer(
        from: String,
        to: String,
        functionData: String,
        fallbackOperation: String
    ): BigInteger {
        return try {
            val estimation = web3j.ethEstimateGas(
                org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
                    from,
                    null, // nonce not needed for estimation
                    null, // gas price not needed for estimation
                    null, // gas limit not needed for estimation
                    to,
                    BigInteger.ZERO,
                    functionData
                )
            ).send().amountUsed

            // Add 20% buffer to the estimate for safety
            val bufferedEstimate = estimation.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100))
            logger.info("Gas estimation for $fallbackOperation: estimated=$estimation, with buffer=$bufferedEstimate")
            bufferedEstimate
        } catch (e: Exception) {
            logger.warn("Failed to estimate gas for $fallbackOperation, falling back to configured limit: ${e.message}")
            // Use a reasonable default gas limit for vote submission (similar to resolveDispute)
            val baseLimit = 300000L // Conservative estimate for vote submission
            BigInteger.valueOf((baseLimit * gasMultiplier).toLong())
        }
    }

    /**
     * Waits for transaction receipt with polling
     */
    private suspend fun waitForTransactionReceipt(transactionHash: String): org.web3j.protocol.core.methods.response.TransactionReceipt? {
        return try {
            var attempts = 0
            val maxAttempts = 60 // 60 attempts with 2 second delays = 2 minutes max wait

            while (attempts < maxAttempts) {
                val receipt = web3j.ethGetTransactionReceipt(transactionHash).send()
                if (receipt.result != null) {
                    return receipt.result
                }
                kotlinx.coroutines.delay(2000) // Wait 2 seconds
                attempts++
            }

            logger.warn("Transaction receipt not found after $maxAttempts attempts for hash: $transactionHash")
            null
        } catch (e: Exception) {
            logger.error("Error waiting for transaction receipt: $transactionHash", e)
            null
        }
    }
}
