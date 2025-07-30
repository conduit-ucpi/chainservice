package com.conduit.chainservice.escrow

import com.utility.chainservice.AuthenticationProvider
import com.utility.chainservice.BlockchainRelayService
import com.utility.chainservice.plugin.BlockchainServicePlugin
import io.swagger.v3.oas.models.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class EscrowServicePlugin : BlockchainServicePlugin {

    private val logger = LoggerFactory.getLogger(EscrowServicePlugin::class.java)
    
    private lateinit var relayService: BlockchainRelayService
    private lateinit var authProvider: AuthenticationProvider

    override fun getPluginName(): String = "escrow-service"

    override fun getApiPrefix(): String = "/api/chain"

    override fun getOpenApiTags(): List<Tag> = listOf(
        Tag().apply {
            name = "Escrow Service"
            description = "Blockchain escrow contract management and transaction relay API"
        }
    )

    override fun initialize(relayService: BlockchainRelayService, authProvider: AuthenticationProvider) {
        logger.info("Initializing Escrow Service Plugin")
        this.relayService = relayService
        this.authProvider = authProvider
        logger.info("Escrow Service Plugin initialized successfully")
    }

    override fun getGasOperations(): List<Pair<String, String>> = listOf(
        "createContract" to "createContract",
        "approveUSDC" to "approveUSDC",
        "depositFunds" to "depositFunds", 
        "raiseDispute" to "raiseDispute",
        "claimFunds" to "claimFunds",
        "resolveDispute" to "resolveDispute"
    )

    fun getRelayService(): BlockchainRelayService = relayService
    fun getAuthProvider(): AuthenticationProvider = authProvider
}