package com.conduit.chainservice.service

import com.conduit.chainservice.config.StateAwareCacheConfig
import com.conduit.chainservice.config.StateAwareCacheConfig.Companion.CONTRACT_INFO_IMMUTABLE_CACHE
import com.conduit.chainservice.config.StateAwareCacheConfig.Companion.CONTRACT_INFO_MUTABLE_CACHE
import com.conduit.chainservice.config.StateAwareCacheConfig.Companion.CONTRACT_STATE_IMMUTABLE_CACHE
import com.conduit.chainservice.config.StateAwareCacheConfig.Companion.CONTRACT_STATE_MUTABLE_CACHE
import com.conduit.chainservice.config.StateAwareCacheConfig.Companion.TRANSACTION_DATA_CACHE
import com.conduit.chainservice.escrow.models.ContractInfo
import com.conduit.chainservice.escrow.models.ContractStatus
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service

/**
 * STATE-AWARE cache invalidation service that handles immutable vs mutable contract caching.
 * 
 * INTELLIGENT INVALIDATION STRATEGY:
 * - IMMUTABLE contracts (CLAIMED, RESOLVED, EXPIRED): Never invalidate - they can't change!
 * - MUTABLE contracts (CREATED, ACTIVE, DISPUTED): Selective invalidation on state changes
 * - STATE TRANSITIONS: Move contracts between caches when they transition to final states
 * - TRANSACTION DATA: Never invalidate - blockchain events are permanent
 * 
 * PERFORMANCE BENEFITS:
 * - Prevents unnecessary invalidation of immutable data
 * - Automatically promotes contracts to long-term cache when they reach final states
 * - Massive reduction in cache misses for historical data
 */
