package br.com.screening.infrastructure.input.http.handler

import br.com.shared.domain.DomainException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant
import java.util.UUID

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        val error = ErrorResponse(
            timestamp = Instant.now(),
            status = 400,
            error = "VALIDACAO_FALHOU",
            message = message
        )
        return ResponseEntity.badRequest().body(error)
    }

    @ExceptionHandler(DomainException::class)
    fun handleDomainException(ex: DomainException): ResponseEntity<ErrorResponse> {
        val error = ErrorResponse(
            timestamp = Instant.now(),
            status = HttpStatus.UNPROCESSABLE_ENTITY.value(),
            error = ex.code,
            message = ex.message
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        val correlationId = UUID.randomUUID().toString()
        log.error("Erro inesperado [correlationId=$correlationId]", ex)
        val error = ErrorResponse(
            timestamp = Instant.now(),
            status = 500,
            error = "ERRO_INTERNO",
            message = "Erro interno. Referência: $correlationId"
        )
        return ResponseEntity.internalServerError().body(error)
    }
}
