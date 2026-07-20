package br.com.screening.infrastructure.input.http.handler

import br.com.screening.domain.exception.AuditNotFoundException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

/**
 * Handler específico do contexto Contextual Screening.
 * Trata exceções que mapeiam para status codes diferentes de 422.
 */
@RestControllerAdvice(basePackages = ["br.com.screening.infrastructure.input.http"])
@Order(Ordered.HIGHEST_PRECEDENCE)
class ContextualScreeningExceptionHandler {

    @ExceptionHandler(AuditNotFoundException::class)
    fun handleNotFound(ex: AuditNotFoundException): ResponseEntity<ErrorResponse> {
        val error = ErrorResponse(
            timestamp = Instant.now(),
            status = HttpStatus.NOT_FOUND.value(),
            error = ex.code,
            message = ex.message
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error)
    }
}
