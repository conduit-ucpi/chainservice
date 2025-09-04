package com.conduit.chainservice

import com.conduit.chainservice.config.EscrowProperties
import com.conduit.chainservice.config.AuthProperties
import com.conduit.chainservice.config.BlockchainProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(exclude = [org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration::class])
@EnableConfigurationProperties(EscrowProperties::class, AuthProperties::class, BlockchainProperties::class)
@EnableScheduling
class ChainServiceApplication

fun main(args: Array<String>) {
    runApplication<ChainServiceApplication>(*args)
}