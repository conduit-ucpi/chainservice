package com.conduit.chainservice.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Service for tracking cache metrics and performance statistics.
 * 
 * Provides:
 * - Hit/miss rate tracking
 * - RPC call reduction metrics
 * - Performance statistics for monitoring
 */
@Service
class CacheMetricsService {

    private val logger = LoggerFactory.getLogger(CacheMetricsService::class.java)

    // Metrics storage
    private val requests = AtomicLong(0)
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val rpcCallsSaved = AtomicLong(0)
    private val totalRpcCalls = AtomicLong(0)
    private val batchQueries = AtomicLong(0)
    private val totalContractsRequested = AtomicLong(0)
    
    // Per-operation metrics
    private val operationMetrics = ConcurrentHashMap<String, OperationMetrics>()
    
    private val startTime = Instant.now()

    fun recordCacheRequest(operation: String, count: Int = 1) {
        requests.addAndGet(count.toLong())
        operationMetrics.computeIfAbsent(operation) { OperationMetrics() }.requests.addAndGet(count.toLong())
    }

    fun recordCacheHit(operation: String, count: Int = 1) {
        hits.addAndGet(count.toLong())
        operationMetrics.computeIfAbsent(operation) { OperationMetrics() }.hits.addAndGet(count.toLong())
    }

    fun recordCacheMiss(operation: String, count: Int = 1) {
        misses.addAndGet(count.toLong())
        operationMetrics.computeIfAbsent(operation) { OperationMetrics() }.misses.addAndGet(count.toLong())
    }

    fun recordBatchQueryStats(totalRequested: Int, cacheHits: Int, rpcCalls: Int) {
        batchQueries.incrementAndGet()
        totalContractsRequested.addAndGet(totalRequested.toLong())
        totalRpcCalls.addAndGet(rpcCalls.toLong())
        rpcCallsSaved.addAndGet((totalRequested - rpcCalls).toLong())
        
        recordCacheHit("batch", cacheHits)
        recordCacheMiss("batch", totalRequested - cacheHits)
        
        // Log significant cache performance
        val hitRate = if (totalRequested > 0) (cacheHits * 100 / totalRequested) else 0
        val rpcReduction = if (totalRequested > 0) ((totalRequested - rpcCalls) * 100 / totalRequested) else 0
        
        if (totalRequested >= 5) { // Only log for larger batches
            logger.info("Batch cache performance: $totalRequested contracts, $hitRate% hit rate, $rpcReduction% RPC reduction")
        }
    }

    fun getMetrics(): CacheMetrics {
        val totalRequests = requests.get()
        val totalHits = hits.get()
        val totalMisses = misses.get()
        val totalRpc = totalRpcCalls.get()
        val savedRpc = rpcCallsSaved.get()
        val totalContractRequests = totalContractsRequested.get()
        
        val hitRate = if (totalRequests > 0) (totalHits.toDouble() / totalRequests * 100) else 0.0
        val rpcReductionRate = if (totalContractRequests > 0) (savedRpc.toDouble() / totalContractRequests * 100) else 0.0
        
        return CacheMetrics(
            totalRequests = totalRequests,
            cacheHits = totalHits,
            cacheMisses = totalMisses,
            hitRate = hitRate,
            rpcCallsMade = totalRpc,
            rpcCallsSaved = savedRpc,
            rpcReductionRate = rpcReductionRate,
            batchQueries = batchQueries.get(),
            totalContractsRequested = totalContractRequests,
            uptime = java.time.Duration.between(startTime, Instant.now()),
            operationMetrics = operationMetrics.mapValues { (_, metrics) ->
                val opRequests = metrics.requests.get()
                val opHits = metrics.hits.get()
                val opMisses = metrics.misses.get()
                val opHitRate = if (opRequests > 0) (opHits.toDouble() / opRequests * 100) else 0.0
                
                OperationMetricsSnapshot(
                    requests = opRequests,
                    hits = opHits,
                    misses = opMisses,
                    hitRate = opHitRate
                )
            }
        )
    }

    fun logCurrentStats() {
        val metrics = getMetrics()
        logger.info(
            "Cache Statistics - " +
            "Requests: ${metrics.totalRequests}, " +
            "Hit Rate: ${"%.1f".format(metrics.hitRate)}%, " +
            "RPC Reduction: ${"%.1f".format(metrics.rpcReductionRate)}%, " +
            "RPC Calls Saved: ${metrics.rpcCallsSaved}/${metrics.rpcCallsSaved + metrics.rpcCallsMade}"
        )
    }

    fun resetMetrics() {
        logger.info("Resetting cache metrics")
        requests.set(0)
        hits.set(0)
        misses.set(0)
        rpcCallsSaved.set(0)
        totalRpcCalls.set(0)
        batchQueries.set(0)
        totalContractsRequested.set(0)
        operationMetrics.clear()
    }

    private data class OperationMetrics(
        val requests: AtomicLong = AtomicLong(0),
        val hits: AtomicLong = AtomicLong(0),
        val misses: AtomicLong = AtomicLong(0)
    )
}

data class CacheMetrics(
    val totalRequests: Long,
    val cacheHits: Long,
    val cacheMisses: Long,
    val hitRate: Double,
    val rpcCallsMade: Long,
    val rpcCallsSaved: Long,
    val rpcReductionRate: Double,
    val batchQueries: Long,
    val totalContractsRequested: Long,
    val uptime: java.time.Duration,
    val operationMetrics: Map<String, OperationMetricsSnapshot>
)

data class OperationMetricsSnapshot(
    val requests: Long,
    val hits: Long,
    val misses: Long,
    val hitRate: Double
)