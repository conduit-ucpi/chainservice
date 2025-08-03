package com.conduit.chainservice.validation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Generates detailed API validation reports.
 */
class ApiValidationReportGenerator {
    
    companion object {
        private val logger = LoggerFactory.getLogger(ApiValidationReportGenerator::class.java)
        private val objectMapper = ObjectMapper().apply {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
        
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                val timeout = System.getProperty("api.validation.timeout", "30000").toLong()
                val validator = ApiValidator(timeout)
                val generator = ApiValidationReportGenerator()
                
                logger.info("Generating API validation report...")
                
                val results = mutableListOf<ValidationResult>()
                
                // Validate User Service
                System.getProperty("USER_SERVICE_URL")?.let { url ->
                    val result = validator.validateUserService(url)
                    results.add(result)
                    logger.info("User Service validation completed")
                } ?: logger.warn("USER_SERVICE_URL not provided, skipping user service validation")
                
                // Validate Contract Service  
                System.getProperty("CONTRACT_SERVICE_URL")?.let { url ->
                    val result = validator.validateContractService(url)
                    results.add(result)
                    logger.info("Contract Service validation completed")
                } ?: logger.warn("CONTRACT_SERVICE_URL not provided, skipping contract service validation")
                
                // Validate Email Service
                System.getProperty("EMAIL_SERVICE_URL")?.let { url ->
                    val result = validator.validateEmailService(url)
                    results.add(result)
                    logger.info("Email Service validation completed")
                } ?: logger.warn("EMAIL_SERVICE_URL not provided, skipping email service validation")
                
                if (results.isEmpty()) {
                    logger.error("No services to validate - no service URLs provided")
                    return
                }
                
                // Generate reports
                generator.generateJsonReport(results)
                generator.generateMarkdownReport(results)
                generator.generateSummaryReport(results)
                
                logger.info("API validation reports generated successfully")
                
            } catch (e: Exception) {
                logger.error("Failed to generate API validation report", e)
                System.exit(1)
            }
        }
    }
    
    /**
     * Generates a JSON report suitable for CI/CD integration.
     */
    fun generateJsonReport(results: List<ValidationResult>) {
        try {
            val report = ApiValidationReport(
                timestamp = Instant.now().toString(),
                overallStatus = if (results.any { it.hasErrors() }) "FAILED" else "PASSED",
                totalServices = results.size,
                passedServices = results.count { !it.hasErrors() },
                failedServices = results.count { it.hasErrors() },
                results = results
            )
            
            val reportFile = File("build/reports/api-validation/validation-report.json")
            reportFile.parentFile.mkdirs()
            
            objectMapper.writeValue(reportFile, report)
            logger.info("JSON report generated: ${reportFile.absolutePath}")
            
        } catch (e: Exception) {
            logger.error("Failed to generate JSON report", e)
        }
    }
    
    /**
     * Generates a human-readable markdown report.
     */
    fun generateMarkdownReport(results: List<ValidationResult>) {
        try {
            val reportFile = File("build/reports/api-validation/validation-report.md")
            reportFile.parentFile.mkdirs()
            
            val markdown = buildString {
                appendLine("# API Validation Report")
                appendLine()
                appendLine("**Generated:** ${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}")
                appendLine("**Overall Status:** ${if (results.any { it.hasErrors() }) "❌ FAILED" else "✅ PASSED"}")
                appendLine()
                
                appendLine("## Summary")
                appendLine()
                appendLine("| Service | Status | Endpoints Validated | Errors | Warnings |")
                appendLine("|---------|--------|-------------------|---------|----------|")
                
                results.forEach { result ->
                    val status = if (result.hasErrors()) "❌ FAILED" else "✅ PASSED"
                    appendLine("| ${result.serviceName} | $status | ${result.validatedEndpoints.size} | ${result.errors.size} | ${result.warnings.size} |")
                }
                
                appendLine()
                
                // Detailed results for each service
                results.forEach { result ->
                    appendLine("## ${result.serviceName}")
                    appendLine()
                    appendLine("**Service URL:** ${result.serviceUrl}")
                    appendLine("**Available:** ${if (result.isServiceAvailable) "✅ Yes" else "❌ No"}")
                    appendLine("**Status:** ${if (result.hasErrors()) "❌ FAILED" else "✅ PASSED"}")
                    appendLine()
                    
                    if (result.errors.isNotEmpty()) {
                        appendLine("### Errors")
                        appendLine()
                        result.errors.forEach { error ->
                            appendLine("- **${error.type}**: ${error.message}")
                            error.endpoint?.let { appendLine("  - Endpoint: `${it}`") }
                            error.details?.let { appendLine("  - Details: ${it}") }
                        }
                        appendLine()
                    }
                    
                    if (result.warnings.isNotEmpty()) {
                        appendLine("### Warnings")
                        appendLine()
                        result.warnings.forEach { warning ->
                            appendLine("- **${warning.type}**: ${warning.message}")
                            warning.endpoint?.let { appendLine("  - Endpoint: `${it}`") }
                        }
                        appendLine()
                    }
                    
                    if (result.validatedEndpoints.isNotEmpty()) {
                        appendLine("### Validated Endpoints")
                        appendLine()
                        appendLine("| Method | Path | Status | Errors | Warnings |")
                        appendLine("|--------|------|--------|---------|----------|")
                        
                        result.validatedEndpoints.forEach { endpoint ->
                            val status = if (endpoint.isValid) "✅ Valid" else "❌ Invalid"
                            appendLine("| ${endpoint.method} | `${endpoint.path}` | $status | ${endpoint.errors.size} | ${endpoint.warnings.size} |")
                        }
                        appendLine()
                    }
                }
                
                appendLine("---")
                appendLine("*Report generated by Chain Service API Validation*")
            }
            
            reportFile.writeText(markdown)
            logger.info("Markdown report generated: ${reportFile.absolutePath}")
            
        } catch (e: Exception) {
            logger.error("Failed to generate Markdown report", e)
        }
    }
    
    /**
     * Generates a brief summary report for console output.
     */
    fun generateSummaryReport(results: List<ValidationResult>) {
        try {
            val reportFile = File("build/reports/api-validation/validation-summary.txt")
            reportFile.parentFile.mkdirs()
            
            val summary = buildString {
                appendLine("API VALIDATION SUMMARY")
                appendLine("=".repeat(50))
                appendLine("Generated: ${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}")
                appendLine()
                
                val totalErrors = results.sumOf { it.errors.size }
                val totalWarnings = results.sumOf { it.warnings.size }
                val totalEndpoints = results.sumOf { it.validatedEndpoints.size }
                
                appendLine("Overall Status: ${if (results.any { it.hasErrors() }) "FAILED" else "PASSED"}")
                appendLine("Services Validated: ${results.size}")
                appendLine("Endpoints Validated: $totalEndpoints")
                appendLine("Total Errors: $totalErrors")
                appendLine("Total Warnings: $totalWarnings")
                appendLine()
                
                results.forEach { result ->
                    val status = if (result.hasErrors()) "FAILED" else "PASSED"
                    appendLine("${result.serviceName}: $status (${result.errors.size} errors, ${result.warnings.size} warnings)")
                }
                
                if (totalErrors > 0) {
                    appendLine()
                    appendLine("CRITICAL ISSUES:")
                    results.forEach { result ->
                        result.errors.forEach { error ->
                            appendLine("- ${result.serviceName}: ${error.type} - ${error.message}")
                        }
                    }
                }
            }
            
            reportFile.writeText(summary)
            logger.info("Summary report generated: ${reportFile.absolutePath}")
            
            // Also print summary to console
            println()
            println(summary)
            
        } catch (e: Exception) {
            logger.error("Failed to generate summary report", e)
        }
    }
}

/**
 * Data class for the complete validation report.
 */
data class ApiValidationReport(
    val timestamp: String,
    val overallStatus: String,
    val totalServices: Int,
    val passedServices: Int,
    val failedServices: Int,
    val results: List<ValidationResult>
)