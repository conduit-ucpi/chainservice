package com.conduit.chainservice.exception

import com.conduit.chainservice.model.ErrorResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.http.HttpStatus
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.context.request.WebRequest
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class GlobalExceptionHandlerTest {

    @Mock
    private lateinit var webRequest: WebRequest

    @Mock
    private lateinit var bindingResult: BindingResult

    private lateinit var globalExceptionHandler: GlobalExceptionHandler

    @BeforeEach
    fun setUp() {
        globalExceptionHandler = GlobalExceptionHandler()
    }

    @Test
    fun `handleValidationExceptions - single validation error`() {
        // Given
        val fieldError = FieldError("testObject", "testField", "Test error message")
        val exception = MethodArgumentNotValidException(mock(), bindingResult)
        
        whenever(bindingResult.fieldErrors).thenReturn(listOf(fieldError))

        // When
        val result = globalExceptionHandler.handleValidationExceptions(exception, webRequest)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertNotNull(result.body)
        
        val errorResponse = result.body!!
        assertEquals("Validation Failed", errorResponse.error)
        assertEquals("testField: Test error message", errorResponse.message)
        assertNotNull(errorResponse.timestamp)
        
        // Verify timestamp is recent (within 1 second)
        val timestamp = Instant.parse(errorResponse.timestamp)
        assertTrue(timestamp.isAfter(Instant.now().minusSeconds(1)))
    }

    @Test
    fun `handleValidationExceptions - multiple validation errors`() {
        // Given
        val fieldError1 = FieldError("testObject", "field1", "Error message 1")
        val fieldError2 = FieldError("testObject", "field2", "Error message 2")
        val fieldError3 = FieldError("testObject", "field3", "Error message 3")
        val exception = MethodArgumentNotValidException(mock(), bindingResult)
        
        whenever(bindingResult.fieldErrors).thenReturn(listOf(fieldError1, fieldError2, fieldError3))

        // When
        val result = globalExceptionHandler.handleValidationExceptions(exception, webRequest)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertNotNull(result.body)
        
        val errorResponse = result.body!!
        assertEquals("Validation Failed", errorResponse.error)
        assertTrue(errorResponse.message.contains("field1: Error message 1"))
        assertTrue(errorResponse.message.contains("field2: Error message 2"))
        assertTrue(errorResponse.message.contains("field3: Error message 3"))
        assertTrue(errorResponse.message.contains(", ")) // Check comma separation
    }

    @Test
    fun `handleValidationExceptions - empty validation errors`() {
        // Given
        val exception = MethodArgumentNotValidException(mock(), bindingResult)
        whenever(bindingResult.fieldErrors).thenReturn(emptyList())

        // When
        val result = globalExceptionHandler.handleValidationExceptions(exception, webRequest)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertNotNull(result.body)
        
        val errorResponse = result.body!!
        assertEquals("Validation Failed", errorResponse.error)
        assertEquals("", errorResponse.message) // Empty string for no errors
    }

    @Test
    fun `handleIllegalArgumentException - with message`() {
        // Given
        val errorMessage = "Invalid wallet address format"
        val exception = IllegalArgumentException(errorMessage)

        // When
        val result = globalExceptionHandler.handleIllegalArgumentException(exception, webRequest)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertNotNull(result.body)
        
        val errorResponse = result.body!!
        assertEquals("Invalid Request", errorResponse.error)
        assertEquals(errorMessage, errorResponse.message)
        assertNotNull(errorResponse.timestamp)
    }

    @Test
    fun `handleIllegalArgumentException - without message`() {
        // Given
        val exception = IllegalArgumentException(null as String?)

        // When
        val result = globalExceptionHandler.handleIllegalArgumentException(exception, webRequest)

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertNotNull(result.body)
        
        val errorResponse = result.body!!
        assertEquals("Invalid Request", errorResponse.error)
        assertEquals("Invalid argument provided", errorResponse.message)
        assertNotNull(errorResponse.timestamp)
    }

    @Test
    fun `handleRuntimeException - with message`() {
        // Given
        val errorMessage = "Database connection failed"
        val exception = RuntimeException(errorMessage)

        // When
        val result = globalExceptionHandler.handleRuntimeException(exception, webRequest)

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
        assertNotNull(result.body)
        
        val errorResponse = result.body!!
        assertEquals("Internal Server Error", errorResponse.error)
        assertEquals("An unexpected error occurred while processing your request", errorResponse.message)
        assertNotNull(errorResponse.timestamp)
    }

    @Test
    fun `handleRuntimeException - without message`() {
        // Given
        val exception = RuntimeException(null as String?)

        // When
        val result = globalExceptionHandler.handleRuntimeException(exception, webRequest)

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
        assertNotNull(result.body)
        
        val errorResponse = result.body!!
        assertEquals("Internal Server Error", errorResponse.error)
        assertEquals("An unexpected error occurred while processing your request", errorResponse.message)
    }

    @Test
    fun `handleGenericException - with message`() {
        // Given
        val errorMessage = "Network timeout"
        val exception = Exception(errorMessage)

        // When
        val result = globalExceptionHandler.handleGenericException(exception, webRequest)

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
        assertNotNull(result.body)
        
        val errorResponse = result.body!!
        assertEquals("Internal Server Error", errorResponse.error)
        assertEquals("An unexpected error occurred", errorResponse.message)
        assertNotNull(errorResponse.timestamp)
    }

    @Test
    fun `handleGenericException - without message`() {
        // Given
        val exception = Exception(null as String?)

        // When
        val result = globalExceptionHandler.handleGenericException(exception, webRequest)

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
        assertNotNull(result.body)
        
        val errorResponse = result.body!!
        assertEquals("Internal Server Error", errorResponse.error)
        assertEquals("An unexpected error occurred", errorResponse.message)
    }

    @Test
    fun `all error responses have consistent structure`() {
        // Given
        val validationException = MethodArgumentNotValidException(mock(), bindingResult)
        val illegalArgException = IllegalArgumentException("test")
        val runtimeException = RuntimeException("test")
        val genericException = Exception("test")
        
        whenever(bindingResult.fieldErrors).thenReturn(listOf(FieldError("obj", "field", "error")))

        // When
        val validationResult = globalExceptionHandler.handleValidationExceptions(validationException, webRequest)
        val illegalArgResult = globalExceptionHandler.handleIllegalArgumentException(illegalArgException, webRequest)
        val runtimeResult = globalExceptionHandler.handleRuntimeException(runtimeException, webRequest)
        val genericResult = globalExceptionHandler.handleGenericException(genericException, webRequest)

        // Then - All responses should have the same structure
        val responses = listOf(
            validationResult.body!!,
            illegalArgResult.body!!,
            runtimeResult.body!!,
            genericResult.body!!
        )

        responses.forEach { response ->
            assertNotNull(response.error)
            assertNotNull(response.message)
            assertNotNull(response.timestamp)
            assertTrue(response.error.isNotBlank())
            assertTrue(response.message.isNotBlank())
            assertTrue(response.timestamp.isNotBlank())
            
            // Verify timestamp format (ISO-8601)
            assertDoesNotThrow { Instant.parse(response.timestamp) }
        }
    }

    @Test
    fun `exception hierarchy is handled correctly`() {
        // RuntimeException should be caught by RuntimeException handler, not generic Exception handler
        val runtimeException = RuntimeException("runtime error")
        val result = globalExceptionHandler.handleRuntimeException(runtimeException, webRequest)
        
        assertEquals("An unexpected error occurred while processing your request", result.body?.message)
        
        // Generic Exception should be caught by generic handler
        val genericException = Exception("generic error")
        val genericResult = globalExceptionHandler.handleGenericException(genericException, webRequest)
        
        assertEquals("An unexpected error occurred", genericResult.body?.message)
    }
}