package com.conduit.chainservice.config

import com.conduit.chainservice.escrow.models.ContractStatus
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.time.Duration

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
            ContractStatus.RESOLVED, 
            ContractStatus.EXPIRED
        )
        
        // Active states that need TTL for real-time updates
        val MUTABLE_STATES = setOf(
            ContractStatus.CREATED,
            ContractStatus.ACTIVE,
            ContractStatus.DISPUTED
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
     * MUTABLE CONTRACT INFO CACHE  
     * For contracts in active states that can still change.
     * SHORT TTL for real-time updates.
     */
    private fun createMutableContractInfoCache(): CaffeineCache {
        logger.info("Creating MUTABLE contract info cache with TTL: $MUTABLE_CONTRACT_INFO_TTL, max size: $MUTABLE_CONTRACT_INFO_MAX_SIZE")
        
        val caffeine = Caffeine.newBuilder()
            .maximumSize(MUTABLE_CONTRACT_INFO_MAX_SIZE)
            .expireAfterWrite(MUTABLE_CONTRACT_INFO_TTL)
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
     * MUTABLE CONTRACT STATE CACHE
     * State data for contracts that can still change.
     * SHORT TTL for real-time updates.
     */
    private fun createMutableContractStateCache(): CaffeineCache {
        logger.info("Creating MUTABLE contract state cache with TTL: $MUTABLE_CONTRACT_STATE_TTL, max size: $MUTABLE_CONTRACT_STATE_MAX_SIZE")
        
        val caffeine = Caffeine.newBuilder()
            .maximumSize(MUTABLE_CONTRACT_STATE_MAX_SIZE)
            .expireAfterWrite(MUTABLE_CONTRACT_STATE_TTL)
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