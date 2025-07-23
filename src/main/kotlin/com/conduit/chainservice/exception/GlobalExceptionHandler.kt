package com.conduit.chainservice.exception

import com.conduit.chainservice.model.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(
        ex: MethodArgumentNotValidException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.map { 
            "${it.field}: ${it.defaultMessage}" 
        }.joinToString(", ")

        logger.warn("Validation error: $errors")

        val errorResponse = ErrorResponse(
            error = "Validation Failed",
            message = errors,
            timestamp = Instant.now().toString()
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        ex: IllegalArgumentException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Illegal argument: ${ex.message}")

        val errorResponse = ErrorResponse(
            error = "Invalid Request",
            message = ex.message ?: "Invalid argument provided",
            timestamp = Instant.now().toString()
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(
        ex: RuntimeException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Runtime exception occurred", ex)

        val errorResponse = ErrorResponse(
            error = "Internal Server Error",
            message = "An unexpected error occurred while processing your request",
            timestamp = Instant.now().toString()
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected exception occurred", ex)

        val errorResponse = ErrorResponse(
            error = "Internal Server Error",
            message = "An unexpected error occurred",
            timestamp = Instant.now().toString()
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
}