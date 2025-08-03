package com.conduit.chainservice.validation

/**
 * Defines the expected API endpoints for the Email Service that chainservice depends on.
 * Based on the EmailServiceClient implementation.
 */
object EmailServiceClientSpec {
    
    fun getExpectedEndpoints(): List<ExpectedEndpoint> {
        return listOf(
            // Payment Notification Email - sent when funds are deposited
            ExpectedEndpoint(
                path = "/api/email/payment-notification",
                method = "POST",
                description = "Send payment notification email to seller",
                requestBodySchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "to" to mapOf("type" to "string"),
                        "buyerEmail" to mapOf("type" to "string"),
                        "link" to mapOf("type" to "string"),
                        "description" to mapOf("type" to "string"),
                        "amount" to mapOf("type" to "string"),
                        "currency" to mapOf("type" to "string"),
                        "payoutDateTime" to mapOf("type" to "string")
                    ),
                    "required" to listOf("to", "buyerEmail", "link", "description", "amount", "currency", "payoutDateTime")
                ),
                responseSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "success" to mapOf("type" to "boolean"),
                        "messageId" to mapOf("type" to "string", "nullable" to true),
                        "message" to mapOf("type" to "string")
                    ),
                    "required" to listOf("success", "message")
                ),
                requiresAuthentication = true,
                tags = listOf("email-notifications", "critical")
            ),
            
            // Dispute Raised Notification - sent when dispute is raised
            ExpectedEndpoint(
                path = "/api/email/dispute-raised",
                method = "POST",
                description = "Send dispute raised notification email",
                requestBodySchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "to" to mapOf("type" to "string"),
                        "buyerEmail" to mapOf("type" to "string"),
                        "amount" to mapOf("type" to "string"),
                        "currency" to mapOf("type" to "string"),
                        "description" to mapOf("type" to "string"),
                        "sellerEmail" to mapOf("type" to "string"),
                        "payoutDateTime" to mapOf("type" to "string"),
                        "productName" to mapOf("type" to "string")
                    ),
                    "required" to listOf("to", "buyerEmail", "amount", "currency", "description", "sellerEmail", "payoutDateTime", "productName")
                ),
                responseSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "success" to mapOf("type" to "boolean"),
                        "messageId" to mapOf("type" to "string", "nullable" to true),
                        "message" to mapOf("type" to "string")
                    ),
                    "required" to listOf("success", "message")
                ),
                requiresAuthentication = true,
                tags = listOf("email-notifications", "critical")
            ),
            
            // Dispute Resolved Notification - sent when dispute is resolved
            ExpectedEndpoint(
                path = "/api/email/dispute-resolved",
                method = "POST",
                description = "Send dispute resolved notification email",
                requestBodySchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "to" to mapOf("type" to "string"),
                        "amount" to mapOf("type" to "string"),
                        "currency" to mapOf("type" to "string"),
                        "buyerEmail" to mapOf("type" to "string"),
                        "sellerEmail" to mapOf("type" to "string"),
                        "description" to mapOf("type" to "string"),
                        "payoutDateTime" to mapOf("type" to "string"),
                        "sellerPercentAmount" to mapOf("type" to "string"),
                        "sellerActualAmount" to mapOf("type" to "string"),
                        "buyerPercentAmount" to mapOf("type" to "string"),
                        "buyerActualAmount" to mapOf("type" to "string")
                    ),
                    "required" to listOf("to", "amount", "currency", "buyerEmail", "sellerEmail", "description", "payoutDateTime", "sellerPercentAmount", "sellerActualAmount", "buyerPercentAmount", "buyerActualAmount")
                ),
                responseSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "success" to mapOf("type" to "boolean"),
                        "messageId" to mapOf("type" to "string", "nullable" to true),
                        "message" to mapOf("type" to "string")
                    ),
                    "required" to listOf("success", "message")
                ),
                requiresAuthentication = true,
                tags = listOf("email-notifications", "critical")
            )
        )
    }
    
    /**
     * Gets endpoints that are critical for chainservice operation.
     * If these fail, chainservice cannot function properly.
     */
    fun getCriticalEndpoints(): List<ExpectedEndpoint> {
        return getExpectedEndpoints().filter { "critical" in it.tags }
    }
}