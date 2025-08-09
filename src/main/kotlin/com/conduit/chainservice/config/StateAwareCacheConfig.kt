package com.conduit.chainservice.config

import com.conduit.chainservice.escrow.models.ContractInfo
import com.conduit.chainservice.escrow.models.ContractStatus
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.time.Duration
import java.time.Instant

/**
 * STATE-AWARE Cache configuration optimized for blockchain immutability patterns.
 * 
 * KEY INSIGHT: Blockchain data has different immutability characteristics:
 * - IMMUTABLE FINAL STATES (CLAIMED, RESOLVED, EXPIRED): Never expire, only evict when full
 * - MUTABLE ACTIVE STATES (CREATED, ACTIVE, DISPUTED): Short TTL for real-time updates
 * - TRANSACTION EVENTS: Completely immutable, never expire
 * - GAS/BLOCK DATA: Network data that changes frequently
 * 
 * This dramatically improves cache hit rates for historical contract queries.
 */
@Configuration
@EnableCaching
class StateAwareCacheConfig {

    private val logger = LoggerFactory.getLogger(StateAwareCacheConfig::class.java)

    companion object {
        // Cache names with state awareness
        const val CONTRACT_INFO_IMMUTABLE_CACHE = "contractInfoImmutable"     // Final states only
        const val CONTRACT_INFO_MUTABLE_CACHE = "contractInfoMutable"         // Active states only
        const val CONTRACT_STATE_IMMUTABLE_CACHE = "contractStateImmutable"   // Final state data
        const val CONTRACT_STATE_MUTABLE_CACHE = "contractStateMutable"       // Active state data
        const val TRANSACTION_DATA_CACHE = "transactionData"                  // Always immutable
        const val GAS_PRICE_CACHE = "gasPrice"                               // Network data
        const val BLOCK_DATA_CACHE = "blockData"                             // Network data
        
        // TTL settings - Immutable caches have no TTL (only size-based eviction)
        private val MUTABLE_CONTRACT_INFO_TTL = Duration.ofMinutes(5)         // Reduced for active contracts
        private val MUTABLE_CONTRACT_STATE_TTL = Duration.ofMinutes(3)        // Reduced for active state
        private val GAS_PRICE_TTL = Duration.ofSeconds(30)                    // Network data
        private val BLOCK_DATA_TTL = Duration.ofSeconds(15)                   // Network data
        
        // Cache size limits - Larger for immutable caches since they keep data longer
        private const val IMMUTABLE_CONTRACT_INFO_MAX_SIZE = 50000L           // Much larger
        private const val MUTABLE_CONTRACT_INFO_MAX_SIZE = 5000L              // Smaller active set
        private const val IMMUTABLE_CONTRACT_STATE_MAX_SIZE = 75000L          // Largest for state
        private const val MUTABLE_CONTRACT_STATE_MAX_SIZE = 7500L             // Active contracts
        private const val TRANSACTION_DATA_MAX_SIZE = 100000L                 // Huge - events never change
        private const val GAS_PRICE_MAX_SIZE = 100L
        private const val BLOCK_DATA_MAX_SIZE = 1000L
        
        // Final states that should be cached indefinitely
        val IMMUTABLE_STATES = setOf(
            ContractStatus.CLAIMED,
            ContractStatus.RESOLVED
        )
        
        // Active states that need TTL for real-time updates
        val MUTABLE_STATES = setOf(
            ContractStatus.CREATED,
            ContractStatus.ACTIVE,
            ContractStatus.DISPUTED,
            ContractStatus.EXPIRED
        )
    }

