package br.com.screening.infrastructure.input.http.handler

import br.com.screening.domain.exception.AuditNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

@DisplayName("ContextualScreeningExceptionHandler")
class ContextualScreeningExceptionHandlerTest {

    private lateinit var handler: ContextualScreeningExceptionHandler

    @BeforeEach
    fun setUp() {
        handler = ContextualScreeningExceptionHandler()
    }

    @Test
    @DisplayName("handleNotFound: AuditNotFoundException returns 404 with correct body")
    fun handleNotFoundReturns404() {
        val ex = AuditNotFoundException("TX-001", "CONTEXTUAL_SCREENING")

        val response = handler.handleNotFound(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val body = response.body!!
        assertEquals(404, body.status)
        assertEquals("AUDITORIA_NAO_ENCONTRADA", body.error)
        assertEquals("Auditoria não encontrada para transactionId=TX-001 e ruleId=CONTEXTUAL_SCREENING", body.message)
        assertNotNull(body.timestamp)
    }

    @Test
    @DisplayName("handleNotFound: different transactionId and ruleId in message")
    fun handleNotFoundDifferentParams() {
        val ex = AuditNotFoundException("TX-999", "RULE_ABC")

        val response = handler.handleNotFound(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val body = response.body!!
        assertTrue(body.message.contains("TX-999"))
        assertTrue(body.message.contains("RULE_ABC"))
    }
}
