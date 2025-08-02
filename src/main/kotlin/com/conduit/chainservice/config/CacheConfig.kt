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
        // Cache names
        const val CONTRACT_INFO_CACHE = "contractInfo"
        const val GAS_PRICE_CACHE = "gasPrice"
        const val BLOCK_DATA_CACHE = "blockData"
        
        // TTL settings
        private val CONTRACT_INFO_TTL = Duration.ofMinutes(2)
        private val GAS_PRICE_TTL = Duration.ofSeconds(30)
        private val BLOCK_DATA_TTL = Duration.ofSeconds(15)
        
        // Cache size limits
        private const val CONTRACT_INFO_MAX_SIZE = 10000L
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