    @Bean
    @Primary
    fun stateAwareCacheManager(): SimpleCacheManager {
        logger.info("Initializing STATE-AWARE cache manager optimized for blockchain immutability")
        
        val cacheManager = SimpleCacheManager()
        
        val caches = listOf(
            // Immutable caches - NO TTL, only size-based LRU eviction
            createImmutableContractInfoCache(),
            createImmutableContractStateCache(),
            createTransactionDataCache(),
            
            // Mutable caches - SHORT TTL for real-time updates
            createMutableContractInfoCache(),
            createMutableContractStateCache(),
            
            // Network data caches - Keep existing TTL behavior
            createGasPriceCache(),
            createBlockDataCache()
        )
        
        cacheManager.setCaches(caches)
        
        logger.info("STATE-AWARE cache manager initialized with ${caches.size} caches")
        logger.info("Immutable caches (no TTL): ${IMMUTABLE_STATES.joinToString()}")
        logger.info("Mutable caches (${MUTABLE_CONTRACT_INFO_TTL.toMinutes()}min TTL): ${MUTABLE_STATES.joinToString()}")
        
        return cacheManager
    }

    /**
     * IMMUTABLE CONTRACT INFO CACHE
     * For contracts in final states (CLAIMED, RESOLVED, EXPIRED) that never change.
     * NO TTL - only LRU eviction when full.
     */
    private fun createImmutableContractInfoCache(): CaffeineCache {
        logger.info("Creating IMMUTABLE contract info cache - NO TTL, max size: $IMMUTABLE_CONTRACT_INFO_MAX_SIZE")
        
        val caffeine = Caffeine.newBuilder()
            .maximumSize(IMMUTABLE_CONTRACT_INFO_MAX_SIZE)
            // NO expireAfterWrite - immutable data never expires!
            .recordStats()
            .build<Any, Any>()
            
        return CaffeineCache(CONTRACT_INFO_IMMUTABLE_CACHE, caffeine)
    }

    /**
     * MUTABLE CONTRACT INFO CACHE WITH DYNAMIC TTL
     * For contracts in active states that can still change.
     * 
     * REVOLUTIONARY FEATURE: ACTIVE contracts get TTL matching their actual expiry time!
     * - ACTIVE contracts: TTL = expiryTimestamp - currentTime (automatic expiry on contract expiration)
     * - Other mutable states: Fixed 5-minute TTL for real-time updates
     * 
     * This ensures ACTIVE contracts automatically expire from cache exactly when they become EXPIRED,
     * triggering fresh blockchain queries that will return the correct EXPIRED status.
     */
    private fun createMutableContractInfoCache(): CaffeineCache {
        logger.info("Creating MUTABLE contract info cache with DYNAMIC TTL for ACTIVE contracts, max size: $MUTABLE_CONTRACT_INFO_MAX_SIZE")
        
        val caffeine = Caffeine.newBuilder()
            .maximumSize(MUTABLE_CONTRACT_INFO_MAX_SIZE)
            .expireAfter(object : Expiry<Any, Any> {
                override fun expireAfterCreate(key: Any, value: Any, currentTime: Long): Long {
                    return calculateDynamicTTL(value, "create")
                }
                
                override fun expireAfterUpdate(key: Any, value: Any, currentTime: Long, currentDuration: Long): Long {
                    return calculateDynamicTTL(value, "update")
                }
                
                override fun expireAfterRead(key: Any, value: Any, currentTime: Long, currentDuration: Long): Long {
                    // Don't change TTL on reads - keep existing duration
                    return currentDuration
                }
                
                private fun calculateDynamicTTL(value: Any, operation: String): Long {
                    return try {
                        if (value is ContractInfo && value.status == ContractStatus.ACTIVE) {
                            val currentTime = Instant.now().epochSecond
                            val timeToExpiry = value.expiryTimestamp - currentTime
                            
                            if (timeToExpiry > 0) {
                                val dynamicTTL = Duration.ofSeconds(timeToExpiry).toNanos()
                                logger.debug("DYNAMIC TTL: ACTIVE contract ${value.contractAddress} cached with TTL ${timeToExpiry}s (expires at ${value.expiryTimestamp}) on $operation")
                                dynamicTTL
                            } else {
                                // Contract already expired - expire immediately from cache
                                logger.debug("DYNAMIC TTL: ACTIVE contract ${value.contractAddress} already expired (${timeToExpiry}s ago), immediate cache expiry on $operation")
                                0L
                            }
                        } else {
                            // Non-ACTIVE contracts use standard TTL
                            val standardTTL = MUTABLE_CONTRACT_INFO_TTL.toNanos()
                            if (value is ContractInfo) {
                                logger.trace("STANDARD TTL: ${value.status} contract ${value.contractAddress} cached with standard TTL ${MUTABLE_CONTRACT_INFO_TTL.toMinutes()}min on $operation")
                            }
                            standardTTL
                        }
                    } catch (e: Exception) {
                        logger.warn("Error calculating dynamic TTL on $operation, falling back to standard TTL", e)
                        MUTABLE_CONTRACT_INFO_TTL.toNanos()
                    }
                }
            })
            .recordStats()
            .build<Any, Any>()
            
        return CaffeineCache(CONTRACT_INFO_MUTABLE_CACHE, caffeine)
    }

