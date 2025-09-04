package com.conduit.chainservice.config

import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import java.util.concurrent.TimeUnit

@Configuration
class Web3jTimeoutConfig {

    private val logger = LoggerFactory.getLogger(Web3jTimeoutConfig::class.java)

    @Value("\${blockchain.rpc-url}")
    private lateinit var rpcUrl: String

    @Value("\${blockchain.timeout.connect-seconds:30}")
    private var connectTimeoutSeconds: Long = 30

    @Value("\${blockchain.timeout.read-seconds:60}")
    private var readTimeoutSeconds: Long = 60

    @Value("\${blockchain.timeout.write-seconds:60}")
    private var writeTimeoutSeconds: Long = 60

    @Bean("web3j")
    @Primary
    fun web3jWithTimeout(): Web3j {
        logger.info("Configuring Web3j with enhanced timeouts: connect=${connectTimeoutSeconds}s, read=${readTimeoutSeconds}s, write=${writeTimeoutSeconds}s")

        val httpClient = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val httpService = HttpService(rpcUrl, httpClient)
        
        return Web3j.build(httpService)
    }
}