@Service
class StateAwareCacheInvalidationService(
    private val cacheManager: CacheManager,
    private val stateAwareCacheConfig: StateAwareCacheConfig
) {

    private val logger = LoggerFactory.getLogger(StateAwareCacheInvalidationService::class.java)

    /**
     * INTELLIGENT CONTRACT CACHE INVALIDATION
     * 
     * Handles invalidation with state awareness:
     * 1. Checks if contract is in a final immutable state - if so, DON'T invalidate!
     * 2. For mutable contracts, invalidates and promotes to immutable cache if state changed to final
     * 3. Never invalidates transaction data (events are immutable)
     * 
     * @param contractAddress The contract address to potentially invalidate
     * @param operationType The operation that triggered this (for logging)
     * @param newStatus The new status after the operation (if known)
     * @param transactionHash Transaction hash for audit trail
     */
    fun invalidateContractCacheIntelligently(
        contractAddress: String, 
        operationType: String,
        newStatus: ContractStatus? = null,
        transactionHash: String? = null
    ) {
        try {
            logger.info("INTELLIGENT INVALIDATION: Analyzing contract $contractAddress, operation: $operationType, newStatus: $newStatus, tx: $transactionHash")
            
            // Get current cached contract to understand current state
            val currentContract = getCurrentCachedContract(contractAddress)
            val currentStatus = currentContract?.status
            
            // DECISION TREE: Should we invalidate?
            when {
                // Case 1: Contract is already in final immutable state - NEVER invalidate
                currentStatus != null && stateAwareCacheConfig.isImmutableState(currentStatus) -> {
                    logger.info("SKIP INVALIDATION: Contract $contractAddress is in immutable state $currentStatus - cannot change!")
                    return
                }
                
                // Case 2: New state is final - invalidate mutable and promote to immutable
                newStatus != null && stateAwareCacheConfig.isImmutableState(newStatus) -> {
                    logger.info("STATE TRANSITION: Contract $contractAddress transitioning to final state $newStatus - promoting to immutable cache")
                    promoteToImmutableCache(contractAddress, operationType, currentStatus, newStatus)
                }
                
                // Case 3: Contract is still mutable - normal selective invalidation
                else -> {
                    logger.info("SELECTIVE INVALIDATION: Contract $contractAddress is mutable - invalidating mutable caches only")
                    invalidateMutableCaches(contractAddress, operationType, transactionHash)
                }
            }
            
        } catch (e: Exception) {
            logger.error("INTELLIGENT INVALIDATION FAILED for contract: $contractAddress, operation: $operationType", e)
        }
    }

    /**
     * Promotes a contract from mutable to immutable cache when it reaches a final state
     */
    private fun promoteToImmutableCache(
        contractAddress: String,
        operationType: String,
        oldStatus: ContractStatus?,
        newStatus: ContractStatus
    ) {
        try {
            var promotionCount = 0
            val promotedCaches = mutableListOf<String>()
            
            // Step 1: Get contract data from mutable cache before removing it
            val mutableInfoCache = cacheManager.getCache(CONTRACT_INFO_MUTABLE_CACHE)
            val contractInfo = mutableInfoCache?.get(contractAddress, ContractInfo::class.java)
            
            val mutableStateCache = cacheManager.getCache(CONTRACT_STATE_MUTABLE_CACHE)
            @Suppress("UNCHECKED_CAST")
            val contractState = mutableStateCache?.get(contractAddress, Map::class.java) as? Map<String, Any>
            
            // Step 2: Remove from mutable caches
            if (contractInfo != null) {
                mutableInfoCache?.evict(contractAddress)
                logger.debug("Removed contract info from mutable cache: $contractAddress")
            }
            
            if (contractState != null) {
                mutableStateCache?.evict(contractAddress)
                logger.debug("Removed contract state from mutable cache: $contractAddress")
            }
            
            // Step 3: Add to immutable caches with updated status
            if (contractInfo != null) {
                val updatedContractInfo = contractInfo.copy(status = newStatus)
                val immutableInfoCache = cacheManager.getCache(CONTRACT_INFO_IMMUTABLE_CACHE)
                immutableInfoCache?.put(contractAddress, updatedContractInfo)
                promotionCount++
                promotedCaches.add("CONTRACT_INFO_IMMUTABLE")
                logger.debug("Promoted contract info to immutable cache: $contractAddress")
            }
            
            if (contractState != null) {
                // Update state data with new status flags
                val updatedStateData = contractState.toMutableMap().apply {
                    this["disputed"] = (newStatus == ContractStatus.DISPUTED)
                    this["resolved"] = (newStatus == ContractStatus.RESOLVED)
                    this["claimed"] = (newStatus == ContractStatus.CLAIMED)
                }
                
                val immutableStateCache = cacheManager.getCache(CONTRACT_STATE_IMMUTABLE_CACHE)
                immutableStateCache?.put(contractAddress, updatedStateData)
                promotionCount++
                promotedCaches.add("CONTRACT_STATE_IMMUTABLE")
                logger.debug("Promoted contract state to immutable cache: $contractAddress")
            }
            
            logger.info("PROMOTION SUCCESS: Contract $contractAddress promoted from $oldStatus to $newStatus - $promotionCount caches promoted: ${promotedCaches.joinToString()}")
            
        } catch (e: Exception) {
            logger.error("PROMOTION FAILED for contract: $contractAddress from $oldStatus to $newStatus", e)
            // Fallback: just invalidate mutable caches
            invalidateMutableCaches(contractAddress, operationType, null)
        }
    }

    /**
     * Invalidates only mutable caches for contracts that are still active
     */
    private fun invalidateMutableCaches(contractAddress: String, operationType: String, transactionHash: String?) {
        try {
            var evictionCount = 0
            val evictedCaches = mutableListOf<String>()
            
            // Invalidate mutable contract info cache
            val mutableInfoCache = cacheManager.getCache(CONTRACT_INFO_MUTABLE_CACHE)
            if (mutableInfoCache?.get(contractAddress) != null) {
                mutableInfoCache.evict(contractAddress)
                evictionCount++
                evictedCaches.add("CONTRACT_INFO_MUTABLE")
                logger.debug("Evicted from mutable contract info cache: $contractAddress")
            }
            
            // Invalidate mutable contract state cache
            val mutableStateCache = cacheManager.getCache(CONTRACT_STATE_MUTABLE_CACHE)
            if (mutableStateCache?.get(contractAddress) != null) {
                mutableStateCache.evict(contractAddress)
                evictionCount++
                evictedCaches.add("CONTRACT_STATE_MUTABLE")
                logger.debug("Evicted from mutable contract state cache: $contractAddress")
            }
            
            // NOTE: Never invalidate immutable caches or transaction data cache
            logger.info("MUTABLE INVALIDATION: Contract $contractAddress - evicted $evictionCount caches: ${evictedCaches.joinToString()}")
            
        } catch (e: Exception) {
            logger.error("MUTABLE INVALIDATION FAILED for contract: $contractAddress", e)
        }
    }

    /**
     * Gets the currently cached contract from either cache to understand current state
     */
    private fun getCurrentCachedContract(contractAddress: String): ContractInfo? {
        // Check immutable cache first (higher priority)
        val immutableCache = cacheManager.getCache(CONTRACT_INFO_IMMUTABLE_CACHE)
        val immutableResult = immutableCache?.get(contractAddress, ContractInfo::class.java)
        if (immutableResult != null) {
            return immutableResult
        }
        
        // Then check mutable cache
        val mutableCache = cacheManager.getCache(CONTRACT_INFO_MUTABLE_CACHE)
        return mutableCache?.get(contractAddress, ContractInfo::class.java)
    }

    /**
     * Batch intelligent invalidation for multiple contracts
     */
    fun invalidateMultipleContractCacheIntelligently(
        contractUpdates: List<ContractUpdate>,
        operationType: String,
        transactionHash: String? = null
    ) {
        if (contractUpdates.isEmpty()) {
            logger.debug("No contracts provided for batch intelligent invalidation")
            return
        }

        logger.info("Starting INTELLIGENT BATCH invalidation for ${contractUpdates.size} contracts, operation: $operationType")
        
        var immutableSkipped = 0
        var promoted = 0
        var invalidated = 0
        var failed = 0
        
        contractUpdates.forEach { update ->
            try {
                val currentContract = getCurrentCachedContract(update.contractAddress)
                val currentStatus = currentContract?.status
                
                when {
                    // Skip immutable contracts
                    currentStatus != null && stateAwareCacheConfig.isImmutableState(currentStatus) -> {
                        immutableSkipped++
                        logger.debug("Skipped immutable contract: ${update.contractAddress}")
                    }
                    
                    // Promote to immutable if new state is final
                    update.newStatus != null && stateAwareCacheConfig.isImmutableState(update.newStatus) -> {
                        promoteToImmutableCache(update.contractAddress, operationType, currentStatus, update.newStatus)
                        promoted++
                    }
                    
                    // Normal invalidation for mutable contracts
                    else -> {
                        invalidateMutableCaches(update.contractAddress, operationType, transactionHash)
                        invalidated++
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed intelligent invalidation for contract: ${update.contractAddress}", e)
                failed++
            }
        }
        
        logger.info("INTELLIGENT BATCH invalidation completed: $immutableSkipped immutable skipped, $promoted promoted, $invalidated invalidated, $failed failed")
    }

    /**
     * Emergency nuclear option - clears all caches (use sparingly!)
     */
    fun clearAllCaches(reason: String) {
        try {
            logger.warn("NUCLEAR OPTION: Clearing ALL state-aware caches. Reason: $reason")
            
            val cacheNames = listOf(
                CONTRACT_INFO_IMMUTABLE_CACHE,
                CONTRACT_INFO_MUTABLE_CACHE,
                CONTRACT_STATE_IMMUTABLE_CACHE,
                CONTRACT_STATE_MUTABLE_CACHE,
                TRANSACTION_DATA_CACHE
            )
            
            var clearedCount = 0
            cacheNames.forEach { cacheName ->
                val cache = cacheManager.getCache(cacheName)
                if (cache != null) {
                    cache.clear()
                    clearedCount++
                    logger.warn("Cleared cache: $cacheName")
                }
            }
            
            logger.warn("NUCLEAR CLEAR completed: $clearedCount caches cleared. Reason: $reason")
            
        } catch (e: Exception) {
            logger.error("NUCLEAR CLEAR FAILED. Reason: $reason", e)
        }
    }

    /**
     * Gets comprehensive state-aware cache statistics
     */
    fun getStateAwareCacheStats(): Map<String, Any> {
        return try {
            val allStats = mutableMapOf<String, Any>()
            
            val cacheNames = mapOf(
                "immutable_contractInfo" to CONTRACT_INFO_IMMUTABLE_CACHE,
                "mutable_contractInfo" to CONTRACT_INFO_MUTABLE_CACHE,
                "immutable_contractState" to CONTRACT_STATE_IMMUTABLE_CACHE,
                "mutable_contractState" to CONTRACT_STATE_MUTABLE_CACHE,
                "transactionData" to TRANSACTION_DATA_CACHE
            )
            
            cacheNames.forEach { (key, cacheName) ->
                val cache = cacheManager.getCache(cacheName)
                if (cache?.nativeCache is com.github.benmanes.caffeine.cache.Cache<*, *>) {
                    val stats = (cache.nativeCache as com.github.benmanes.caffeine.cache.Cache<*, *>).stats()
                    allStats[key] = mapOf(
                        "requestCount" to stats.requestCount(),
                        "hitCount" to stats.hitCount(),
                        "hitRate" to String.format("%.1f%%", stats.hitRate() * 100),
                        "missCount" to stats.missCount(),
                        "evictionCount" to stats.evictionCount(),
                        "estimatedSize" to (cache.nativeCache as com.github.benmanes.caffeine.cache.Cache<*, *>).estimatedSize()
                    )
                }
            }
            
            // Calculate combined immutable vs mutable statistics
            val immutableStats = combineStats(allStats, listOf("immutable_contractInfo", "immutable_contractState"))
            val mutableStats = combineStats(allStats, listOf("mutable_contractInfo", "mutable_contractState"))
            
            allStats["summary"] = mapOf(
                "immutable_combined" to immutableStats,
                "mutable_combined" to mutableStats,
                "transaction_immutable" to (allStats["transactionData"] ?: "No data")
            )
            
            return allStats
            
        } catch (e: Exception) {
            logger.error("Error getting state-aware cache statistics", e)
            mapOf<String, Any>("error" to (e.message ?: "Unknown error"))
        }
    }

    private fun combineStats(allStats: Map<String, Any>, keys: List<String>): Map<String, Any> {
        var totalRequests = 0L
        var totalHits = 0L
        var totalMisses = 0L
        var totalEvictions = 0L
        var totalSize = 0L
        var validKeys = 0
        
        keys.forEach { key ->
            @Suppress("UNCHECKED_CAST")
            val stats = allStats[key] as? Map<String, Any>
            if (stats != null) {
                totalRequests += (stats["requestCount"] as? Long) ?: 0L
                totalHits += (stats["hitCount"] as? Long) ?: 0L
                totalMisses += (stats["missCount"] as? Long) ?: 0L
                totalEvictions += (stats["evictionCount"] as? Long) ?: 0L
                totalSize += (stats["estimatedSize"] as? Long) ?: 0L
                validKeys++
            }
        }
        
        val hitRate = if (totalRequests > 0) (totalHits.toDouble() / totalRequests * 100) else 0.0
        
        return mapOf(
            "requestCount" to totalRequests,
            "hitCount" to totalHits,
            "hitRate" to String.format("%.1f%%", hitRate),
            "missCount" to totalMisses,
            "evictionCount" to totalEvictions,
            "estimatedSize" to totalSize,
            "cacheCount" to validKeys
        )
    }

    /**
     * Data class for batch contract updates
     */
    data class ContractUpdate(
        val contractAddress: String,
        val newStatus: ContractStatus? = null
    )
}