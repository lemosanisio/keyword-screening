package br.com.screening.application.service

import br.com.screening.application.usecase.ContextualScreeningResultDto
import br.com.screening.application.usecase.EvaluateContextualScreeningCommand
import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.ContextualScreeningAudit
import br.com.screening.domain.model.HistoricalDecision
import br.com.screening.domain.port.ContextualScreeningAuditRepository
import br.com.screening.domain.port.HistoricalDecisionRepository
import br.com.screening.domain.port.LlmClassifierPort
import br.com.screening.domain.port.LlmResponse
import br.com.screening.domain.service.PromptBuilder
import br.com.screening.domain.service.ResponseNormalizer
import br.com.screening.domain.service.RoutingClassifier
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant

class ContextualScreeningServiceTest : StringSpec({

    val auditRepository = mockk<ContextualScreeningAuditRepository>()
    val historicalDecisionRepository = mockk<HistoricalDecisionRepository>()
    val llmClassifier = mockk<LlmClassifierPort>()
    val promptBuilder = PromptBuilder()
    val routingClassifier = RoutingClassifier()
    val responseNormalizer = ResponseNormalizer()
    val autoCloseThreshold = 0.95

    val service = ContextualScreeningService(
        auditRepository = auditRepository,
        historicalDecisionRepository = historicalDecisionRepository,
        llmClassifier = llmClassifier,
        promptBuilder = promptBuilder,
        routingClassifier = routingClassifier,
        responseNormalizer = responseNormalizer,
        properties = br.com.screening.infrastructure.configuration.ContextualScreeningProperties(autoCloseThreshold = autoCloseThreshold)
    )

    val command = EvaluateContextualScreeningCommand(
        transactionId = "TX-001",
        ruleId = "CONTEXTUAL_SCREENING",
        description = "Depósito de R$ 80.000 em espécie",
        matchedKeyword = "espécie"
    )

    beforeTest {
        clearAllMocks()
    }

    "full flow with successful LLM response — COMUNICAR maps to SUSPICIOUS" {
        val decisions = listOf(
            HistoricalDecision(
                id = 1L,
                keyword = "espécie",
                description = "Depósito anterior em espécie suspeito",
                analystDecision = Classification.SUSPICIOUS,
                createdAt = Instant.now()
            )
        )

        every { auditRepository.findByTransactionIdAndRuleId("TX-001", "CONTEXTUAL_SCREENING") } returns null
        every { historicalDecisionRepository.findByKeyword("espécie") } returns decisions
        every { llmClassifier.classify(any()) } returns LlmResponse(
            classification = "COMUNICAR",
            confidence = 0.92,
            reason = "Valor alto em espécie sem comprovação de origem",
            rawResponse = """{"decisao":"COMUNICAR","confianca":0.92,"justificativa":"Valor alto"}""",
            success = true
        )
        val auditSlot = slot<ContextualScreeningAudit>()
        every { auditRepository.save(capture(auditSlot)) } answers { auditSlot.captured }

        val result = service.execute(command)

        result.classification shouldBe "SUSPICIOUS"
        result.confidence shouldBe 0.92
        result.reason shouldBe "Valor alto em espécie sem comprovação de origem"
        result.requiresAnalystReview shouldBe true

        verify(exactly = 1) { llmClassifier.classify(any()) }
        verify(exactly = 1) { auditRepository.save(any()) }

        auditSlot.captured.finalClassification shouldBe Classification.SUSPICIOUS
        auditSlot.captured.finalConfidence shouldBe 0.92
        auditSlot.captured.llmClassification shouldBe "COMUNICAR"
    }

    "idempotency — existing audit returns cached result without calling LLM" {
        val existingAudit = ContextualScreeningAudit(
            id = 10L,
            transactionId = "TX-001",
            ruleId = "CONTEXTUAL_SCREENING",
            keyword = "espécie",
            prompt = "some prompt",
            modelResponse = """{"decisao":"COMUNICAR"}""",
            llmClassification = "COMUNICAR",
            llmConfidence = 0.88,
            finalClassification = Classification.SUSPICIOUS,
            finalConfidence = 0.88,
            requiresAnalystReview = true,
            reason = "Cached reason",
            createdAt = Instant.now()
        )

        every { auditRepository.findByTransactionIdAndRuleId("TX-001", "CONTEXTUAL_SCREENING") } returns existingAudit

        val result = service.execute(command)

        result shouldBe ContextualScreeningResultDto(
            classification = "SUSPICIOUS",
            confidence = 0.88,
            reason = "Cached reason",
            requiresAnalystReview = true
        )

        verify(exactly = 0) { llmClassifier.classify(any()) }
        verify(exactly = 0) { historicalDecisionRepository.findByKeyword(any()) }
        verify(exactly = 0) { auditRepository.save(any()) }
    }

    "LLM failure fallback — returns UNCERTAIN with confidence 0.00 and requiresReview=true" {
        every { auditRepository.findByTransactionIdAndRuleId("TX-001", "CONTEXTUAL_SCREENING") } returns null
        every { historicalDecisionRepository.findByKeyword("espécie") } returns emptyList()
        every { llmClassifier.classify(any()) } returns LlmResponse(
            classification = null,
            confidence = null,
            reason = null,
            rawResponse = null,
            success = false,
            errorMessage = "Timeout ao conectar com LLM"
        )
        val auditSlot = slot<ContextualScreeningAudit>()
        every { auditRepository.save(capture(auditSlot)) } answers { auditSlot.captured }

        val result = service.execute(command)

        result.classification shouldBe "UNCERTAIN"
        result.confidence shouldBe 0.00
        result.reason shouldBe "Timeout ao conectar com LLM"
        result.requiresAnalystReview shouldBe true

        auditSlot.captured.finalClassification shouldBe Classification.UNCERTAIN
        auditSlot.captured.finalConfidence shouldBe 0.00
        auditSlot.captured.modelResponse shouldBe "Timeout ao conectar com LLM"
    }

    "historical decisions failure — proceeds with empty list" {
        every { auditRepository.findByTransactionIdAndRuleId("TX-001", "CONTEXTUAL_SCREENING") } returns null
        every { historicalDecisionRepository.findByKeyword("espécie") } throws RuntimeException("DB connection failed")
        every { llmClassifier.classify(any()) } returns LlmResponse(
            classification = "NAO_COMUNICAR",
            confidence = 0.97,
            reason = "Contexto legítimo",
            rawResponse = """{"decisao":"NAO_COMUNICAR","confianca":0.97}""",
            success = true
        )
        val auditSlot = slot<ContextualScreeningAudit>()
        every { auditRepository.save(capture(auditSlot)) } answers { auditSlot.captured }

        val result = service.execute(command)

        result.classification shouldBe "FALSE_POSITIVE"
        result.confidence shouldBe 0.97
        result.requiresAnalystReview shouldBe false

        verify(exactly = 1) { llmClassifier.classify(any()) }
    }

    "mapping NAO_COMUNICAR to FALSE_POSITIVE" {
        every { auditRepository.findByTransactionIdAndRuleId("TX-001", "CONTEXTUAL_SCREENING") } returns null
        every { historicalDecisionRepository.findByKeyword("espécie") } returns emptyList()
        every { llmClassifier.classify(any()) } returns LlmResponse(
            classification = "NAO_COMUNICAR",
            confidence = 0.98,
            reason = "Operação rotineira",
            rawResponse = """{"decisao":"NAO_COMUNICAR","confianca":0.98}""",
            success = true
        )
        val auditSlot = slot<ContextualScreeningAudit>()
        every { auditRepository.save(capture(auditSlot)) } answers { auditSlot.captured }

        val result = service.execute(command)

        result.classification shouldBe "FALSE_POSITIVE"
        result.confidence shouldBe 0.98
        result.requiresAnalystReview shouldBe false

        auditSlot.captured.finalClassification shouldBe Classification.FALSE_POSITIVE
    }

    "mapping REVISAO_MANUAL to UNCERTAIN" {
        every { auditRepository.findByTransactionIdAndRuleId("TX-001", "CONTEXTUAL_SCREENING") } returns null
        every { historicalDecisionRepository.findByKeyword("espécie") } returns emptyList()
        every { llmClassifier.classify(any()) } returns LlmResponse(
            classification = "REVISAO_MANUAL",
            confidence = 0.50,
            reason = "Análise inconclusiva",
            rawResponse = """{"decisao":"REVISAO_MANUAL","confianca":0.50}""",
            success = true
        )
        val auditSlot = slot<ContextualScreeningAudit>()
        every { auditRepository.save(capture(auditSlot)) } answers { auditSlot.captured }

        val result = service.execute(command)

        result.classification shouldBe "UNCERTAIN"
        result.confidence shouldBe 0.50
        result.requiresAnalystReview shouldBe true

        auditSlot.captured.finalClassification shouldBe Classification.UNCERTAIN
    }

    "invalid classification from LLM normalizes to UNCERTAIN" {
        every { auditRepository.findByTransactionIdAndRuleId("TX-001", "CONTEXTUAL_SCREENING") } returns null
        every { historicalDecisionRepository.findByKeyword("espécie") } returns emptyList()
        every { llmClassifier.classify(any()) } returns LlmResponse(
            classification = "VALOR_INVALIDO",
            confidence = 0.75,
            reason = "Alguma razão",
            rawResponse = """{"decisao":"VALOR_INVALIDO","confianca":0.75}""",
            success = true
        )
        val auditSlot = slot<ContextualScreeningAudit>()
        every { auditRepository.save(capture(auditSlot)) } answers { auditSlot.captured }

        val result = service.execute(command)

        result.classification shouldBe "UNCERTAIN"
        result.confidence shouldBe 0.75
        result.requiresAnalystReview shouldBe true

        auditSlot.captured.finalClassification shouldBe Classification.UNCERTAIN
        auditSlot.captured.llmClassification shouldBe "VALOR_INVALIDO"
    }

    "confidence out of range — greater than 1.0 is clamped to 1.0" {
        every { auditRepository.findByTransactionIdAndRuleId("TX-001", "CONTEXTUAL_SCREENING") } returns null
        every { historicalDecisionRepository.findByKeyword("espécie") } returns emptyList()
        every { llmClassifier.classify(any()) } returns LlmResponse(
            classification = "NAO_COMUNICAR",
            confidence = 1.5,
            reason = "Alta confiança",
            rawResponse = """{"decisao":"NAO_COMUNICAR","confianca":1.5}""",
            success = true
        )
        val auditSlot = slot<ContextualScreeningAudit>()
        every { auditRepository.save(capture(auditSlot)) } answers { auditSlot.captured }

        val result = service.execute(command)

        result.classification shouldBe "FALSE_POSITIVE"
        result.confidence shouldBe 1.00
        result.requiresAnalystReview shouldBe false

        auditSlot.captured.finalConfidence shouldBe 1.00
        auditSlot.captured.llmConfidence shouldBe 1.5
    }

    "confidence out of range — negative value is clamped to 0.0" {
        every { auditRepository.findByTransactionIdAndRuleId("TX-001", "CONTEXTUAL_SCREENING") } returns null
        every { historicalDecisionRepository.findByKeyword("espécie") } returns emptyList()
        every { llmClassifier.classify(any()) } returns LlmResponse(
            classification = "COMUNICAR",
            confidence = -0.5,
            reason = "Baixa confiança",
            rawResponse = """{"decisao":"COMUNICAR","confianca":-0.5}""",
            success = true
        )
        val auditSlot = slot<ContextualScreeningAudit>()
        every { auditRepository.save(capture(auditSlot)) } answers { auditSlot.captured }

        val result = service.execute(command)

        result.classification shouldBe "SUSPICIOUS"
        result.confidence shouldBe 0.00
        result.requiresAnalystReview shouldBe true

        auditSlot.captured.finalConfidence shouldBe 0.00
        auditSlot.captured.llmConfidence shouldBe -0.5
    }
})
