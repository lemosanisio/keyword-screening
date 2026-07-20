package br.com.screening.infrastructure.input.http.handler

import br.com.shared.domain.DomainException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private lateinit var handler: GlobalExceptionHandler

    @BeforeEach
    fun setUp() {
        handler = GlobalExceptionHandler()
    }

    @Test
    @DisplayName("handleValidation: MethodArgumentNotValidException returns 400 with field errors")
    fun handleValidationReturns400() {
        val fieldError1 = FieldError("request", "transactionId", "não pode ser vazio")
        val fieldError2 = FieldError("request", "description", "é obrigatório")
        val bindingResult = mockk<BindingResult>()
        every { bindingResult.fieldErrors } returns listOf(fieldError1, fieldError2)

        val ex = mockk<MethodArgumentNotValidException>()
        every { ex.bindingResult } returns bindingResult

        val response = handler.handleValidation(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body!!
        assertEquals(400, body.status)
        assertEquals("VALIDACAO_FALHOU", body.error)
        assertTrue(body.message.contains("transactionId"))
        assertTrue(body.message.contains("description"))
    }

    @Test
    @DisplayName("handleIllegalArgument: IllegalArgumentException returns 400")
    fun handleIllegalArgumentReturns400() {
        val ex = IllegalArgumentException("Valor inválido para campo X")

        val response = handler.handleIllegalArgument(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body!!
        assertEquals(400, body.status)
        assertEquals("VALIDACAO_FALHOU", body.error)
        assertEquals("Valor inválido para campo X", body.message)
    }

    @Test
    @DisplayName("handleIllegalArgument: null message uses default message")
    fun handleIllegalArgumentNullMessage() {
        val ex = IllegalArgumentException(null as String?)

        val response = handler.handleIllegalArgument(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Argumento inválido", response.body!!.message)
    }

    @Test
    @DisplayName("handleMessageNotReadable: HttpMessageNotReadableException returns 400")
    fun handleMessageNotReadableReturns400() {
        val ex = mockk<HttpMessageNotReadableException>()

        val response = handler.handleMessageNotReadable(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body!!
        assertEquals(400, body.status)
        assertEquals("REQUISICAO_INVALIDA", body.error)
        assertEquals("Corpo da requisição inválido ou campos obrigatórios ausentes", body.message)
    }

    @Test
    @DisplayName("handleDomainException: DomainException returns 422")
    fun handleDomainExceptionReturns422() {
        val ex = object : DomainException("REGRA_NEGOCIO", "Violação de regra de negócio") {}

        val response = handler.handleDomainException(ex)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        val body = response.body!!
        assertEquals(422, body.status)
        assertEquals("REGRA_NEGOCIO", body.error)
        assertEquals("Violação de regra de negócio", body.message)
    }

    @Test
    @DisplayName("handleGeneric: Exception returns 500 with correlation id")
    fun handleGenericReturns500() {
        val ex = RuntimeException("unexpected error")

        val response = handler.handleGeneric(ex)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        val body = response.body!!
        assertEquals(500, body.status)
        assertEquals("ERRO_INTERNO", body.error)
        assertTrue(body.message.startsWith("Erro interno. Referência:"))
    }
}
