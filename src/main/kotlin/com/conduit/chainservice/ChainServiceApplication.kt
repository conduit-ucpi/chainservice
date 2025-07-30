package com.conduit.chainservice

import com.conduit.chainservice.auth.AuthProperties
import com.conduit.chainservice.config.EscrowBlockchainProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties::class, EscrowBlockchainProperties::class)
@ComponentScan(basePackages = ["com.conduit.chainservice", "com.utility.chainservice"])
class ChainServiceApplication

fun main(args: Array<String>) {
    runApplication<ChainServiceApplication>(*args)
}