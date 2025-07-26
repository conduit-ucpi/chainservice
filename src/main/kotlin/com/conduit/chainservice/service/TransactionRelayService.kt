package com.conduit.chainservice.service

import com.conduit.chainservice.config.BlockchainProperties
import com.conduit.chainservice.model.ContractCreationResult
import com.conduit.chainservice.model.OperationGasCost
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
import org.web3j.crypto.TransactionDecoder
import org.web3j.crypto.Keys
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

@Service
class TransactionRelayService(
    private val web3j: Web3j,
    private val relayerCredentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val blockchainProperties: BlockchainProperties,
    private val chainId: Long
) {

    private val logger = LoggerFactory.getLogger(TransactionRelayService::class.java)

    suspend fun relayTransaction(signedTransactionHex: String): TransactionResult {
        return try {
            logger.info("Relaying transaction: ${signedTransactionHex.substring(0, 20)}...")

            val decodedTx = TransactionDecoder.decode(signedTransactionHex)
            
            val nonce = web3j.ethGetTransactionCount(
                relayerCredentials.address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount

            val gasPrice = gasProvider.gasPrice
            val gasLimit = gasProvider.gasLimit

            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                decodedTx.to,
                decodedTx.value,
                decodedTx.data
            )

            val signedTransaction = org.web3j.crypto.TransactionEncoder.signMessage(
                rawTransaction,
                chainId,
                relayerCredentials
            )

            val transactionHash = web3j.ethSendRawTransaction(
                Numeric.toHexString(signedTransaction)
            ).send()

            if (transactionHash.hasError()) {
                logger.error("Transaction relay failed: ${transactionHash.error.message}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = transactionHash.error.message
                )
            }

            val txHash = transactionHash.transactionHash
            logger.info("Transaction relayed successfully: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                TransactionResult(
                    success = true,
                    transactionHash = txHash
                )
            } else {
                TransactionResult(
                    success = false,
                    transactionHash = txHash,
                    error = "Transaction failed on blockchain"
                )
            }

        } catch (e: Exception) {
            logger.error("Error relaying transaction", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    suspend fun createContract(
        buyer: String,
        seller: String,
        amount: BigInteger,
        expiryTimestamp: Long,
        description: String
    ): ContractCreationResult {
        return try {
            logger.info("Creating escrow contract for buyer: $buyer, seller: $seller, amount: $amount")

            val nonce = web3j.ethGetTransactionCount(
                relayerCredentials.address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount

            val gasPrice = gasProvider.getGasPrice("createContract")
            val gasLimit = gasProvider.getGasLimit("createContract")

            // Build function call data for createEscrowContract with string description
            val function = Function(
                "createEscrowContract",
                listOf(
                    Address(buyer),
                    Address(seller), 
                    Uint256(amount),
                    Uint256(BigInteger.valueOf(expiryTimestamp)),
                    Utf8String(description)
                ),
                emptyList()
            )

            val functionData = FunctionEncoder.encode(function)

            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                blockchainProperties.contractFactoryAddress,
                BigInteger.ZERO,
                functionData
            )

            val signedTransaction = org.web3j.crypto.TransactionEncoder.signMessage(
                rawTransaction,
                chainId,
                relayerCredentials
            )

            val transactionHash = web3j.ethSendRawTransaction(
                Numeric.toHexString(signedTransaction)
            ).send()

            if (transactionHash.hasError()) {
                logger.error("Contract creation failed: ${transactionHash.error.message}")
                return ContractCreationResult(
                    success = false,
                    transactionHash = null,
                    contractAddress = null,
                    error = transactionHash.error.message
                )
            }

            val txHash = transactionHash.transactionHash
            logger.info("Contract creation transaction sent: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                logger.info("Transaction receipt received successfully, status: ${receipt.status}")
                logger.debug("Receipt logs count: ${receipt.logs.size}")
                
                // Parse ContractCreated event to get the contract address
                val contractAddress = parseContractAddressFromReceipt(receipt)
                
                if (contractAddress != null) {
                    logger.info("Escrow contract created successfully at address: $contractAddress")
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
            val gasLimit = gasProvider.getGasLimit("resolveDispute")

            val function = Function(
                "resolveDispute",
                listOf(Address(recipient)),
                emptyList()
            )

            val functionData = FunctionEncoder.encode(function)

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

            val transactionHash = web3j.ethSendRawTransaction(
                Numeric.toHexString(signedTransaction)
            ).send()

            if (transactionHash.hasError()) {
                logger.error("Dispute resolution failed: ${transactionHash.error.message}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = transactionHash.error.message
                )
            }

            val txHash = transactionHash.transactionHash
            logger.info("Dispute resolution transaction sent: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                logger.info("Dispute resolved successfully")
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

    private suspend fun waitForTransactionReceipt(transactionHash: String): TransactionReceipt? {
        return try {
            var attempts = 0
            val maxAttempts = 30

            while (attempts < maxAttempts) {
                val receiptResponse = web3j.ethGetTransactionReceipt(transactionHash).send()
                val receipt = receiptResponse.result

                if (receipt != null) {
                    return receipt
                }

                Thread.sleep(2000)
                attempts++
            }

            logger.warn("Transaction receipt not found after $maxAttempts attempts for tx: $transactionHash")
            null
        } catch (e: Exception) {
            logger.error("Error waiting for transaction receipt", e)
            null
        }
    }

    suspend fun raiseDispute(contractAddress: String): TransactionResult {
        return try {
            logger.info("Raising dispute for contract: $contractAddress")

            val nonce = web3j.ethGetTransactionCount(
                relayerCredentials.address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount

            val gasPrice = gasProvider.getGasPrice("raiseDispute")
            val gasLimit = gasProvider.getGasLimit("raiseDispute")

            val function = Function(
                "raiseDispute",
                emptyList(),
                emptyList()
            )

            val functionData = FunctionEncoder.encode(function)

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

            val transactionHash = web3j.ethSendRawTransaction(
                Numeric.toHexString(signedTransaction)
            ).send()

            if (transactionHash.hasError()) {
                logger.error("Dispute raising failed: ${transactionHash.error.message}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = transactionHash.error.message
                )
            }

            val txHash = transactionHash.transactionHash
            logger.info("Dispute raising transaction sent: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                logger.info("Dispute raised successfully")
                TransactionResult(
                    success = true,
                    transactionHash = txHash
                )
            } else {
                TransactionResult(
                    success = false,
                    transactionHash = txHash,
                    error = "Dispute raising transaction failed"
                )
            }

        } catch (e: Exception) {
            logger.error("Error raising dispute", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    suspend fun claimFunds(contractAddress: String): TransactionResult {
        return try {
            logger.info("Claiming funds for contract: $contractAddress")

            val nonce = web3j.ethGetTransactionCount(
                relayerCredentials.address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount

            val gasPrice = gasProvider.getGasPrice("claimFunds")
            val gasLimit = gasProvider.getGasLimit("claimFunds")

            val function = Function(
                "claimFunds",
                emptyList(),
                emptyList()
            )

            val functionData = FunctionEncoder.encode(function)

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

            val transactionHash = web3j.ethSendRawTransaction(
                Numeric.toHexString(signedTransaction)
            ).send()

            if (transactionHash.hasError()) {
                logger.error("Funds claiming failed: ${transactionHash.error.message}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = transactionHash.error.message
                )
            }

            val txHash = transactionHash.transactionHash
            logger.info("Funds claiming transaction sent: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                logger.info("Funds claimed successfully")
                TransactionResult(
                    success = true,
                    transactionHash = txHash
                )
            } else {
                TransactionResult(
                    success = false,
                    transactionHash = txHash,
                    error = "Funds claiming transaction failed"
                )
            }

        } catch (e: Exception) {
            logger.error("Error claiming funds", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    suspend fun depositFunds(contractAddress: String): TransactionResult {
        return try {
            logger.info("Depositing funds for contract: $contractAddress")

            val nonce = web3j.ethGetTransactionCount(
                relayerCredentials.address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount

            val gasPrice = gasProvider.getGasPrice("depositFunds")
            val gasLimit = gasProvider.getGasLimit("depositFunds")

            val function = Function(
                "depositFunds",
                emptyList(),
                emptyList()
            )

            val functionData = FunctionEncoder.encode(function)

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

            val transactionHash = web3j.ethSendRawTransaction(
                Numeric.toHexString(signedTransaction)
            ).send()

            if (transactionHash.hasError()) {
                logger.error("Funds deposit failed: ${transactionHash.error.message}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = transactionHash.error.message
                )
            }

            val txHash = transactionHash.transactionHash
            logger.info("Funds deposit transaction sent: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                logger.info("Funds deposited successfully")
                TransactionResult(
                    success = true,
                    transactionHash = txHash
                )
            } else {
                TransactionResult(
                    success = false,
                    transactionHash = txHash,
                    error = "Funds deposit transaction failed"
                )
            }

        } catch (e: Exception) {
            logger.error("Error depositing funds", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
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

    fun getOperationGasCosts(): List<OperationGasCost> {
        val operations = listOf(
            "createContract" to "createContract",
            "approveUSDC" to "approveUSDC",
            "depositFunds" to "depositFunds", 
            "raiseDispute" to "raiseDispute",
            "claimFunds" to "claimFunds",
            "resolveDispute" to "resolveDispute"
        )

        return operations.map { (operation, gasFunction) ->
            val gasLimit = gasProvider.getGasLimit(gasFunction).toLong()
            val gasPrice = gasProvider.getGasPrice(gasFunction)
            val totalCostWei = gasPrice.multiply(BigInteger.valueOf(gasLimit))
            val totalCostAvax = weiToAvax(totalCostWei)

            OperationGasCost(
                operation = operation,
                gasLimit = gasLimit,
                gasPriceWei = gasPrice,
                totalCostWei = totalCostWei,
                totalCostAvax = totalCostAvax
            )
        }
    }

    private fun weiToAvax(wei: BigInteger): String {
        val weiDecimal = BigDecimal(wei)
        val avaxDecimal = weiDecimal.divide(BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP)
        return avaxDecimal.stripTrailingZeros().toPlainString()
    }


    private suspend fun transferGasToUser(userAddress: String, gasAmount: BigInteger): TransactionResult {
        return try {
            logger.info("Transferring $gasAmount wei to user: $userAddress")
            
            val nonce = web3j.ethGetTransactionCount(
                relayerCredentials.address,
                DefaultBlockParameterName.PENDING
            ).send().transactionCount
            
            val gasPrice = gasProvider.gasPrice
            val gasLimit = BigInteger.valueOf(21000) // Standard gas limit for ETH transfer

            val rawTransaction = RawTransaction.createEtherTransaction(
                nonce,
                gasPrice,
                gasLimit,
                userAddress,
                gasAmount
            )

            val signedTransaction = org.web3j.crypto.TransactionEncoder.signMessage(
                rawTransaction,
                chainId,
                relayerCredentials
            )

            val transactionHash = web3j.ethSendRawTransaction(
                Numeric.toHexString(signedTransaction)
            ).send()

            if (transactionHash.hasError()) {
                logger.error("Gas transfer failed: ${transactionHash.error.message}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = transactionHash.error.message
                )
            }

            val txHash = transactionHash.transactionHash
            logger.info("Gas transfer transaction sent: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                logger.info("Gas transferred successfully to user")
                TransactionResult(
                    success = true,
                    transactionHash = txHash
                )
            } else {
                TransactionResult(
                    success = false,
                    transactionHash = txHash,
                    error = "Gas transfer transaction failed"
                )
            }

        } catch (e: Exception) {
            logger.error("Error transferring gas to user", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    suspend fun depositFundsWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        return try {
            logger.info("Processing deposit funds with gas transfer for user: $userWalletAddress")

            // Extract gas values from user's signed transaction
            val decodedTx = TransactionDecoder.decode(signedTransactionHex)
            val userGasLimit = decodedTx.gasLimit
            val userGasPrice = decodedTx.gasPrice
            val totalGasCost = userGasPrice.multiply(userGasLimit)
            
            logger.info("User's transaction gas parameters: gasLimit=$userGasLimit, gasPrice=$userGasPrice wei, totalCost=$totalGasCost wei")

            // Check user's current AVAX balance
            val currentBalance = web3j.ethGetBalance(userWalletAddress, DefaultBlockParameterName.LATEST).send().balance
            
            // Only transfer gas if user doesn't have enough
            if (currentBalance < totalGasCost) {
                val gasNeeded = totalGasCost.subtract(currentBalance)
                logger.info("User has $currentBalance wei, needs $totalGasCost wei, transferring $gasNeeded wei")
                
                val gasTransferResult = transferGasToUser(userWalletAddress, gasNeeded)
                if (!gasTransferResult.success) {
                    logger.error("Failed to transfer gas to user: ${gasTransferResult.error}")
                    return TransactionResult(
                        success = false,
                        transactionHash = null,
                        error = "Failed to transfer gas to user: ${gasTransferResult.error}"
                    )
                }
            } else {
                logger.info("User already has sufficient balance ($currentBalance wei >= $totalGasCost wei), skipping gas transfer")
            }

            // Forward the original signed transaction unchanged
            val transactionHash = web3j.ethSendRawTransaction(signedTransactionHex).send()

            if (transactionHash.hasError()) {
                logger.error("Transaction forwarding failed: ${transactionHash.error.message}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = transactionHash.error.message
                )
            }

            val txHash = transactionHash.transactionHash
            logger.info("Transaction forwarded successfully: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                TransactionResult(
                    success = true,
                    transactionHash = txHash
                )
            } else {
                TransactionResult(
                    success = false,
                    transactionHash = txHash,
                    error = "Transaction failed on blockchain"
                )
            }

        } catch (e: Exception) {
            logger.error("Error processing deposit funds with gas transfer", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    suspend fun approveUSDCWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        return try {
            logger.info("Processing USDC approval with gas transfer for user: $userWalletAddress")

            // Extract gas values from user's signed transaction
            val decodedTx = TransactionDecoder.decode(signedTransactionHex)
            val userGasLimit = decodedTx.gasLimit
            val userGasPrice = decodedTx.gasPrice
            val totalGasCost = userGasPrice.multiply(userGasLimit)
            
            logger.info("User's transaction gas parameters: gasLimit=$userGasLimit, gasPrice=$userGasPrice wei, totalCost=$totalGasCost wei")

            // Check user's current AVAX balance
            val currentBalance = web3j.ethGetBalance(userWalletAddress, DefaultBlockParameterName.LATEST).send().balance
            
            // Only transfer gas if user doesn't have enough
            if (currentBalance < totalGasCost) {
                val gasNeeded = totalGasCost.subtract(currentBalance)
                logger.info("User has $currentBalance wei, needs $totalGasCost wei, transferring $gasNeeded wei")
                
                val gasTransferResult = transferGasToUser(userWalletAddress, gasNeeded)
                if (!gasTransferResult.success) {
                    logger.error("Failed to transfer gas to user: ${gasTransferResult.error}")
                    return TransactionResult(
                        success = false,
                        transactionHash = null,
                        error = "Failed to transfer gas to user: ${gasTransferResult.error}"
                    )
                }
            } else {
                logger.info("User already has sufficient balance ($currentBalance wei >= $totalGasCost wei), skipping gas transfer")
            }

            // Forward the original signed transaction unchanged
            val transactionHash = web3j.ethSendRawTransaction(signedTransactionHex).send()

            if (transactionHash.hasError()) {
                logger.error("Transaction forwarding failed: ${transactionHash.error.message}")
                return TransactionResult(
                    success = false,
                    transactionHash = null,
                    error = transactionHash.error.message
                )
            }

            val txHash = transactionHash.transactionHash
            logger.info("Transaction forwarded successfully: $txHash")

            val receipt = waitForTransactionReceipt(txHash)
            if (receipt?.isStatusOK == true) {
                TransactionResult(
                    success = true,
                    transactionHash = txHash
                )
            } else {
                TransactionResult(
                    success = false,
                    transactionHash = txHash,
                    error = "Transaction failed on blockchain"
                )
            }

        } catch (e: Exception) {
            logger.error("Error processing USDC approval with gas transfer", e)
            TransactionResult(
                success = false,
                transactionHash = null,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }
}