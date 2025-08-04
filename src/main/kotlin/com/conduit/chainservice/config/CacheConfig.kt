package com.conduit.chainservice.config

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
 * Cache configuration for blockchain data with appropriate TTLs for different data types.
 * 
 * Cache Strategy:
 * - Contract Info: 2 minutes TTL (state changes rarely)
 * - Gas Price: 30 seconds TTL (changes frequently)
 * - Block Data: 15 seconds TTL (blocks every ~2 seconds on Avalanche)
 */
@Configuration
@EnableCaching
class CacheConfig {

    private val logger = LoggerFactory.getLogger(CacheConfig::class.java)

    companion object {
        // Cache names - Two-level caching strategy
        const val CONTRACT_INFO_CACHE = "contractInfo"
        const val CONTRACT_STATE_CACHE = "contractState"  // Level 1: Contract state data
        const val TRANSACTION_DATA_CACHE = "transactionData"  // Level 2: Event/transaction data
        const val GAS_PRICE_CACHE = "gasPrice"
        const val BLOCK_DATA_CACHE = "blockData"
        
        // TTL settings - Optimized for performance
        private val CONTRACT_INFO_TTL = Duration.ofMinutes(2)
        private val CONTRACT_STATE_TTL = Duration.ofMinutes(1)  // Shorter TTL for state data
        private val TRANSACTION_DATA_TTL = Duration.ofMinutes(5)  // Longer TTL for historical data
        private val GAS_PRICE_TTL = Duration.ofSeconds(30)
        private val BLOCK_DATA_TTL = Duration.ofSeconds(15)
        
        // Cache size limits - Increased for batch operations
        private const val CONTRACT_INFO_MAX_SIZE = 10000L
        private const val CONTRACT_STATE_MAX_SIZE = 15000L  // Larger for state cache
        private const val TRANSACTION_DATA_MAX_SIZE = 20000L  // Largest for tx data
        private const val GAS_PRICE_MAX_SIZE = 100L
        private const val BLOCK_DATA_MAX_SIZE = 1000L
    }

    @Bean
    @Primary
    fun cacheManager(): SimpleCacheManager {
        logger.info("Initializing cache manager with Caffeine")
        
        val cacheManager = SimpleCacheManager()
        
        val caches = listOf(
            createContractInfoCache(),
            createContractStateCache(),
            createTransactionDataCache(),
            createGasPriceCache(),
            createBlockDataCache()
        )
        
        cacheManager.setCaches(caches)
        
        logger.info("Cache manager initialized with ${caches.size} caches")
        
        return cacheManager
    }

    private fun createContractInfoCache(): CaffeineCache {
        logger.info("Creating contract info cache with TTL: $CONTRACT_INFO_TTL, max size: $CONTRACT_INFO_MAX_SIZE")
        
        val caffeine = Caffeine.newBuilder()
            .maximumSize(CONTRACT_INFO_MAX_SIZE)
            .expireAfterWrite(CONTRACT_INFO_TTL)
            .recordStats() // Enable statistics for monitoring
            .build<Any, Any>()
            
        return CaffeineCache(CONTRACT_INFO_CACHE, caffeine)
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

    private fun createContractStateCache(): CaffeineCache {
        logger.info("Creating contract state cache with TTL: $CONTRACT_STATE_TTL, max size: $CONTRACT_STATE_MAX_SIZE")
        
        val caffeine = Caffeine.newBuilder()
            .maximumSize(CONTRACT_STATE_MAX_SIZE)
            .expireAfterWrite(CONTRACT_STATE_TTL)
            .recordStats()
            .build<Any, Any>()
            
        return CaffeineCache(CONTRACT_STATE_CACHE, caffeine)
    }

    private fun createTransactionDataCache(): CaffeineCache {
        logger.info("Creating transaction data cache with TTL: $TRANSACTION_DATA_TTL, max size: $TRANSACTION_DATA_MAX_SIZE")
        
        val caffeine = Caffeine.newBuilder()
            .maximumSize(TRANSACTION_DATA_MAX_SIZE)
            .expireAfterWrite(TRANSACTION_DATA_TTL)
            .recordStats()
            .build<Any, Any>()
            
        return CaffeineCache(TRANSACTION_DATA_CACHE, caffeine)
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
}