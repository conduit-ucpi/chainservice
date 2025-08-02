package com.conduit.chainservice.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Component that logs cache statistics at regular intervals to monitor cache effectiveness.
 */
@Component
class CacheStatsLogger(
    private val cacheMetricsService: CacheMetricsService
) {

    private val logger = LoggerFactory.getLogger(CacheStatsLogger::class.java)

    /**
     * Log cache statistics every 5 minutes
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    fun logCacheStats() {
        try {
            val metrics = cacheMetricsService.getMetrics()
            
            // Only log if there's been some activity
            if (metrics.totalRequests > 0) {
                logger.info(
                    "Cache Performance Report - " +
                    "Total Requests: ${metrics.totalRequests}, " +
                    "Hit Rate: ${"%.1f".format(metrics.hitRate)}%, " +
                    "RPC Reduction: ${"%.1f".format(metrics.rpcReductionRate)}%, " +
                    "RPC Calls Saved: ${metrics.rpcCallsSaved}, " +
                    "Uptime: ${formatDuration(metrics.uptime)}"
                )
                
                // Log per-operation stats if there are multiple operations
                if (metrics.operationMetrics.size > 1) {
                    metrics.operationMetrics.forEach { (operation, opMetrics) ->
                        if (opMetrics.requests > 0) {
                            logger.info(
                                "Cache Stats [$operation] - " +
                                "Requests: ${opMetrics.requests}, " +
                                "Hit Rate: ${"%.1f".format(opMetrics.hitRate)}%"
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to log cache statistics", e)
        }
    }

    /**
     * Log cache statistics every hour with more detail
     */
    @Scheduled(fixedDelay = 3600000) // 1 hour
    fun logDetailedCacheStats() {
        try {
            val metrics = cacheMetricsService.getMetrics()
            
            if (metrics.totalRequests > 0) {
                logger.info("=== DETAILED CACHE PERFORMANCE REPORT ===")
                logger.info("Uptime: ${formatDuration(metrics.uptime)}")
                logger.info("Total Cache Requests: ${metrics.totalRequests}")
                logger.info("Cache Hits: ${metrics.cacheHits}")
                logger.info("Cache Misses: ${metrics.cacheMisses}")
                logger.info("Overall Hit Rate: ${"%.2f".format(metrics.hitRate)}%")
                logger.info("RPC Calls Made: ${metrics.rpcCallsMade}")
                logger.info("RPC Calls Saved: ${metrics.rpcCallsSaved}")
                logger.info("RPC Reduction Rate: ${"%.2f".format(metrics.rpcReductionRate)}%")
                logger.info("Batch Queries: ${metrics.batchQueries}")
                logger.info("Total Contracts Requested: ${metrics.totalContractsRequested}")
                
                if (metrics.totalContractsRequested > 0) {
                    val avgContractsPerBatch = metrics.totalContractsRequested.toDouble() / metrics.batchQueries
                    logger.info("Average Contracts per Batch: ${"%.1f".format(avgContractsPerBatch)}")
                }
                
                logger.info("============================================")
            }
        } catch (e: Exception) {
            logger.warn("Failed to log detailed cache statistics", e)
        }
    }

    private fun formatDuration(duration: java.time.Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart()
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}