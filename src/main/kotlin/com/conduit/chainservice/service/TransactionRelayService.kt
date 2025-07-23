package com.conduit.chainservice.service

import com.conduit.chainservice.config.BlockchainProperties
import com.conduit.chainservice.model.ContractCreationResult
import com.conduit.chainservice.model.TransactionResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionDecoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.utils.Numeric
import java.math.BigInteger

@Service
class TransactionRelayService(
    private val web3j: Web3j,
    private val relayerCredentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val blockchainProperties: BlockchainProperties
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

    suspend fun createContract(signedTransaction: String): ContractCreationResult {
        return try {
            logger.info("Creating contract with transaction: ${signedTransaction.substring(0, 20)}...")

            val result = relayTransaction(signedTransaction)
            
            if (!result.success) {
                return ContractCreationResult(
                    success = false,
                    transactionHash = result.transactionHash,
                    contractAddress = null,
                    error = result.error
                )
            }

            val receipt = web3j.ethGetTransactionReceipt(result.transactionHash!!).send()
            val contractAddress = receipt.result?.contractAddress

            if (contractAddress != null) {
                logger.info("Contract created successfully at address: $contractAddress")
                ContractCreationResult(
                    success = true,
                    transactionHash = result.transactionHash,
                    contractAddress = contractAddress
                )
            } else {
                logger.error("Contract creation failed - no contract address in receipt")
                ContractCreationResult(
                    success = false,
                    transactionHash = result.transactionHash,
                    contractAddress = null,
                    error = "Contract address not found in transaction receipt"
                )
            }

        } catch (e: Exception) {
            logger.error("Error creating contract", e)
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

            val functionData = buildResolveDisputeFunctionData(recipient)

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

    private fun buildResolveDisputeFunctionData(recipient: String): String {
        return "0xa3c11ba6" + 
               recipient.removePrefix("0x").padStart(64, '0')
    }
}