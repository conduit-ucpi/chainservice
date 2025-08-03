package com.conduit.chainservice.escrow.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Custom validation annotation for conditional email field requirements.
 * When email fields are provided, certain other fields become required for successful email notifications.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ConditionalEmailFieldsValidator::class])
annotation class ConditionalEmailFields(
    val message: String = "When email addresses are provided, required fields for email notifications must also be provided",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

/**
 * Validator for conditional email field requirements.
 * Checks that when email fields are provided, all required fields for email notifications are also present.
 */
class ConditionalEmailFieldsValidator : ConstraintValidator<ConditionalEmailFields, Any> {
    
    override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        
        return when (value::class.simpleName) {
            "RaiseDisputeRequest" -> validateRaiseDisputeRequest(value, context)
            "ResolveDisputeRequest" -> validateResolveDisputeRequest(value, context)
            else -> true // Skip validation for unsupported types
        }
    }
    
    private fun validateRaiseDisputeRequest(request: Any, context: ConstraintValidatorContext): Boolean {
        val clazz = request::class.java
        
        val buyerEmail = clazz.getDeclaredField("buyerEmail").apply { isAccessible = true }.get(request) as? String
        val sellerEmail = clazz.getDeclaredField("sellerEmail").apply { isAccessible = true }.get(request) as? String
        
        // If no email addresses provided, validation passes (no email notifications will be sent)
        if (buyerEmail.isNullOrBlank() || sellerEmail.isNullOrBlank()) {
            return true
        }
        
        // If BOTH email addresses are provided, ALL required fields become mandatory
        val amount = clazz.getDeclaredField("amount").apply { isAccessible = true }.get(request) as? String
        val payoutDateTime = clazz.getDeclaredField("payoutDateTime").apply { isAccessible = true }.get(request) as? String
        val contractDescription = clazz.getDeclaredField("contractDescription").apply { isAccessible = true }.get(request) as? String
        val productName = clazz.getDeclaredField("productName").apply { isAccessible = true }.get(request) as? String
        
        val violations = mutableListOf<String>()
        
        // Check for null, blank, or "N/A" values
        if (isInvalidField(amount)) violations.add("amount (cannot be null, empty, or 'N/A')")
        if (isInvalidField(payoutDateTime)) violations.add("payoutDateTime (cannot be null, empty, or 'N/A')")
        if (isInvalidField(contractDescription)) violations.add("contractDescription (cannot be null, empty, or 'N/A')")
        if (isInvalidField(productName)) violations.add("productName (cannot be null, empty, or 'N/A')")
        
        if (violations.isNotEmpty()) {
            context.disableDefaultConstraintViolation()
            context.buildConstraintViolationWithTemplate(
                "When both buyer and seller email addresses are provided, all email notification fields are required. Invalid fields: ${violations.joinToString(", ")}"
            ).addConstraintViolation()
            return false
        }
        
        return true
    }
    
    /**
     * Checks if a field value is invalid for email notifications.
     * Invalid means: null, blank, whitespace-only, or common problematic values
     */
    private fun isInvalidField(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        
        val trimmedValue = value.trim()
        val problematicValues = setOf("N/A", "null", "undefined", "")
        
        return problematicValues.any { it.equals(trimmedValue, ignoreCase = true) }
    }
    
    private fun validateResolveDisputeRequest(request: Any, context: ConstraintValidatorContext): Boolean {
        val clazz = request::class.java
        
        val buyerEmail = clazz.getDeclaredField("buyerEmail").apply { isAccessible = true }.get(request) as? String
        val sellerEmail = clazz.getDeclaredField("sellerEmail").apply { isAccessible = true }.get(request) as? String
        
        // If no email addresses provided, validation passes
        if (buyerEmail.isNullOrBlank() || sellerEmail.isNullOrBlank()) {
            return true
        }
        
        // If email addresses are provided, check required fields
        val amount = clazz.getDeclaredField("amount").apply { isAccessible = true }.get(request) as? String
        val payoutDateTime = clazz.getDeclaredField("payoutDateTime").apply { isAccessible = true }.get(request) as? String
        val contractDescription = clazz.getDeclaredField("contractDescription").apply { isAccessible = true }.get(request) as? String
        val sellerActualAmount = clazz.getDeclaredField("sellerActualAmount").apply { isAccessible = true }.get(request) as? String
        val buyerActualAmount = clazz.getDeclaredField("buyerActualAmount").apply { isAccessible = true }.get(request) as? String
        
        val violations = mutableListOf<String>()
        
        if (isInvalidField(amount)) violations.add("amount")
        if (isInvalidField(payoutDateTime)) violations.add("payoutDateTime")
        if (isInvalidField(contractDescription)) violations.add("contractDescription")
        if (isInvalidField(sellerActualAmount)) violations.add("sellerActualAmount")
        if (isInvalidField(buyerActualAmount)) violations.add("buyerActualAmount")
        
        if (violations.isNotEmpty()) {
            context.disableDefaultConstraintViolation()
            context.buildConstraintViolationWithTemplate(
                "When email addresses are provided, the following fields are required for email notifications: ${violations.joinToString(", ")}"
            ).addConstraintViolation()
            return false
        }
        
        return true
    }
}

/**
 * Utility object to check if all required email fields are present for sending notifications.
 */
object EmailFieldValidator {
    
    /**
     * Checks if a field value is valid for email notifications.
     * Valid means: not null, not blank, and not any common problematic values
     */
    private fun isValidEmailField(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        
        val trimmedValue = value.trim()
        val problematicValues = setOf("N/A", "null", "undefined", "")
        
        return !problematicValues.any { it.equals(trimmedValue, ignoreCase = true) }
    }
    
    /**
     * Checks if all required fields for dispute raised email are present and valid.
     */
    fun canSendDisputeRaisedEmail(
        buyerEmail: String?,
        sellerEmail: String?,
        amount: String?,
        payoutDateTime: String?,
        contractDescription: String?,
        productName: String?
    ): Boolean {
        return isValidEmailField(buyerEmail) &&
               isValidEmailField(sellerEmail) &&
               isValidEmailField(amount) &&
               isValidEmailField(payoutDateTime) &&
               isValidEmailField(contractDescription) &&
               isValidEmailField(productName)
    }
    
    /**
     * Checks if all required fields for dispute resolved email are present and valid.
     */
    fun canSendDisputeResolvedEmail(
        buyerEmail: String?,
        sellerEmail: String?,
        amount: String?,
        payoutDateTime: String?,
        contractDescription: String?,
        sellerActualAmount: String?,
        buyerActualAmount: String?
    ): Boolean {
        return isValidEmailField(buyerEmail) &&
               isValidEmailField(sellerEmail) &&
               isValidEmailField(amount) &&
               isValidEmailField(payoutDateTime) &&
               isValidEmailField(contractDescription) &&
               isValidEmailField(sellerActualAmount) &&
               isValidEmailField(buyerActualAmount)
    }
    
    /**
     * Checks if all required fields for payment notification email are present and valid.
     */
    fun canSendPaymentNotificationEmail(
        buyerEmail: String?,
        sellerEmail: String?,
        contractDescription: String?,
        amount: String?,
        payoutDateTime: String?
    ): Boolean {
        return isValidEmailField(buyerEmail) &&
               isValidEmailField(sellerEmail) &&
               isValidEmailField(contractDescription) &&
               isValidEmailField(amount) &&
               isValidEmailField(payoutDateTime)
    }
}