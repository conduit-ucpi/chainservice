package com.conduit.chainservice.escrow

import com.conduit.chainservice.config.EscrowBlockchainProperties
import com.conduit.chainservice.escrow.models.ContractCreationResult
import com.utility.chainservice.BlockchainRelayService
import com.utility.chainservice.models.TransactionResult
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
    private val blockchainRelayService: BlockchainRelayService,
    private val web3j: Web3j,
    private val relayerCredentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val blockchainProperties: EscrowBlockchainProperties,
    private val chainId: Long
) {

    private val logger = LoggerFactory.getLogger(EscrowTransactionService::class.java)

    suspend fun createContract(
        buyer: String,
        seller: String,
        amount: BigInteger,
        expiryTimestamp: Long,
        description: String
    ): ContractCreationResult {
        return try {
            logger.info("Creating escrow contract for buyer: $buyer, seller: $seller, amount: $amount")

            // Determine creator fee with special case logic
            val creatorFee = if (amount == BigInteger.valueOf(1000)) { // 0.001 USDC = 1000 units (6 decimals)
                BigInteger.ZERO
            } else {
                blockchainProperties.creatorFee
            }

            // Validate creator fee
            if (creatorFee >= amount) {
                logger.error("Creator fee ($creatorFee) must be less than contract amount ($amount)")
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
            val gasLimit = gasProvider.getGasLimit("createContract")

            // Build function call data for createEscrowContract with creator fee
            val function = Function(
                "createEscrowContract",
                listOf(
                    Address(buyer),
                    Address(seller), 
                    Uint256(amount),
                    Uint256(BigInteger.valueOf(expiryTimestamp)),
                    Utf8String(description),
                    Uint256(creatorFee)
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

            val receipt = blockchainRelayService.waitForTransactionReceipt(txHash)
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

            val receipt = blockchainRelayService.waitForTransactionReceipt(txHash)
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

    // Delegate gas transfer operations to generic service
    suspend fun raiseDisputeWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        return blockchainRelayService.processTransactionWithGasTransfer(userWalletAddress, signedTransactionHex, "raiseDispute")
    }

    suspend fun claimFundsWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        return blockchainRelayService.processTransactionWithGasTransfer(userWalletAddress, signedTransactionHex, "claimFunds")
    }

    suspend fun depositFundsWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        return blockchainRelayService.processTransactionWithGasTransfer(userWalletAddress, signedTransactionHex, "depositFunds")
    }

    suspend fun approveUSDCWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        return blockchainRelayService.processTransactionWithGasTransfer(userWalletAddress, signedTransactionHex, "approveUSDC")
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