package com.conduit.chainservice

import com.conduit.chainservice.config.BlockchainProperties
import com.conduit.chainservice.config.CorsProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(BlockchainProperties::class, CorsProperties::class)
class ChainServiceApplication

fun main(args: Array<String>) {
    runApplication<ChainServiceApplication>(*args)
}