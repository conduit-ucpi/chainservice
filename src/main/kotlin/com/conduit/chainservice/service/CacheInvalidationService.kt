package com.conduit.chainservice.service

import com.conduit.chainservice.config.CacheConfig.Companion.CONTRACT_INFO_CACHE
import com.conduit.chainservice.config.CacheConfig.Companion.CONTRACT_STATE_CACHE
import com.conduit.chainservice.config.CacheConfig.Companion.TRANSACTION_DATA_CACHE
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service

/**
 * Service responsible for selective cache invalidation when contract state changes.
 * 
 * CRITICAL: This service implements SELECTIVE INVALIDATION to prevent the cache invalidation
 * bug where changing 1 contract invalidates ALL 31 contracts in batch operations.
 * 
 * Multi-Level Cache Invalidation Strategy:
 * - Level 1 (CONTRACT_STATE_CACHE): Basic contract state data from blockchain calls
 * - Level 2 (TRANSACTION_DATA_CACHE): Event/transaction historical data  
 * - Level 3 (CONTRACT_INFO_CACHE): Final assembled ContractInfo objects
 * - Only invalidate specific contract addresses, never clear entire cache
 * - Support for status-prefixed keys and direct address keys
 * - Thread-safe operations with detailed logging for debugging
 */
@Service
class CacheInvalidationService(
    private val cacheManager: CacheManager
) {

    private val logger = LoggerFactory.getLogger(CacheInvalidationService::class.java)

    /**
     * SELECTIVE CACHE INVALIDATION: Invalidates cache entries for ONE specific contract 
     * across all three cache levels after a successful transaction.
     * 
     * CRITICAL: This method implements selective invalidation to solve the cache invalidation
     * bug where 1 contract change was invalidating ALL 31 contracts.
     * 
     * @param contractAddress The specific contract address whose cache should be invalidated
     * @param operationType The type of operation that triggered the invalidation (for logging)
     * @param transactionHash The transaction hash for audit logging
     */
    fun invalidateContractCache(contractAddress: String, operationType: String, transactionHash: String? = null) {
        try {
            logger.info("SELECTIVE INVALIDATION: Invalidating multi-level cache for contract: $contractAddress, operation: $operationType, tx: $transactionHash")
            
            val invalidatedKeys = mutableListOf<String>()
            var totalEvictions = 0

            // LEVEL 3: Contract Info Cache (assembled objects)
            val level3Cache = cacheManager.getCache(CONTRACT_INFO_CACHE)
            if (level3Cache != null) {
                val level3Keys = listOf(contractAddress, "status:$contractAddress")
                level3Keys.forEach { key ->
                    if (level3Cache.get(key) != null) {
                        level3Cache.evict(key)
                        invalidatedKeys.add("L3:$key")
                        totalEvictions++
                        logger.debug("LEVEL 3: Evicted cache entry for key: $key")
                    }
                }
            } else {
                logger.warn("CONTRACT_INFO_CACHE not found, skipping Level 3 invalidation")
            }

            // LEVEL 1: Contract State Cache (blockchain state data)
            val level1Cache = cacheManager.getCache(CONTRACT_STATE_CACHE)
            if (level1Cache != null) {
                if (level1Cache.get(contractAddress) != null) {
                    level1Cache.evict(contractAddress)
                    invalidatedKeys.add("L1:$contractAddress")
                    totalEvictions++
                    logger.debug("LEVEL 1: Evicted state cache entry for: $contractAddress")
                }
            } else {
                logger.debug("CONTRACT_STATE_CACHE not found, skipping Level 1 invalidation")
            }

            // LEVEL 2: Transaction Data Cache (event history)
            val level2Cache = cacheManager.getCache(TRANSACTION_DATA_CACHE)
            if (level2Cache != null) {
                if (level2Cache.get(contractAddress) != null) {
                    level2Cache.evict(contractAddress)
                    invalidatedKeys.add("L2:$contractAddress")
                    totalEvictions++
                    logger.debug("LEVEL 2: Evicted transaction data cache entry for: $contractAddress")
                }
            } else {
                logger.debug("TRANSACTION_DATA_CACHE not found, skipping Level 2 invalidation")
            }

            if (totalEvictions > 0) {
                logger.info("SELECTIVE INVALIDATION SUCCESS: Evicted $totalEvictions cache entries for contract $contractAddress: ${invalidatedKeys.joinToString(", ")}")
            } else {
                logger.debug("SELECTIVE INVALIDATION: No cache entries found to invalidate for contract: $contractAddress")
            }

        } catch (e: Exception) {
            logger.error("SELECTIVE INVALIDATION FAILED for contract: $contractAddress, operation: $operationType", e)
            // Don't rethrow - cache invalidation failure shouldn't break the transaction flow
        }
    }

    /**
     * Invalidates cache entries for multiple contracts in a batch operation.
     * 
     * @param contractAddresses List of contract addresses to invalidate
     * @param operationType The type of operation that triggered the invalidation
     * @param transactionHash The transaction hash for audit logging
     */
    fun invalidateMultipleContractCache(contractAddresses: List<String>, operationType: String, transactionHash: String? = null) {
        if (contractAddresses.isEmpty()) {
            logger.debug("No contracts provided for batch cache invalidation")
            return
        }

        logger.info("Starting batch cache invalidation for ${contractAddresses.size} contracts, operation: $operationType, tx: $transactionHash")
        
        var successCount = 0
        var failureCount = 0
        
        contractAddresses.forEach { contractAddress ->
            try {
                invalidateContractCache(contractAddress, operationType, transactionHash)
                successCount++
            } catch (e: Exception) {
                logger.error("Failed to invalidate cache for contract: $contractAddress in batch operation", e)
                failureCount++
            }
        }
        
        logger.info("Batch cache invalidation completed: $successCount successful, $failureCount failed")
    }

    /**
     * Invalidates all contract info cache entries.
     * This is a nuclear option for cache maintenance or emergency scenarios.
     * 
     * @param reason The reason for clearing all cache entries
     */
    fun invalidateAllContractCache(reason: String) {
        try {
            logger.warn("Invalidating ALL contract cache entries. Reason: $reason")
            
            val cache = cacheManager.getCache(CONTRACT_INFO_CACHE)
            if (cache == null) {
                logger.warn("Contract info cache not found, cannot clear all entries")
                return
            }

            cache.clear()
            logger.warn("Successfully cleared all contract info cache entries. Reason: $reason")
            
        } catch (e: Exception) {
            logger.error("Failed to clear all contract cache entries. Reason: $reason", e)
        }
    }

    /**
     * Checks if the cache manager and contract info cache are available.
     * Useful for health checks and diagnostics.
     * 
     * @return true if cache is available and functional
     */
    fun isCacheAvailable(): Boolean {
        return try {
            val cache = cacheManager.getCache(CONTRACT_INFO_CACHE)
            cache != null
        } catch (e: Exception) {
            logger.error("Error checking cache availability", e)
            false
        }
    }

    /**
     * Gets comprehensive multi-level cache statistics for monitoring selective invalidation effectiveness.
     * 
     * @return Map of multi-level cache statistics or empty map if not available
     */
    fun getCacheStats(): Map<String, Any> {
        return try {
            val allStats = mutableMapOf<String, Any>()
            
            // Level 3: Contract Info Cache stats
            val level3Cache = cacheManager.getCache(CONTRACT_INFO_CACHE)
            if (level3Cache?.nativeCache is com.github.benmanes.caffeine.cache.Cache<*, *>) {
                val stats = (level3Cache.nativeCache as com.github.benmanes.caffeine.cache.Cache<*, *>).stats()
                allStats["level3_contractInfo"] = mapOf(
                    "requestCount" to stats.requestCount(),
                    "hitCount" to stats.hitCount(),
                    "hitRate" to String.format("%.1f%%", stats.hitRate() * 100),
                    "missCount" to stats.missCount(),
                    "evictionCount" to stats.evictionCount(),
                    "estimatedSize" to (level3Cache.nativeCache as com.github.benmanes.caffeine.cache.Cache<*, *>).estimatedSize()
                )
            }
            
            // Level 1: Contract State Cache stats  
            val level1Cache = cacheManager.getCache(CONTRACT_STATE_CACHE)
            if (level1Cache?.nativeCache is com.github.benmanes.caffeine.cache.Cache<*, *>) {
                val stats = (level1Cache.nativeCache as com.github.benmanes.caffeine.cache.Cache<*, *>).stats()
                allStats["level1_contractState"] = mapOf(
                    "requestCount" to stats.requestCount(),
                    "hitCount" to stats.hitCount(),
                    "hitRate" to String.format("%.1f%%", stats.hitRate() * 100),
                    "missCount" to stats.missCount(),
                    "evictionCount" to stats.evictionCount(),
                    "estimatedSize" to (level1Cache.nativeCache as com.github.benmanes.caffeine.cache.Cache<*, *>).estimatedSize()
                )
            }
            
            // Level 2: Transaction Data Cache stats
            val level2Cache = cacheManager.getCache(TRANSACTION_DATA_CACHE)
            if (level2Cache?.nativeCache is com.github.benmanes.caffeine.cache.Cache<*, *>) {
                val stats = (level2Cache.nativeCache as com.github.benmanes.caffeine.cache.Cache<*, *>).stats()
                allStats["level2_transactionData"] = mapOf(
                    "requestCount" to stats.requestCount(),
                    "hitCount" to stats.hitCount(),
                    "hitRate" to String.format("%.1f%%", stats.hitRate() * 100),
                    "missCount" to stats.missCount(),
                    "evictionCount" to stats.evictionCount(),
                    "estimatedSize" to (level2Cache.nativeCache as com.github.benmanes.caffeine.cache.Cache<*, *>).estimatedSize()
                )
            }
            
            if (allStats.isEmpty()) {
                allStats["status"] = "No cache statistics available"
            }
            
            return allStats
            
        } catch (e: Exception) {
            logger.error("Error getting multi-level cache statistics", e)
            mapOf<String, Any>("error" to (e.message ?: "Unknown error"))
        }
    }

    /**
     * VERIFICATION METHOD: Analyze cache effectiveness for selective invalidation verification.
     * This method helps verify that selective invalidation is working correctly by providing
     * detailed analysis of cache hit rates and eviction patterns.
     * 
     * @return Detailed cache effectiveness report
     */
    fun analyzeCacheEffectiveness(): Map<String, Any> {
        return try {
            val stats = getCacheStats()
            val analysis = mutableMapOf<String, Any>()
            
            if (stats.containsKey("level3_contractInfo")) {
                @Suppress("UNCHECKED_CAST")
                val level3Stats = stats["level3_contractInfo"] as Map<String, Any>
                val hitRate = level3Stats["hitRate"] as String
                val evictionCount = level3Stats["evictionCount"] as Long
                val estimatedSize = level3Stats["estimatedSize"] as Long
                
                analysis["level3_analysis"] = mapOf(
                    "hitRate" to hitRate,
                    "selectiveInvalidationWorking" to (evictionCount > 0 && estimatedSize > 0),
                    "cacheUtilization" to "Size: $estimatedSize, Evictions: $evictionCount"
                )
            }
            
            // Calculate overall cache effectiveness score
            val overallScore = calculateCacheEffectivenessScore(stats)
            analysis["overallEffectivenessScore"] = overallScore
            analysis["selectiveInvalidationStatus"] = if (overallScore > 70) "WORKING" else "NEEDS_INVESTIGATION"
            
            return analysis
            
        } catch (e: Exception) {
            logger.error("Error analyzing cache effectiveness", e)
            mapOf<String, Any>("error" to (e.message ?: "Analysis failed"))
        }
    }
    
    private fun calculateCacheEffectivenessScore(stats: Map<String, Any>): Int {
        return try {
            var totalHitRate = 0.0
            var levels = 0
            
            listOf("level3_contractInfo", "level1_contractState", "level2_transactionData").forEach { levelKey ->
                if (stats.containsKey(levelKey)) {
                    @Suppress("UNCHECKED_CAST")
                    val levelStats = stats[levelKey] as Map<String, Any>
                    val hitRateStr = levelStats["hitRate"] as String
                    val hitRate = hitRateStr.removeSuffix("%").toDoubleOrNull() ?: 0.0
                    totalHitRate += hitRate
                    levels++
                }
            }
            
            if (levels > 0) (totalHitRate / levels).toInt() else 0
            
        } catch (e: Exception) {
            0
        }
    }
}