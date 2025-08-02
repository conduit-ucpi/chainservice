package com.conduit.chainservice.service

import com.conduit.chainservice.config.CacheConfig.Companion.CONTRACT_INFO_CACHE
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service

/**
 * Service responsible for invalidating cached contract data when contract state changes.
 * 
 * This service ensures that cached contract information is immediately invalidated
 * when blockchain transactions successfully change contract state, preventing stale
 * data from being served to clients.
 * 
 * Cache Invalidation Strategy:
 * - Invalidate contract info cache entries immediately after successful transactions
 * - Invalidate both direct contract address keys and status-prefixed keys
 * - Thread-safe operations to handle concurrent access
 * - Detailed logging for cache operations
 */
@Service
class CacheInvalidationService(
    private val cacheManager: CacheManager
) {

    private val logger = LoggerFactory.getLogger(CacheInvalidationService::class.java)

    /**
     * Invalidates cache entries for a specific contract after a successful transaction.
     * 
     * @param contractAddress The contract address whose cache should be invalidated
     * @param operationType The type of operation that triggered the invalidation (for logging)
     * @param transactionHash The transaction hash for audit logging
     */
    fun invalidateContractCache(contractAddress: String, operationType: String, transactionHash: String? = null) {
        try {
            logger.info("Invalidating cache for contract: $contractAddress, operation: $operationType, tx: $transactionHash")
            
            val cache = cacheManager.getCache(CONTRACT_INFO_CACHE)
            if (cache == null) {
                logger.warn("Contract info cache not found, skipping invalidation for contract: $contractAddress")
                return
            }

            // Track what we're invalidating for logging
            val invalidatedKeys = mutableListOf<String>()

            // Invalidate direct contract info cache entry
            val directKey = contractAddress
            if (cache.get(directKey) != null) {
                cache.evict(directKey)
                invalidatedKeys.add(directKey)
                logger.debug("Evicted cache entry for key: $directKey")
            }

            // Invalidate contract status cache entry (used by getContractStatus method)
            val statusKey = "status:$contractAddress"
            if (cache.get(statusKey) != null) {
                cache.evict(statusKey)
                invalidatedKeys.add(statusKey)
                logger.debug("Evicted cache entry for key: $statusKey")
            }

            if (invalidatedKeys.isNotEmpty()) {
                logger.info("Successfully invalidated ${invalidatedKeys.size} cache entries for contract $contractAddress: ${invalidatedKeys.joinToString(", ")}")
            } else {
                logger.debug("No cache entries found to invalidate for contract: $contractAddress")
            }

        } catch (e: Exception) {
            logger.error("Failed to invalidate cache for contract: $contractAddress, operation: $operationType", e)
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
     * Gets cache statistics for monitoring purposes.
     * 
     * @return Map of cache statistics or empty map if not available
     */
    fun getCacheStats(): Map<String, Any> {
        return try {
            val cache = cacheManager.getCache(CONTRACT_INFO_CACHE)
            if (cache != null) {
                // Try to get native Caffeine cache stats if available
                val nativeCache = cache.nativeCache
                if (nativeCache is com.github.benmanes.caffeine.cache.Cache<*, *>) {
                    val stats = nativeCache.stats()
                    mapOf<String, Any>(
                        "requestCount" to stats.requestCount(),
                        "hitCount" to stats.hitCount(),
                        "hitRate" to stats.hitRate(),
                        "missCount" to stats.missCount(),
                        "missRate" to stats.missRate(),
                        "loadCount" to stats.loadCount(),
                        "evictionCount" to stats.evictionCount(),
                        "estimatedSize" to nativeCache.estimatedSize()
                    )
                } else {
                    mapOf<String, Any>("status" to "cache available but stats not supported")
                }
            } else {
                mapOf<String, Any>("status" to "cache not available")
            }
        } catch (e: Exception) {
            logger.error("Error getting cache statistics", e)
            mapOf<String, Any>("error" to (e.message ?: "Unknown error"))
        }
    }
}