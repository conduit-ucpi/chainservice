package com.conduit.chainservice.escrow

import org.springframework.beans.factory.annotation.Value
import com.conduit.chainservice.config.BlockchainProperties
import com.conduit.chainservice.escrow.models.ContractCreationResult
import com.conduit.chainservice.service.StateAwareCacheInvalidationService
import com.conduit.chainservice.model.TransactionResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.utils.Numeric
import java.math.BigInteger

@Service
class EscrowTransactionService(
    private val gasPayerServiceClient: com.conduit.chainservice.service.GasPayerServiceClient,
    private val cacheInvalidationService: StateAwareCacheInvalidationService,
    private val web3j: Web3j,
    private val relayerCredentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val chainId: Long,
    @Value("\${escrow.usdc-contract-address}") private val usdcContractAddress: String,
    @Value("\${escrow.contract-factory-address}") private val contractFactoryAddress: String,
    @Value("\${escrow.creator-fee}") private val creatorFee: String,
    @Value("\${escrow.limit-dispute}") private val limitDispute: Long,
    @Value("\${escrow.limit-claim}") private val limitClaim: Long,
    @Value("\${escrow.limit-deposit}") private val limitDeposit: Long,
    @Value("\${escrow.limit-approve-usdc}") private val limitApproveUsdc: Long,
    @Value("\${escrow.gas-multiplier}") private val gasMultiplier: Double
) {

    private val logger = LoggerFactory.getLogger(EscrowTransactionService::class.java)
    
    /**
     * Estimate gas for a transaction with a safety buffer
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
            gasProvider.getGasLimit(fallbackOperation)
        }
    }

    // Helper method to wait for transaction receipt since gas-payer-service doesn't provide this
    suspend fun waitForTransactionReceipt(transactionHash: String): TransactionReceipt? {
        return try {
            // Simple polling implementation - could be improved with exponential backoff
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

    init {
        logger.debug("EscrowTransactionService initialized with gas settings:")
        logger.debug("  - limitDispute: $limitDispute")
        logger.debug("  - limitClaim: $limitClaim")
        logger.debug("  - limitDeposit: $limitDeposit")
        logger.debug("  - limitApproveUsdc: $limitApproveUsdc")
        logger.debug("  - gasMultiplier: $gasMultiplier")
    }

    suspend fun createContract(
        tokenAddress: String,
        buyer: String,
        seller: String,
        amount: BigInteger,
        expiryTimestamp: Long,
        description: String
    ): ContractCreationResult {
        return try {
            logger.info("Creating escrow contract for token: $tokenAddress, buyer: $buyer, seller: $seller, amount: $amount")

            // Determine creator fee with special case logic
            val creatorFeeAmount = if (amount == BigInteger.valueOf(1000)) { // 0.001 USDC = 1000 units (6 decimals)
                BigInteger.ZERO
            } else {
                BigInteger(creatorFee)
            }

            // Validate creator fee
            if (creatorFeeAmount >= amount) {
                logger.error("Creator fee ($creatorFeeAmount) must be less than contract amount ($amount)")
                return ContractCreationResult(
                    success = false,
                    transactionHash = null,
                    contractAddress = null,
                    error = "Creator fee must be less than contract amount"
                )
            }

            val nonce = web3j.ethGetTransactionCount(
                relayerCredentials.address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount

            val gasPrice = gasProvider.getGasPrice("createContract")

            // Build function call data for createEscrowContract with creator fee
            val function = Function(
                "createEscrowContract",
                listOf(
                    Address(tokenAddress),
                    Address(buyer),
                    Address(seller), 
                    Uint256(amount),
                    Uint256(BigInteger.valueOf(expiryTimestamp)),
                    Utf8String(description),
                    Uint256(creatorFeeAmount)
                ),
                emptyList()
            )

            val functionData = FunctionEncoder.encode(function)
            
            // Estimate gas for the contract creation transaction
            val gasLimit = estimateGasWithBuffer(
                relayerCredentials.address,
                contractFactoryAddress,
                functionData,
                "createContract"
            )
            
            logger.debug("createContract gas settings: gasPrice=$gasPrice, gasLimit=$gasLimit, totalGasCost=${gasPrice.multiply(gasLimit)}")

            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                contractFactoryAddress,
                BigInteger.ZERO,
                functionData
            )

            val signedTransaction = org.web3j.crypto.TransactionEncoder.signMessage(
                rawTransaction,
                chainId,
                relayerCredentials
            )

            // Send the signed transaction to gas-payer-service
            val signedTransactionHex = Numeric.toHexString(signedTransaction)
            val gasPayerResult = gasPayerServiceClient.processTransactionWithGasTransfer(
                relayerCredentials.address, // Use relayer address as userWalletAddress since we're paying our own gas
                signedTransactionHex,
                "createContract",
                gasLimit
            )

            if (!gasPayerResult.success) {
                logger.error("Contract creation failed via gas-payer-service: ${gasPayerResult.error}")
                return ContractCreationResult(
                    success = false,
                    transactionHash = gasPayerResult.transactionHash,
                    contractAddress = null,
                    error = gasPayerResult.error
                )
            }

            val txHash = gasPayerResult.transactionHash!!
            logger.info("Contract creation transaction sent via gas-payer-service: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                logger.info("Transaction receipt received successfully, status: ${receipt.status}")
                logger.debug("Receipt logs count: ${receipt.logs.size}")
                
                // Parse ContractCreated event to get the contract address
                val contractAddress = parseContractAddressFromReceipt(receipt)
                
                if (contractAddress != null) {
                    logger.info("Escrow contract created successfully at address: $contractAddress")
                    
                    // Invalidate cache for the newly created contract
                    cacheInvalidationService.invalidateContractCacheIntelligently(
                        contractAddress = contractAddress,
                        operationType = "createContract",
                        newStatus = com.conduit.chainservice.escrow.models.ContractStatus.CREATED,
                        transactionHash = txHash
                    )
                    
                    ContractCreationResult(
                        success = true,
                        transactionHash = txHash,
                        contractAddress = contractAddress
                    )
                } else {
                    logger.error("Could not extract contract address from transaction receipt")
                    logger.debug("Receipt logs: ${receipt.logs.joinToString { "Topic[0]: ${it.topics.getOrNull(0)}, Data: ${it.data}" }}")
                    ContractCreationResult(
                        success = false,
                        transactionHash = txHash,
                        contractAddress = null,
                        error = "Contract address not found in transaction receipt"
                    )
                }
            } else {
                logger.error("Transaction receipt indicates failure or not found. Status: ${receipt?.status}, Receipt: ${receipt != null}")
                ContractCreationResult(
                    success = false,
                    transactionHash = txHash,
                    contractAddress = null,
                    error = "Contract creation transaction failed"
                )
            }

        } catch (e: Exception) {
            logger.error("Error creating escrow contract", e)
            ContractCreationResult(
                success = false,
                transactionHash = null,
                contractAddress = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    suspend fun resolveDispute(contractAddress: String, recipient: String): TransactionResult {
        return try {
            logger.info("Resolving dispute for contract: $contractAddress, recipient: $recipient")

            val nonce = web3j.ethGetTransactionCount(
                relayerCredentials.address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount

            val gasPrice = gasProvider.getGasPrice("resolveDispute")

            val function = Function(
                "resolveDispute",
                listOf(Address(recipient)),
                emptyList()
            )

            val functionData = FunctionEncoder.encode(function)
            
            // Estimate gas for the resolve dispute transaction
            val gasLimit = estimateGasWithBuffer(
                relayerCredentials.address,
                contractAddress,
                functionData,
                "resolveDispute"
            )

            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                contractAddress,
                BigInteger.ZERO,
                functionData
            )

            val signedTransaction = org.web3j.crypto.TransactionEncoder.signMessage(
                rawTransaction,
                chainId,
                relayerCredentials
            )

            // Send the signed transaction to gas-payer-service
            val signedTransactionHex = Numeric.toHexString(signedTransaction)
            val gasPayerResult = gasPayerServiceClient.processTransactionWithGasTransfer(
                relayerCredentials.address, // Use relayer address as userWalletAddress since we're paying our own gas
                signedTransactionHex,
                "resolveDispute",
                gasLimit
            )

            if (!gasPayerResult.success) {
                logger.error("Dispute resolution failed via gas-payer-service: ${gasPayerResult.error}")
                return TransactionResult(
                    success = false,
                    transactionHash = gasPayerResult.transactionHash,
                    error = gasPayerResult.error
                )
            }

            val txHash = gasPayerResult.transactionHash!!
            logger.info("Dispute resolution transaction sent via gas-payer-service: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                logger.info("Dispute resolved successfully")
                
                // Invalidate cache for the contract whose dispute was resolved
                cacheInvalidationService.invalidateContractCacheIntelligently(
                    contractAddress = contractAddress,
                    operationType = "resolveDispute",
                    newStatus = com.conduit.chainservice.escrow.models.ContractStatus.RESOLVED,
                    transactionHash = txHash
                )
                
                TransactionResult(
                    success = true,
                    transactionHash = txHash
                )
            } else {
                TransactionResult(
                    success = false,
                    transactionHash = txHash,
                    error = "Dispute resolution transaction failed"
                )
            }

        } catch (e: Exception) {
            logger.error("Error resolving dispute", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    suspend fun resolveDisputeWithPercentages(contractAddress: String, buyerPercentage: Double, sellerPercentage: Double): TransactionResult {
        return try {
            logger.info("Resolving dispute with percentages for contract: $contractAddress, buyer: $buyerPercentage%, seller: $sellerPercentage%")

            // Validate percentages
            if (buyerPercentage < 0 || sellerPercentage < 0) {
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = "Percentages cannot be negative"
                )
            }
            
            if (Math.abs(buyerPercentage + sellerPercentage - 100.0) > 0.01) {
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = "Percentages must sum to 100"
                )
            }

            val nonce = web3j.ethGetTransactionCount(
                relayerCredentials.address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount

            val gasPrice = gasProvider.getGasPrice("resolveDispute")

            // Convert percentages to integers (smart contract expects uint256)
            val buyerPercentageInt = buyerPercentage.toBigDecimal().toBigInteger()
            val sellerPercentageInt = sellerPercentage.toBigDecimal().toBigInteger()

            val function = Function(
                "resolveDispute",
                listOf(
                    org.web3j.abi.datatypes.generated.Uint256(buyerPercentageInt),
                    org.web3j.abi.datatypes.generated.Uint256(sellerPercentageInt)
                ),
                emptyList()
            )

            val functionData = FunctionEncoder.encode(function)
            
            // Estimate gas for the resolve dispute transaction
            val gasLimit = estimateGasWithBuffer(
                relayerCredentials.address,
                contractAddress,
                functionData,
                "resolveDispute"
            )

            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                contractAddress,
                BigInteger.ZERO,
                functionData
            )

            val signedTransaction = org.web3j.crypto.TransactionEncoder.signMessage(
                rawTransaction,
                chainId,
                relayerCredentials
            )

            // Send the signed transaction to gas-payer-service
            val signedTransactionHex = Numeric.toHexString(signedTransaction)
            val gasPayerResult = gasPayerServiceClient.processTransactionWithGasTransfer(
                relayerCredentials.address, // Use relayer address as userWalletAddress since we're paying our own gas
                signedTransactionHex,
                "resolveDisputeWithPercentages",
                gasLimit
            )

            if (!gasPayerResult.success) {
                logger.error("Percentage-based dispute resolution failed via gas-payer-service: ${gasPayerResult.error}")
                return TransactionResult(
                    success = false,
                    transactionHash = gasPayerResult.transactionHash,
                    error = gasPayerResult.error
                )
            }

            val txHash = gasPayerResult.transactionHash!!
            logger.info("Percentage-based dispute resolution transaction sent via gas-payer-service: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                logger.info("Dispute resolved successfully with percentages")
                
                // Invalidate cache for the contract whose dispute was resolved
                cacheInvalidationService.invalidateContractCacheIntelligently(
                    contractAddress = contractAddress,
                    operationType = "resolveDisputeWithPercentages",
                    newStatus = com.conduit.chainservice.escrow.models.ContractStatus.RESOLVED,
                    transactionHash = txHash
                )
                
                TransactionResult(
                    success = true,
                    transactionHash = txHash
                )
            } else {
                TransactionResult(
                    success = false,
                    transactionHash = txHash,
                    error = "Percentage-based dispute resolution transaction failed"
                )
            }

        } catch (e: Exception) {
            logger.error("Error resolving dispute with percentages", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    // Delegate gas transfer operations to generic service with cache invalidation
    suspend fun raiseDisputeWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        val gasLimit = BigInteger.valueOf((limitDispute * gasMultiplier).toLong())
        logger.debug("raiseDispute gas calculation: baseLimit=$limitDispute, multiplier=$gasMultiplier, finalLimit=$gasLimit")
        
        val result = gasPayerServiceClient.processTransactionWithGasTransfer(
            userWalletAddress, 
            signedTransactionHex, 
            "raiseDispute",
            gasLimit
        )
        
        // Invalidate cache on successful transaction - use contract address from result
        if (result.success && result.transactionHash != null && result.contractAddress != null) {
            cacheInvalidationService.invalidateContractCacheIntelligently(
                contractAddress = result.contractAddress!!,
                operationType = "raiseDispute",
                newStatus = com.conduit.chainservice.escrow.models.ContractStatus.DISPUTED,
                transactionHash = result.transactionHash
            )
        } else if (result.success && result.contractAddress == null) {
            logger.warn("Contract address not available from blockchain relay service for raiseDispute cache invalidation")
        }
        
        return result
    }

    suspend fun claimFundsWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        val gasLimit = BigInteger.valueOf((limitClaim * gasMultiplier).toLong())
        logger.debug("claimFunds gas calculation: baseLimit=$limitClaim, multiplier=$gasMultiplier, finalLimit=$gasLimit")
        
        val result = gasPayerServiceClient.processTransactionWithGasTransfer(
            userWalletAddress, 
            signedTransactionHex, 
            "claimFunds",
            gasLimit
        )
        
        // Invalidate cache on successful transaction - use contract address from result
        if (result.success && result.transactionHash != null && result.contractAddress != null) {
            cacheInvalidationService.invalidateContractCacheIntelligently(
                contractAddress = result.contractAddress!!,
                operationType = "claimFunds",
                newStatus = com.conduit.chainservice.escrow.models.ContractStatus.CLAIMED,
                transactionHash = result.transactionHash
            )
        } else if (result.success && result.contractAddress == null) {
            logger.warn("Contract address not available from blockchain relay service for claimFunds cache invalidation")
        }
        
        return result
    }

    suspend fun depositFundsWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        val gasLimit = BigInteger.valueOf((limitDeposit * gasMultiplier).toLong())
        logger.debug("depositFunds gas calculation: baseLimit=$limitDeposit, multiplier=$gasMultiplier, finalLimit=$gasLimit")
        
        val result = gasPayerServiceClient.processTransactionWithGasTransfer(
            userWalletAddress, 
            signedTransactionHex, 
            "depositFunds",
            gasLimit
        )
        
        // Invalidate cache on successful transaction - use contract address from result
        if (result.success && result.transactionHash != null && result.contractAddress != null) {
            cacheInvalidationService.invalidateContractCacheIntelligently(
                contractAddress = result.contractAddress!!,
                operationType = "depositFunds",
                newStatus = com.conduit.chainservice.escrow.models.ContractStatus.ACTIVE,
                transactionHash = result.transactionHash
            )
        } else if (result.success && result.contractAddress == null) {
            logger.warn("Contract address not available from blockchain relay service for depositFunds cache invalidation")
        }
        
        return result
    }

    suspend fun approveUSDCWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        val gasLimit = BigInteger.valueOf((limitApproveUsdc * gasMultiplier).toLong())
        logger.debug("approveUSDC gas calculation: baseLimit=$limitApproveUsdc, multiplier=$gasMultiplier, finalLimit=$gasLimit")
        
        val result = gasPayerServiceClient.processTransactionWithGasTransfer(
            userWalletAddress, 
            signedTransactionHex, 
            "approveUSDC",
            gasLimit
        )
        
        // For approveUSDC, we're approving the USDC contract to spend tokens on behalf of the escrow contract
        // This doesn't directly change contract state, but we might want to invalidate cache for related contracts
        // For now, we'll skip cache invalidation for approve operations as they don't change escrow contract state
        
        return result
    }

    suspend fun transferUSDCWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        val gasLimit = BigInteger.valueOf((limitApproveUsdc * gasMultiplier).toLong()) // Using same gas limit as approve since it's a similar ERC20 operation
        logger.debug("transferUSDC gas calculation: baseLimit=$limitApproveUsdc, multiplier=$gasMultiplier, finalLimit=$gasLimit")
        
        val result = gasPayerServiceClient.processTransactionWithGasTransfer(
            userWalletAddress,
            signedTransactionHex,
            "transferUSDC",
            gasLimit
        )
        
        // For transferUSDC, we're transferring tokens between wallets
        // This doesn't affect escrow contract state, so no cache invalidation needed
        
        return result
    }


    private fun parseContractAddressFromReceipt(receipt: TransactionReceipt): String? {
        return try {
            logger.debug("Parsing contract address from receipt with ${receipt.logs.size} logs")
            
            // Look for ContractCreated event signature: ContractCreated(address,address,address,uint256,uint256)
            val contractCreatedSignature = "0x" + org.web3j.crypto.Hash.sha3String("ContractCreated(address,address,address,uint256,uint256)").substring(2)
            logger.debug("Looking for ContractCreated event signature: $contractCreatedSignature")
            
            for (log in receipt.logs) {
                logger.debug("Log: topics=${log.topics.size}, data=${log.data}")
                
                if (log.topics.isNotEmpty()) {
                    val eventSignature = log.topics[0]
                    logger.debug("Event signature: $eventSignature")
                    
                    // Check if this is a ContractCreated event
                    if (eventSignature.equals(contractCreatedSignature, ignoreCase = true)) {
                        // For ContractCreated(address indexed contractAddress, address indexed buyer, address indexed seller, uint256 amount, uint256 expiryTimestamp)
                        // The contract address is the first indexed parameter (topic[1])
                        if (log.topics.size >= 2) {
                            val contractAddressTopic = log.topics[1]
                            if (contractAddressTopic.length == 66) { // 0x + 64 hex chars
                                val address = "0x" + contractAddressTopic.substring(26) // Last 20 bytes = address
                                logger.info("Extracted contract address from ContractCreated event: $address")
                                return address
                            }
                        }
                    }
                }
            }
            
            logger.warn("ContractCreated event not found in transaction receipt logs")
            null
        } catch (e: Exception) {
            logger.error("Error parsing contract address from receipt", e)
            null
        }
    }
}