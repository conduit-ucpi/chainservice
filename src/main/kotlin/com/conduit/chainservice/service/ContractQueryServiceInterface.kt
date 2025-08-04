package com.conduit.chainservice.service

import com.conduit.chainservice.escrow.models.ContractInfo
import com.conduit.chainservice.escrow.models.ContractInfoResult

/**
 * Interface for contract query services to enable proper dependency injection
 * and caching layer integration.
 */
interface ContractQueryServiceInterface {
    
    /**
     * Get all contracts associated with a wallet address (as buyer or seller)
     */
    suspend fun getContractsForWallet(walletAddress: String, userType: String?): List<ContractInfo>
    
    /**
     * Get detailed information for a specific contract
     */
    suspend fun getContractInfo(contractAddress: String): ContractInfo?
    
    /**
     * Batch query for multiple contract information with optimized RPC calls
     */
    suspend fun getBatchContractInfo(contractAddresses: List<String>): Map<String, ContractInfoResult>
}