package br.com.alert.infrastructure.input.http.handler

import br.com.alert.domain.exception.AlertNotFoundException
import br.com.alert.domain.exception.InvalidAlertTransitionException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException

@DisplayName("AlertExceptionHandler")
class AlertExceptionHandlerTest {

    private lateinit var handler: AlertExceptionHandler

    @BeforeEach
    fun setUp() {
        handler = AlertExceptionHandler()
    }

    @Test
    @DisplayName("handleAlertNotFound: AlertNotFoundException returns 404")
    fun handleAlertNotFoundReturns404() {
        val ex = AlertNotFoundException("Alerta com id=abc-123 não encontrado")

        val response = handler.handleAlertNotFound(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val body = response.body!!
        assertEquals(404, body.status)
        assertEquals("Not Found", body.error)
        assertEquals("Alerta com id=abc-123 não encontrado", body.message)
        assertNull(body.details)
        assertNotNull(body.timestamp)
    }

    @Test
    @DisplayName("handleInvalidTransition: InvalidAlertTransitionException returns 422")
    fun handleInvalidTransitionReturns422() {
        val ex = InvalidAlertTransitionException("Transição inválida: CLOSED → OPEN")

        val response = handler.handleInvalidTransition(ex)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        val body = response.body!!
        assertEquals(422, body.status)
        assertEquals("Unprocessable Entity", body.error)
        assertEquals("Transição inválida: CLOSED → OPEN", body.message)
        assertNull(body.details)
    }

    @Test
    @DisplayName("handleIllegalArgument: IllegalArgumentException returns 400")
    fun handleIllegalArgumentReturns400() {
        val ex = IllegalArgumentException("Status inválido: INVALID_STATUS")

        val response = handler.handleIllegalArgument(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body!!
        assertEquals(400, body.status)
        assertEquals("Bad Request", body.error)
        assertEquals("Status inválido: INVALID_STATUS", body.message)
        assertNull(body.details)
    }

    @Test
    @DisplayName("handleIllegalArgument: null message uses default")
    fun handleIllegalArgumentNullMessage() {
        val ex = IllegalArgumentException(null as String?)

        val response = handler.handleIllegalArgument(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Argumento inválido", response.body!!.message)
    }

    @Test
    @DisplayName("handleValidation: MethodArgumentNotValidException returns 400 with details")
    fun handleValidationReturns400() {
        val fieldError = FieldError("request", "status", "Status é obrigatório")
        val bindingResult = mockk<BindingResult>()
        every { bindingResult.fieldErrors } returns listOf(fieldError)

        val ex = mockk<MethodArgumentNotValidException>()
        every { ex.bindingResult } returns bindingResult

        val response = handler.handleValidation(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body!!
        assertEquals(400, body.status)
        assertEquals("Bad Request", body.error)
        assertEquals("Erro de validação nos campos da requisição", body.message)
        assertNotNull(body.details)
        assertTrue(body.details!!.contains("status"))
        assertTrue(body.details!!.contains("Status é obrigatório"))
    }

    @Test
    @DisplayName("handleValidation: multiple field errors joined with semicolons")
    fun handleValidationMultipleErrors() {
        val fieldError1 = FieldError("request", "field1", "erro 1")
        val fieldError2 = FieldError("request", "field2", "erro 2")
        val bindingResult = mockk<BindingResult>()
        every { bindingResult.fieldErrors } returns listOf(fieldError1, fieldError2)

        val ex = mockk<MethodArgumentNotValidException>()
        every { ex.bindingResult } returns bindingResult

        val response = handler.handleValidation(ex)

        val body = response.body!!
        assertTrue(body.details!!.contains("field1: erro 1"))
        assertTrue(body.details!!.contains("field2: erro 2"))
        assertTrue(body.details!!.contains(";"))
    }
}