    /**
     * IMMUTABLE CONTRACT STATE CACHE
     * State data for contracts in final states.
     * NO TTL - only LRU eviction.
     */
    private fun createImmutableContractStateCache(): CaffeineCache {
        logger.info("Creating IMMUTABLE contract state cache - NO TTL, max size: $IMMUTABLE_CONTRACT_STATE_MAX_SIZE")
        
        val caffeine = Caffeine.newBuilder()
            .maximumSize(IMMUTABLE_CONTRACT_STATE_MAX_SIZE)
            // NO expireAfterWrite
            .recordStats()
            .build<Any, Any>()
            
        return CaffeineCache(CONTRACT_STATE_IMMUTABLE_CACHE, caffeine)
    }

    /**
     * MUTABLE CONTRACT STATE CACHE WITH DYNAMIC TTL
     * State data for contracts that can still change.
     * 
     * MATCHES CONTRACT INFO CACHE: Uses same dynamic TTL logic for consistency.
     * This ensures both contract info and state data expire simultaneously for ACTIVE contracts.
     */
    private fun createMutableContractStateCache(): CaffeineCache {
        logger.info("Creating MUTABLE contract state cache with DYNAMIC TTL for ACTIVE contracts, max size: $MUTABLE_CONTRACT_STATE_MAX_SIZE")
        
        val caffeine = Caffeine.newBuilder()
            .maximumSize(MUTABLE_CONTRACT_STATE_MAX_SIZE)
            .expireAfter(object : Expiry<Any, Any> {
                override fun expireAfterCreate(key: Any, value: Any, currentTime: Long): Long {
                    return calculateStateDynamicTTL(value, "create")
                }
                
                override fun expireAfterUpdate(key: Any, value: Any, currentTime: Long, currentDuration: Long): Long {
                    return calculateStateDynamicTTL(value, "update")
                }
                
                override fun expireAfterRead(key: Any, value: Any, currentTime: Long, currentDuration: Long): Long {
                    return currentDuration
                }
                
                @Suppress("UNCHECKED_CAST")
                private fun calculateStateDynamicTTL(value: Any, operation: String): Long {
                    return try {
                        if (value is Map<*, *>) {
                            val stateMap = value as Map<String, Any>
                            val expiryTimestamp = stateMap["expiryTimestamp"] as? Long
                            val funded = stateMap["funded"] as? Boolean ?: false
                            val disputed = stateMap["disputed"] as? Boolean ?: false
                            val resolved = stateMap["resolved"] as? Boolean ?: false
                            val claimed = stateMap["claimed"] as? Boolean ?: false
                            
                            // Calculate status from state data
                            val currentTime = Instant.now().epochSecond
                            val isActive = funded && !disputed && !resolved && !claimed && 
                                          expiryTimestamp != null && currentTime <= expiryTimestamp
                            
                            if (isActive && expiryTimestamp != null) {
                                val timeToExpiry = expiryTimestamp - currentTime
                                if (timeToExpiry > 0) {
                                    val dynamicTTL = Duration.ofSeconds(timeToExpiry).toNanos()
                                    logger.debug("DYNAMIC STATE TTL: ACTIVE contract state cached with TTL ${timeToExpiry}s on $operation")
                                    dynamicTTL
                                } else {
                                    logger.debug("DYNAMIC STATE TTL: ACTIVE contract state already expired, immediate cache expiry on $operation")
                                    0L
                                }
                            } else {
                                val standardTTL = MUTABLE_CONTRACT_STATE_TTL.toNanos()
                                logger.trace("STANDARD STATE TTL: Non-ACTIVE contract state cached with standard TTL ${MUTABLE_CONTRACT_STATE_TTL.toMinutes()}min on $operation")
                                standardTTL
                            }
                        } else {
                            MUTABLE_CONTRACT_STATE_TTL.toNanos()
                        }
                    } catch (e: Exception) {
                        logger.warn("Error calculating dynamic state TTL on $operation, falling back to standard TTL", e)
                        MUTABLE_CONTRACT_STATE_TTL.toNanos()
                    }
                }
            })
            .recordStats()
            .build<Any, Any>()
            
        return CaffeineCache(CONTRACT_STATE_MUTABLE_CACHE, caffeine)
    }

