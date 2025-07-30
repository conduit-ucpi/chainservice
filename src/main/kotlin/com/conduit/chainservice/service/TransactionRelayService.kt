package com.conduit.chainservice.service

import com.conduit.chainservice.config.EscrowBlockchainProperties
import com.conduit.chainservice.model.ContractCreationResult
import com.conduit.chainservice.model.OperationGasCost
import com.conduit.chainservice.model.TransactionResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.gas.ContractGasProvider
import java.math.BigInteger

@Service
class TransactionRelayService(
    private val blockchainRelayService: com.utility.chainservice.BlockchainRelayService,
    private val escrowTransactionService: com.conduit.chainservice.escrow.EscrowTransactionService,
    private val web3j: Web3j,
    private val relayerCredentials: Credentials,
    private val gasProvider: ContractGasProvider,
    private val blockchainProperties: EscrowBlockchainProperties,
    private val chainId: Long
) {

    private val logger = LoggerFactory.getLogger(TransactionRelayService::class.java)

    suspend fun relayTransaction(signedTransactionHex: String): TransactionResult {
        // Delegate to generic blockchain relay service
        val genericResult = blockchainRelayService.relayTransaction(signedTransactionHex)
        // Convert to legacy TransactionResult format
        return TransactionResult(
            success = genericResult.success,
            transactionHash = genericResult.transactionHash,
            error = genericResult.error
        )
    }

    suspend fun createContract(
        buyer: String,
        seller: String,
        amount: BigInteger,
        expiryTimestamp: Long,
        description: String
    ): ContractCreationResult {
        // Delegate to escrow transaction service
        val escrowResult = escrowTransactionService.createContract(buyer, seller, amount, expiryTimestamp, description)
        // Convert to legacy ContractCreationResult format
        return ContractCreationResult(
            success = escrowResult.success,
            transactionHash = escrowResult.transactionHash,
            contractAddress = escrowResult.contractAddress,
            error = escrowResult.error
        )
    }

    suspend fun resolveDispute(contractAddress: String, recipient: String): TransactionResult {
        // Delegate to escrow transaction service
        val genericResult = escrowTransactionService.resolveDispute(contractAddress, recipient)
        // Convert to legacy TransactionResult format
        return TransactionResult(
            success = genericResult.success,
            transactionHash = genericResult.transactionHash,
            error = genericResult.error
        )
    }

    suspend fun waitForTransactionReceipt(transactionHash: String): TransactionReceipt? {
        // Delegate to generic blockchain relay service
        return blockchainRelayService.waitForTransactionReceipt(transactionHash)
    }

    suspend fun raiseDispute(contractAddress: String): TransactionResult {
        // This method is deprecated - users should use raiseDisputeWithGasTransfer
        logger.warn("Deprecated method raiseDispute called - use raiseDisputeWithGasTransfer instead")
        return TransactionResult(
            success = false,
            transactionHash = null,
            error = "This method is deprecated - use raiseDisputeWithGasTransfer instead"
        )
    }

    suspend fun claimFunds(contractAddress: String): TransactionResult {
        // This method is deprecated - users should use claimFundsWithGasTransfer
        logger.warn("Deprecated method claimFunds called - use claimFundsWithGasTransfer instead")
        return TransactionResult(
            success = false,
            transactionHash = null,
            error = "This method is deprecated - use claimFundsWithGasTransfer instead"
        )
    }

    suspend fun depositFunds(contractAddress: String): TransactionResult {
        // This method is deprecated - users should use depositFundsWithGasTransfer
        logger.warn("Deprecated method depositFunds called - use depositFundsWithGasTransfer instead")
        return TransactionResult(
            success = false,
            transactionHash = null,
            error = "This method is deprecated - use depositFundsWithGasTransfer instead"
        )
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

        // Delegate to generic blockchain relay service and convert to legacy format
        val genericCosts = blockchainRelayService.getOperationGasCosts(operations)
        return genericCosts.map { genericCost ->
            OperationGasCost(
                operation = genericCost.operation,
                gasLimit = genericCost.gasLimit,
                gasPriceWei = genericCost.gasPriceWei,
                totalCostWei = genericCost.totalCostWei,
                totalCostAvax = genericCost.totalCostAvax
            )
        }
    }

    // Gas transfer methods - delegate to escrow transaction service
    suspend fun depositFundsWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        val genericResult = escrowTransactionService.depositFundsWithGasTransfer(userWalletAddress, signedTransactionHex)
        return TransactionResult(
            success = genericResult.success,
            transactionHash = genericResult.transactionHash,
            error = genericResult.error
        )
    }

    suspend fun approveUSDCWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        val genericResult = escrowTransactionService.approveUSDCWithGasTransfer(userWalletAddress, signedTransactionHex)
        return TransactionResult(
            success = genericResult.success,
            transactionHash = genericResult.transactionHash,
            error = genericResult.error
        )
    }

    suspend fun claimFundsWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        val genericResult = escrowTransactionService.claimFundsWithGasTransfer(userWalletAddress, signedTransactionHex)
        return TransactionResult(
            success = genericResult.success,
            transactionHash = genericResult.transactionHash,
            error = genericResult.error
        )
    }

    suspend fun raiseDisputeWithGasTransfer(userWalletAddress: String, signedTransactionHex: String): TransactionResult {
        val genericResult = escrowTransactionService.raiseDisputeWithGasTransfer(userWalletAddress, signedTransactionHex)
        return TransactionResult(
            success = genericResult.success,
            transactionHash = genericResult.transactionHash,
            error = genericResult.error
        )
    }
}