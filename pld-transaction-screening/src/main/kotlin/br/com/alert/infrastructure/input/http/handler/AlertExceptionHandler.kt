package br.com.alert.infrastructure.input.http.handler

import br.com.alert.domain.exception.AlertNotFoundException
import br.com.alert.domain.exception.InvalidAlertTransitionException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice(basePackages = ["br.com.alert.infrastructure.input.http"])
class AlertExceptionHandler {

    private val log = LoggerFactory.getLogger(AlertExceptionHandler::class.java)

    @ExceptionHandler(AlertNotFoundException::class)
    fun handleAlertNotFound(ex: AlertNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Alerta não encontrado: {}", ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(
                timestamp = Instant.now(),
                status = 404,
                error = "Not Found",
                message = ex.message,
                details = null
            )
        )
    }

    @ExceptionHandler(InvalidAlertTransitionException::class)
    fun handleInvalidTransition(ex: InvalidAlertTransitionException): ResponseEntity<ErrorResponse> {
        log.warn("Transição de alerta inválida: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ErrorResponse(
                timestamp = Instant.now(),
                status = 422,
                error = "Unprocessable Entity",
                message = ex.message,
                details = null
            )
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.warn("Argumento inválido: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                timestamp = Instant.now(),
                status = 400,
                error = "Bad Request",
                message = ex.message ?: "Argumento inválido",
                details = null
            )
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                timestamp = Instant.now(),
                status = 400,
                error = "Bad Request",
                message = "Erro de validação nos campos da requisição",
                details = details
            )
        )
    }
}

data class ErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String,
    val details: String?
)
