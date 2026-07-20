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
import br.com.shared.domain.valueobject.TransactionId
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class ContextualScreeningServiceTest {

    private val auditRepository = mockk<ContextualScreeningAuditRepository>()
    private val historicalDecisionRepository = mockk<HistoricalDecisionRepository>()
    private val llmClassifier = mockk<LlmClassifierPort>()
    private val promptBuilder = PromptBuilder()
    private val routingClassifier = RoutingClassifier()
    private val responseNormalizer = ResponseNormalizer()
    private val autoCloseThreshold = 0.95

    private val service = ContextualScreeningService(
        auditRepository = auditRepository,
        historicalDecisionRepository = historicalDecisionRepository,
        llmClassifier = llmClassifier,
        promptBuilder = promptBuilder,
        routingClassifier = routingClassifier,
        responseNormalizer = responseNormalizer,
        properties = br.com.screening.infrastructure.configuration.ContextualScreeningProperties(autoCloseThreshold = autoCloseThreshold)
    )

    private val command = EvaluateContextualScreeningCommand(
        transactionId = TransactionId("TX-001"),
        ruleId = "CONTEXTUAL_SCREENING",
        description = "Depósito de R$ 80.000 em espécie",
        matchedKeyword = "espécie"
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    @DisplayName("full flow with successful LLM response — COMUNICAR maps to SUSPICIOUS")
    fun fullFlowWithComunicarMapsToSuspicious() {
        val decisions = listOf(
            HistoricalDecision(
                id = 1L,
                keyword = "espécie",
                description = "Depósito anterior em espécie suspeito",
                analystDecision = Classification.SUSPICIOUS,
                createdAt = Instant.now()
            )
        )

        every { auditRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), "CONTEXTUAL_SCREENING") } returns null
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

        assertEquals("SUSPICIOUS", result.classification)
        assertEquals(0.92, result.confidence)
        assertEquals("Valor alto em espécie sem comprovação de origem", result.reason)
        assertEquals(true, result.requiresAnalystReview)

        verify(exactly = 1) { llmClassifier.classify(any()) }
        verify(exactly = 1) { auditRepository.save(any()) }

        assertEquals(Classification.SUSPICIOUS, auditSlot.captured.finalClassification)
        assertEquals(0.92, auditSlot.captured.finalConfidence)
        assertEquals("COMUNICAR", auditSlot.captured.llmClassification)
    }

    @Test
    @DisplayName("idempotency — existing audit returns cached result without calling LLM")
    fun idempotencyReturnsCachedResult() {
        val existingAudit = ContextualScreeningAudit(
            id = 10L, transactionId = TransactionId("TX-001"),
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

        every { auditRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), "CONTEXTUAL_SCREENING") } returns existingAudit

        val result = service.execute(command)

        assertEquals(
            ContextualScreeningResultDto(
                classification = "SUSPICIOUS",
                confidence = 0.88,
                reason = "Cached reason",
                requiresAnalystReview = true
            ),
            result
        )

        verify(exactly = 0) { llmClassifier.classify(any()) }
        verify(exactly = 0) { historicalDecisionRepository.findByKeyword(any()) }
        verify(exactly = 0) { auditRepository.save(any()) }
    }

    @Test
    @DisplayName("LLM failure fallback — returns UNCERTAIN with confidence 0.00 and requiresReview=true")
    fun llmFailureFallback() {
        every { auditRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), "CONTEXTUAL_SCREENING") } returns null
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

        assertEquals("UNCERTAIN", result.classification)
        assertEquals(0.00, result.confidence)
        assertEquals("Timeout ao conectar com LLM", result.reason)
        assertEquals(true, result.requiresAnalystReview)

        assertEquals(Classification.UNCERTAIN, auditSlot.captured.finalClassification)
        assertEquals(0.00, auditSlot.captured.finalConfidence)
        assertEquals("Timeout ao conectar com LLM", auditSlot.captured.modelResponse)
    }

    @Test
    @DisplayName("historical decisions failure — proceeds with empty list")
    fun historicalDecisionsFailureProceedsWithEmptyList() {
        every { auditRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), "CONTEXTUAL_SCREENING") } returns null
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

        assertEquals("FALSE_POSITIVE", result.classification)
        assertEquals(0.97, result.confidence)
        assertEquals(false, result.requiresAnalystReview)

        verify(exactly = 1) { llmClassifier.classify(any()) }
    }

    @Test
    @DisplayName("mapping NAO_COMUNICAR to FALSE_POSITIVE")
    fun mappingNaoComunicarToFalsePositive() {
        every { auditRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), "CONTEXTUAL_SCREENING") } returns null
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

        assertEquals("FALSE_POSITIVE", result.classification)
        assertEquals(0.98, result.confidence)
        assertEquals(false, result.requiresAnalystReview)

        assertEquals(Classification.FALSE_POSITIVE, auditSlot.captured.finalClassification)
    }

    @Test
    @DisplayName("mapping REVISAO_MANUAL to UNCERTAIN")
    fun mappingRevisaoManualToUncertain() {
        every { auditRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), "CONTEXTUAL_SCREENING") } returns null
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

        assertEquals("UNCERTAIN", result.classification)
        assertEquals(0.50, result.confidence)
        assertEquals(true, result.requiresAnalystReview)

        assertEquals(Classification.UNCERTAIN, auditSlot.captured.finalClassification)
    }

    @Test
    @DisplayName("invalid classification from LLM normalizes to UNCERTAIN")
    fun invalidClassificationFromLlmNormalizesToUncertain() {
        every { auditRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), "CONTEXTUAL_SCREENING") } returns null
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

        assertEquals("UNCERTAIN", result.classification)
        assertEquals(0.75, result.confidence)
        assertEquals(true, result.requiresAnalystReview)

        assertEquals(Classification.UNCERTAIN, auditSlot.captured.finalClassification)
        assertEquals("VALOR_INVALIDO", auditSlot.captured.llmClassification)
    }

    @Test
    @DisplayName("confidence out of range — greater than 1.0 is clamped to 1.0")
    fun confidenceAboveOneIsClamped() {
        every { auditRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), "CONTEXTUAL_SCREENING") } returns null
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

        assertEquals("FALSE_POSITIVE", result.classification)
        assertEquals(1.00, result.confidence)
        assertEquals(false, result.requiresAnalystReview)

        assertEquals(1.00, auditSlot.captured.finalConfidence)
        assertEquals(1.5, auditSlot.captured.llmConfidence)
    }

    @Test
    @DisplayName("confidence out of range — negative value is clamped to 0.0")
    fun negativeConfidenceIsClamped() {
        every { auditRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), "CONTEXTUAL_SCREENING") } returns null
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

        assertEquals("SUSPICIOUS", result.classification)
        assertEquals(0.00, result.confidence)
        assertEquals(true, result.requiresAnalystReview)

        assertEquals(0.00, auditSlot.captured.finalConfidence)
        assertEquals(-0.5, auditSlot.captured.llmConfidence)
    }

    @Test
    @DisplayName("null classification from LLM maps to UNCERTAIN via normalizer")
    fun nullClassificationFromLlmNormalizesToUncertain() {
        every { auditRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), "CONTEXTUAL_SCREENING") } returns null
        every { historicalDecisionRepository.findByKeyword("espécie") } returns emptyList()
        every { llmClassifier.classify(any()) } returns LlmResponse(
            classification = null,
            confidence = 0.70,
            reason = "Alguma razão",
            rawResponse = """{"confianca":0.70}""",
            success = true
        )
        val auditSlot = slot<ContextualScreeningAudit>()
        every { auditRepository.save(capture(auditSlot)) } answers { auditSlot.captured }

        val result = service.execute(command)

        assertEquals("UNCERTAIN", result.classification)
        assertEquals(0.70, result.confidence)
        assertEquals(true, result.requiresAnalystReview)
    }

    @Test
    @DisplayName("reason falls back to 'Sem justificativa disponível' when both reason and errorMessage are null")
    fun reasonFallbackWhenBothNull() {
        every { auditRepository.findByTransactionIdAndRuleId(TransactionId("TX-001"), "CONTEXTUAL_SCREENING") } returns null
        every { historicalDecisionRepository.findByKeyword("espécie") } returns emptyList()
        every { llmClassifier.classify(any()) } returns LlmResponse(
            classification = "COMUNICAR",
            confidence = 0.80,
            reason = null,
            rawResponse = """{"decisao":"COMUNICAR","confianca":0.80}""",
            success = true,
            errorMessage = null
        )
        val auditSlot = slot<ContextualScreeningAudit>()
        every { auditRepository.save(capture(auditSlot)) } answers { auditSlot.captured }

        val result = service.execute(command)

        assertEquals("SUSPICIOUS", result.classification)
        assertEquals("Sem justificativa disponível", result.reason)
    }
}
