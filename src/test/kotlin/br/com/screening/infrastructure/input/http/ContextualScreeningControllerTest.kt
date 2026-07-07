package br.com.screening.infrastructure.input.http

import br.com.screening.application.usecase.*
import br.com.screening.domain.exception.AuditNotFoundException
import br.com.screening.infrastructure.input.http.handler.ContextualScreeningExceptionHandler
import br.com.screening.infrastructure.input.http.handler.GlobalExceptionHandler
import br.com.shared.domain.valueobject.TransactionId
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@DisplayName("ContextualScreeningController")
class ContextualScreeningControllerTest {

    private val evaluateUseCase = mockk<EvaluateContextualScreeningUseCase>()
    private val registerDecisionUseCase = mockk<RegisterAnalystDecisionUseCase>()
    private val controller = ContextualScreeningController(evaluateUseCase, registerDecisionUseCase)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(ContextualScreeningExceptionHandler(), GlobalExceptionHandler())
        .build()
    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    @Test
    @DisplayName("evaluateContextualScreening: valid request returns 200 with correct body")
    fun evaluateValidRequestReturns200() {
        val useCaseResult = ContextualScreeningResultDto(
            classification = "SUSPICIOUS",
            confidence = 0.85,
            reason = "Keyword indicates suspicious activity",
            requiresAnalystReview = true
        )
        every { evaluateUseCase.execute(any()) } returns useCaseResult

        val requestBody = mapOf(
            "transactionId" to "TX-001",
            "ruleId" to "CONTEXTUAL_SCREENING",
            "description" to "pagamento terrorismo",
            "matchedKeyword" to "terrorismo"
        )

        mockMvc.post("/v1/rules/contextual-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.classification") { value("SUSPICIOUS") }
            jsonPath("$.confidence") { value(0.85) }
            jsonPath("$.reason") { value("Keyword indicates suspicious activity") }
            jsonPath("$.requiresAnalystReview") { value(true) }
        }
    }

    @Test
    @DisplayName("evaluateContextualScreening: missing transactionId returns 400")
    fun evaluateMissingTransactionIdReturns400() {
        val requestBody = mapOf(
            "description" to "pagamento terrorismo",
            "matchedKeyword" to "terrorismo"
        )

        mockMvc.post("/v1/rules/contextual-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    @DisplayName("registerAnalystDecision: valid request returns 200")
    fun registerDecisionValidReturns200() {
        val resultDto = AnalystDecisionResultDto(
            transactionId = "TX-001",
            ruleId = "CONTEXTUAL_SCREENING",
            analystDecision = "APPROVE",
            registeredAt = "2024-01-15T10:30:00Z"
        )
        every { registerDecisionUseCase.execute(any()) } returns resultDto

        val requestBody = mapOf(
            "transactionId" to "TX-001",
            "ruleId" to "CONTEXTUAL_SCREENING",
            "analystDecision" to "APPROVE"
        )

        mockMvc.post("/v1/rules/contextual-screening/decisions") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.transactionId") { value("TX-001") }
            jsonPath("$.ruleId") { value("CONTEXTUAL_SCREENING") }
            jsonPath("$.analystDecision") { value("APPROVE") }
        }
    }

    @Test
    @DisplayName("registerAnalystDecision: non-existent transaction returns 404")
    fun registerDecisionNotFoundReturns404() {
        every { registerDecisionUseCase.execute(any()) } throws AuditNotFoundException("TX-999", "CONTEXTUAL_SCREENING")

        val requestBody = mapOf(
            "transactionId" to "TX-999",
            "ruleId" to "CONTEXTUAL_SCREENING",
            "analystDecision" to "APPROVE"
        )

        mockMvc.post("/v1/rules/contextual-screening/decisions") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("AUDITORIA_NAO_ENCONTRADA") }
        }
    }

    @Test
    @DisplayName("evaluateContextualScreening: request without ruleId uses default CONTEXTUAL_SCREENING")
    fun evaluateWithoutRuleIdUsesDefault() {
        val useCaseResult = ContextualScreeningResultDto(
            classification = "SUSPICIOUS",
            confidence = 0.97,
            reason = "Suspicious activity detected",
            requiresAnalystReview = true
        )
        every { evaluateUseCase.execute(any()) } returns useCaseResult

        val requestBody = mapOf(
            "transactionId" to "TX-002",
            "description" to "pagamento conta de luz",
            "matchedKeyword" to "luz"
        )

        mockMvc.post("/v1/rules/contextual-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.classification") { value("SUSPICIOUS") }
            jsonPath("$.requiresAnalystReview") { value(true) }
        }
    }

    @Test
    @DisplayName("registerAnalystDecision: request without ruleId uses default CONTEXTUAL_SCREENING")
    fun registerDecisionWithoutRuleIdUsesDefault() {
        val resultDto = AnalystDecisionResultDto(
            transactionId = "TX-003",
            ruleId = "CONTEXTUAL_SCREENING",
            analystDecision = "REJECT",
            registeredAt = "2024-01-15T10:30:00Z"
        )
        every { registerDecisionUseCase.execute(any()) } returns resultDto

        val requestBody = mapOf(
            "transactionId" to "TX-003",
            "analystDecision" to "REJECT"
        )

        mockMvc.post("/v1/rules/contextual-screening/decisions") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.transactionId") { value("TX-003") }
            jsonPath("$.analystDecision") { value("REJECT") }
        }
    }

    @Test
    @DisplayName("evaluateContextualScreening: INCONCLUSIVE classification")
    fun evaluateReturnsInconclusive() {
        val useCaseResult = ContextualScreeningResultDto(
            classification = "INCONCLUSIVE",
            confidence = 0.50,
            reason = "Inconclusive analysis",
            requiresAnalystReview = true
        )
        every { evaluateUseCase.execute(any()) } returns useCaseResult

        val requestBody = mapOf(
            "transactionId" to "TX-004",
            "ruleId" to "CONTEXTUAL_SCREENING",
            "description" to "pagamento suspeito",
            "matchedKeyword" to "suspeito"
        )

        mockMvc.post("/v1/rules/contextual-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.classification") { value("INCONCLUSIVE") }
            jsonPath("$.requiresAnalystReview") { value(true) }
        }
    }

    @Test
    @DisplayName("evaluateContextualScreening: NOT_SUSPICIOUS classification with high confidence")
    fun evaluateReturnsNotSuspicious() {
        val useCaseResult = ContextualScreeningResultDto(
            classification = "NOT_SUSPICIOUS",
            confidence = 0.98,
            reason = "Normal operation",
            requiresAnalystReview = false
        )
        every { evaluateUseCase.execute(any()) } returns useCaseResult

        val requestBody = mapOf(
            "transactionId" to "TX-005",
            "ruleId" to "CONTEXTUAL_SCREENING",
            "description" to "pagamento conta de luz",
            "matchedKeyword" to "luz"
        )

        mockMvc.post("/v1/rules/contextual-screening/evaluate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(requestBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.classification") { value("NOT_SUSPICIOUS") }
            jsonPath("$.confidence") { value(0.98) }
            jsonPath("$.requiresAnalystReview") { value(false) }
        }
    }
}
