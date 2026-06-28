package br.com.screening.application.service

import br.com.screening.application.usecase.RegisterAnalystDecisionCommand
import br.com.screening.domain.exception.AuditNotFoundException
import br.com.screening.domain.exception.InvalidClassificationException
import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.ContextualScreeningAudit
import br.com.screening.domain.model.HistoricalDecision
import br.com.screening.domain.port.ContextualScreeningAuditRepository
import br.com.screening.domain.port.HistoricalDecisionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant

class AnalystDecisionServiceTest : StringSpec({

    val auditRepository = mockk<ContextualScreeningAuditRepository>()
    val historicalDecisionRepository = mockk<HistoricalDecisionRepository>()

    val service = AnalystDecisionService(
        auditRepository = auditRepository,
        historicalDecisionRepository = historicalDecisionRepository
    )

    val transactionId = "TX-100"
    val ruleId = "CONTEXTUAL_SCREENING"

    beforeTest {
        clearMocks(auditRepository, historicalDecisionRepository)
    }

    "valid decision FALSE_POSITIVE persists HistoricalDecision and updates analystDecision in audit" {
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

        result.transactionId shouldBe transactionId
        result.ruleId shouldBe ruleId
        result.analystDecision shouldBe "FALSE_POSITIVE"

        // Verify HistoricalDecision was persisted with correct data
        val captured = historicalDecisionSlot.captured
        captured.keyword shouldBe "terrorismo"
        captured.analystDecision shouldBe Classification.FALSE_POSITIVE

        verify(exactly = 1) { historicalDecisionRepository.save(any()) }
        verify(exactly = 1) { auditRepository.updateAnalystDecision(transactionId, ruleId, Classification.FALSE_POSITIVE) }
    }

    "audit not found throws AuditNotFoundException" {
        every { auditRepository.findByTransactionIdAndRuleId(transactionId, ruleId) } returns null

        val command = RegisterAnalystDecisionCommand(
            transactionId = transactionId,
            ruleId = ruleId,
            analystDecision = "SUSPICIOUS"
        )

        shouldThrow<AuditNotFoundException> {
            service.execute(command)
        }

        verify(exactly = 0) { historicalDecisionRepository.save(any()) }
        verify(exactly = 0) { auditRepository.updateAnalystDecision(any(), any(), any()) }
    }

    "invalid decision value throws InvalidClassificationException" {
        val command = RegisterAnalystDecisionCommand(
            transactionId = transactionId,
            ruleId = ruleId,
            analystDecision = "INVALID"
        )

        shouldThrow<InvalidClassificationException> {
            service.execute(command)
        }

        verify(exactly = 0) { auditRepository.findByTransactionIdAndRuleId(any(), any()) }
        verify(exactly = 0) { historicalDecisionRepository.save(any()) }
        verify(exactly = 0) { auditRepository.updateAnalystDecision(any(), any(), any()) }
    }
})
