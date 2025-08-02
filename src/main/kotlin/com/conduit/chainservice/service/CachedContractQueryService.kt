package com.conduit.chainservice.service

import com.conduit.chainservice.config.CacheConfig.Companion.CONTRACT_INFO_CACHE
import com.conduit.chainservice.escrow.models.ContractInfo
import com.conduit.chainservice.escrow.models.ContractInfoResult
import com.conduit.chainservice.escrow.models.ContractStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

/**
 * Cached wrapper for ContractQueryService that dramatically reduces RPC calls.
 * 
 * Caching Strategy:
 * - Individual contract info is cached for 2 minutes
 * - Batch operations check cache first, only query uncached contracts from RPC
 * - Cache individual contracts even when fetched in batches to maximize hit rates
 * - Transparent to existing code (same interface)
 */
@Service
@Primary
class CachedContractQueryService(
    @Qualifier("originalContractQueryService") private val originalService: ContractQueryService,
    private val cacheMetricsService: CacheMetricsService,
    private val cacheManager: CacheManager
) {

    private val logger = LoggerFactory.getLogger(CachedContractQueryService::class.java)

    suspend fun getContractsForWallet(walletAddress: String, userType: String? = null): List<ContractInfo> {
        logger.debug("Getting contracts for wallet: $walletAddress, userType: $userType")
        cacheMetricsService.recordCacheRequest("getContractsForWallet")
        
        return originalService.getContractsForWallet(walletAddress, userType)
    }

    @Cacheable(value = [CONTRACT_INFO_CACHE], key = "#contractAddress")
    suspend fun getContractInfo(contractAddress: String): ContractInfo? {
        logger.debug("Cache miss for contract info: $contractAddress")
        cacheMetricsService.recordCacheMiss("contractInfo")
        
        val result = originalService.getContractInfo(contractAddress)
        if (result != null) {
            logger.debug("Cached contract info for: $contractAddress")
        }
        return result
    }

    @Cacheable(value = [CONTRACT_INFO_CACHE], key = "'status:' + #contractAddress")
    suspend fun getContractStatus(contractAddress: String): ContractStatus {
        logger.debug("Cache miss for contract status: $contractAddress")
        cacheMetricsService.recordCacheMiss("contractStatus")
        
        return originalService.getContractStatus(contractAddress)
    }

    /**
     * Smart batch query that maximizes cache hit rates.
     * 
     * Strategy:
     * 1. Check cache for each contract individually
     * 2. Only query uncached contracts from RPC
     * 3. Cache individual results from RPC calls
     * 4. Combine cached and fresh results
     */
    suspend fun getBatchContractInfo(contractAddresses: List<String>): Map<String, ContractInfoResult> = coroutineScope {
        logger.info("Starting smart batch query for ${contractAddresses.size} contracts")
        cacheMetricsService.recordCacheRequest("getBatchContractInfo", contractAddresses.size)
        
        if (contractAddresses.isEmpty()) {
            return@coroutineScope emptyMap<String, ContractInfoResult>()
        }
        
        val results = mutableMapOf<String, ContractInfoResult>()
        val uncachedAddresses = mutableListOf<String>()
        
        // Step 1: Check cache for each contract
        val cacheCheckResults = contractAddresses.map { contractAddress ->
            async {
                try {
                    val cachedInfo = getCachedContractInfo(contractAddress)
                    if (cachedInfo != null) {
                        logger.debug("Cache hit for contract: $contractAddress")
                        cacheMetricsService.recordCacheHit("contractInfo")
                        contractAddress to ContractInfoResult(
                            success = true,
                            contractInfo = cachedInfo,
                            error = null
                        )
                    } else {
                        logger.debug("Cache miss for contract: $contractAddress")
                        uncachedAddresses.add(contractAddress)
                        null
                    }
                } catch (e: Exception) {
                    logger.warn("Error checking cache for contract: $contractAddress", e)
                    uncachedAddresses.add(contractAddress)
                    null
                }
            }
        }
        
        // Collect cached results
        cacheCheckResults.awaitAll().filterNotNull().forEach { (address, result) ->
            results[address] = result
        }
        
        val cacheHits = results.size
        val cacheMisses = uncachedAddresses.size
        
        logger.info("Batch cache check completed: $cacheHits hits, $cacheMisses misses (${if (contractAddresses.isNotEmpty()) (cacheHits * 100 / contractAddresses.size) else 0}% hit rate)")
        
        // Step 2: Query uncached contracts from RPC
        if (uncachedAddresses.isNotEmpty()) {
            logger.info("Querying ${uncachedAddresses.size} uncached contracts from RPC")
            
            val rpcResults = originalService.getBatchContractInfo(uncachedAddresses)
            
            // Step 3: Cache individual results and add to final results
            rpcResults.forEach { (address, result) ->
                results[address] = result
                
                // Cache successful results individually for future hits
                if (result.success && result.contractInfo != null) {
                    try {
                        cacheContractInfo(address, result.contractInfo)
                        logger.debug("Cached fresh contract info for: $address")
                    } catch (e: Exception) {
                        logger.warn("Failed to cache contract info for: $address", e)
                    }
                }
            }
        }
        
        val rpcCalls = uncachedAddresses.size
        val totalContracts = contractAddresses.size
        val rpcReduction = if (totalContracts > 0) ((totalContracts - rpcCalls) * 100 / totalContracts) else 0
        
        logger.info("Batch query completed: $totalContracts requested, $rpcCalls RPC calls, ${rpcReduction}% RPC reduction")
        cacheMetricsService.recordBatchQueryStats(totalContracts, cacheHits, rpcCalls)
        
        return@coroutineScope results
    }

    /**
     * Check cache without triggering the @Cacheable method to avoid cache misses
     */
    private suspend fun getCachedContractInfo(contractAddress: String): ContractInfo? {
        return try {
            val cache = cacheManager.getCache(CONTRACT_INFO_CACHE)
            cache?.get(contractAddress, ContractInfo::class.java)
        } catch (e: Exception) {
            logger.debug("Cache lookup failed for: $contractAddress", e)
            null
        }
    }

    /**
     * Cache contract info individually (called after RPC fetch)
     */
    private suspend fun cacheContractInfo(contractAddress: String, contractInfo: ContractInfo) {
        try {
            val cache = cacheManager.getCache(CONTRACT_INFO_CACHE)
            cache?.put(contractAddress, contractInfo)
            logger.trace("Cached contract info for: $contractAddress")
        } catch (e: Exception) {
            logger.warn("Failed to cache contract info for: $contractAddress", e)
        }
    }
}