package com.conduit.chainservice

import com.conduit.chainservice.config.EscrowProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(EscrowProperties::class)
@EnableScheduling
@ComponentScan(basePackages = ["com.conduit.chainservice", "com.utility.chainservice"])
class ChainServiceApplication

fun main(args: Array<String>) {
    runApplication<ChainServiceApplication>(*args)
}