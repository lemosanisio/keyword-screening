package br.com.screening.application.service

import br.com.screening.application.usecase.RegisterAnalystDecisionCommand
import br.com.screening.domain.exception.AuditNotFoundException
import br.com.screening.domain.exception.InvalidClassificationException
import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.ContextualScreeningAudit
import br.com.screening.domain.model.HistoricalDecision
import br.com.screening.domain.port.ContextualScreeningAuditRepository
import br.com.screening.domain.port.HistoricalDecisionRepository
import br.com.shared.domain.valueobject.TransactionId
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class AnalystDecisionServiceTest {

    private val auditRepository = mockk<ContextualScreeningAuditRepository>()
    private val historicalDecisionRepository = mockk<HistoricalDecisionRepository>()

    private val service = AnalystDecisionService(
        auditRepository = auditRepository,
        historicalDecisionRepository = historicalDecisionRepository
    )

    private val transactionId = TransactionId("TX-100")
    private val ruleId = "CONTEXTUAL_SCREENING"

    @BeforeEach
    fun setup() {
        clearMocks(auditRepository, historicalDecisionRepository)
    }

    @Test
    @DisplayName("valid decision FALSE_POSITIVE persists HistoricalDecision and updates analystDecision in audit")
    fun validDecisionPersistsAndUpdatesAudit() {
        val audit = ContextualScreeningAudit(
            id = 1L,
            transactionId = transactionId,
            ruleId = ruleId,
            keyword = "terrorismo",
            prompt = "prompt-test",
            modelResponse = """{"decisao":"NAO_COMUNICAR"}""",
            llmClassification = "NAO_COMUNICAR",
            llmConfidence = 0.97,
            finalClassification = Classification.FALSE_POSITIVE,
            finalConfidence = 0.97,
            requiresAnalystReview = false,
            reason = "Contexto não suspeito",
            createdAt = Instant.now()
        )

        val historicalDecisionSlot = slot<HistoricalDecision>()

        every { auditRepository.findByTransactionIdAndRuleId(transactionId, ruleId) } returns audit
        every { historicalDecisionRepository.save(capture(historicalDecisionSlot)) } answers { historicalDecisionSlot.captured }
        every { auditRepository.updateAnalystDecision(transactionId, ruleId, Classification.FALSE_POSITIVE) } returns Unit

        val command = RegisterAnalystDecisionCommand(
            transactionId = transactionId,
            ruleId = ruleId,
            analystDecision = "FALSE_POSITIVE"
        )

        val result = service.execute(command)

        assertEquals(transactionId.value, result.transactionId)
        assertEquals(ruleId, result.ruleId)
        assertEquals("FALSE_POSITIVE", result.analystDecision)

        val captured = historicalDecisionSlot.captured
        assertEquals("terrorismo", captured.keyword)
        assertEquals(Classification.FALSE_POSITIVE, captured.analystDecision)

        verify(exactly = 1) { historicalDecisionRepository.save(any()) }
        verify(exactly = 1) { auditRepository.updateAnalystDecision(transactionId, ruleId, Classification.FALSE_POSITIVE) }
    }

    @Test
    @DisplayName("audit not found throws AuditNotFoundException")
    fun auditNotFoundThrowsException() {
        every { auditRepository.findByTransactionIdAndRuleId(transactionId, ruleId) } returns null

        val command = RegisterAnalystDecisionCommand(
            transactionId = transactionId,
            ruleId = ruleId,
            analystDecision = "SUSPICIOUS"
        )

        assertThrows(AuditNotFoundException::class.java) {
            service.execute(command)
        }

        verify(exactly = 0) { historicalDecisionRepository.save(any()) }
        verify(exactly = 0) { auditRepository.updateAnalystDecision(any(), any(), any()) }
    }

    @Test
    @DisplayName("invalid decision value throws InvalidClassificationException")
    fun invalidDecisionThrowsException() {
        val command = RegisterAnalystDecisionCommand(
            transactionId = transactionId,
            ruleId = ruleId,
            analystDecision = "INVALID"
        )

        assertThrows(InvalidClassificationException::class.java) {
            service.execute(command)
        }

        verify(exactly = 0) { auditRepository.findByTransactionIdAndRuleId(any(), any()) }
        verify(exactly = 0) { historicalDecisionRepository.save(any()) }
        verify(exactly = 0) { auditRepository.updateAnalystDecision(any(), any(), any()) }
    }
}
