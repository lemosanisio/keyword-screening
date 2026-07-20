package br.com.decision.infrastructure.input.http.handler

import br.com.decision.domain.exception.DuplicateActiveConfigException
import br.com.decision.domain.exception.InvalidConfigurationException
import br.com.decision.domain.exception.RuleConfigurationNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice(basePackages = ["br.com.decision.infrastructure.input.http"])
class DecisionExceptionHandler {

    private val log = LoggerFactory.getLogger(DecisionExceptionHandler::class.java)

    @ExceptionHandler(InvalidConfigurationException::class)
    fun handleInvalidConfiguration(ex: InvalidConfigurationException): ResponseEntity<ErrorResponse> {
        log.warn("Configuração inválida: {}", ex.message)
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

    @ExceptionHandler(RuleConfigurationNotFoundException::class)
    fun handleNotFound(ex: RuleConfigurationNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Recurso não encontrado: {}", ex.message)
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

    @ExceptionHandler(DuplicateActiveConfigException::class)
    fun handleConflict(ex: DuplicateActiveConfigException): ResponseEntity<ErrorResponse> {
        log.warn("Conflito de configuração ativa: {}", ex.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                timestamp = Instant.now(),
                status = 409,
                error = "Conflict",
                message = ex.message,
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
