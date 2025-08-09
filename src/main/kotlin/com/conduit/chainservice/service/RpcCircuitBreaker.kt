package com.conduit.chainservice.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Component
class RpcCircuitBreaker {
    
    private val logger = LoggerFactory.getLogger(RpcCircuitBreaker::class.java)
    
    // Track failure counts per operation type
    private val failureCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val lastFailureTime = ConcurrentHashMap<String, AtomicLong>()
    
    // Configuration
    private val maxFailures = 5 // Open circuit after 5 failures
    private val recoveryTimeMs = 60000L // 1 minute recovery time
    private val maxConcurrentRequests = 10 // Max concurrent RPC requests
    private val currentRequests = AtomicInteger(0)
    
    fun <T> executeWithCircuitBreaker(
        operationType: String,
        operation: () -> T
    ): T? {
        // Check if circuit should be open
        if (isCircuitOpen(operationType)) {
            logger.warn("Circuit breaker is OPEN for operation: $operationType, skipping request")
            return null
        }
        
        // Check concurrent request limit
        if (currentRequests.get() >= maxConcurrentRequests) {
            logger.warn("Too many concurrent RPC requests (${currentRequests.get()}/$maxConcurrentRequests), throttling operation: $operationType")
            return null
        }
        
        try {
            currentRequests.incrementAndGet()
            val result = operation()
            
            // Reset failure count on success
            failureCounts[operationType]?.set(0)
            
            return result
        } catch (e: Exception) {
            // Record failure
            val failures = failureCounts.computeIfAbsent(operationType) { AtomicInteger(0) }
            val failureCount = failures.incrementAndGet()
            lastFailureTime[operationType] = AtomicLong(System.currentTimeMillis())
            
            logger.warn("RPC operation failed: $operationType (failure count: $failureCount/$maxFailures)", e)
            
            if (failureCount >= maxFailures) {
                logger.error("Circuit breaker OPENED for operation: $operationType after $failureCount failures")
            }
            
            throw e
        } finally {
            currentRequests.decrementAndGet()
        }
    }
    
    private fun isCircuitOpen(operationType: String): Boolean {
        val failures = failureCounts[operationType]?.get() ?: 0
        
        if (failures < maxFailures) {
            return false
        }
        
        val lastFailure = lastFailureTime[operationType]?.get() ?: 0
        val timeSinceLastFailure = System.currentTimeMillis() - lastFailure
        
        if (timeSinceLastFailure > recoveryTimeMs) {
            logger.info("Circuit breaker recovery attempt for operation: $operationType")
            // Reset failure count for recovery attempt
            failureCounts[operationType]?.set(maxFailures - 1)
            return false
        }
        
        return true
    }
    
    fun getCircuitStatus(): Map<String, String> {
        return failureCounts.entries.associate { (operation, failures) ->
            val status = if (isCircuitOpen(operation)) "OPEN" else "CLOSED"
            val count = failures.get()
            operation to "$status (failures: $count/$maxFailures)"
        }
    }
    
    fun getCurrentLoad(): Int = currentRequests.get()
}