    /**
     * TRANSACTION DATA CACHE
     * Blockchain events are ALWAYS immutable once written.
     * NO TTL - largest cache size since events never change.
     */
    private fun createTransactionDataCache(): CaffeineCache {
        logger.info("Creating TRANSACTION DATA cache - IMMUTABLE events, NO TTL, max size: $TRANSACTION_DATA_MAX_SIZE")
        
        val caffeine = Caffeine.newBuilder()
            .maximumSize(TRANSACTION_DATA_MAX_SIZE)
            // NO expireAfterWrite - blockchain events are permanent!
            .recordStats()
            .build<Any, Any>()
            
        return CaffeineCache(TRANSACTION_DATA_CACHE, caffeine)
    }

    private fun createGasPriceCache(): CaffeineCache {
        logger.info("Creating gas price cache with TTL: $GAS_PRICE_TTL, max size: $GAS_PRICE_MAX_SIZE")
        
        val caffeine = Caffeine.newBuilder()
            .maximumSize(GAS_PRICE_MAX_SIZE)
            .expireAfterWrite(GAS_PRICE_TTL)
            .recordStats()
            .build<Any, Any>()
            
        return CaffeineCache(GAS_PRICE_CACHE, caffeine)
    }

    private fun createBlockDataCache(): CaffeineCache {
        logger.info("Creating block data cache with TTL: $BLOCK_DATA_TTL, max size: $BLOCK_DATA_MAX_SIZE")
        
        val caffeine = Caffeine.newBuilder()
            .maximumSize(BLOCK_DATA_MAX_SIZE)
            .expireAfterWrite(BLOCK_DATA_TTL)
            .recordStats()
            .build<Any, Any>()
            
        return CaffeineCache(BLOCK_DATA_CACHE, caffeine)
    }

    /**
     * Helper method to determine if a contract status represents immutable data
     */
    fun isImmutableState(status: ContractStatus): Boolean {
        return status in IMMUTABLE_STATES
    }

    /**
     * Helper method to get the appropriate cache name based on contract status
     */
    fun getContractInfoCacheName(status: ContractStatus): String {
        return if (isImmutableState(status)) {
            CONTRACT_INFO_IMMUTABLE_CACHE
        } else {
            CONTRACT_INFO_MUTABLE_CACHE
        }
    }

    /**
     * Helper method to get the appropriate state cache name based on contract status
     */
    fun getContractStateCacheName(status: ContractStatus): String {
        return if (isImmutableState(status)) {
            CONTRACT_STATE_IMMUTABLE_CACHE
        } else {
            CONTRACT_STATE_MUTABLE_CACHE
        }
    }
}