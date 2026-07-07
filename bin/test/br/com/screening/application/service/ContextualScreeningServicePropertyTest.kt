package br.com.screening.application.service

import br.com.screening.application.usecase.EvaluateContextualScreeningCommand
import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.ContextualScreeningAudit
import br.com.screening.domain.port.ContextualScreeningAuditRepository
import br.com.screening.domain.port.HistoricalDecisionRepository
import br.com.screening.domain.port.LlmClassifierPort
import br.com.screening.domain.port.LlmResponse
import br.com.screening.domain.service.PromptBuilder
import br.com.screening.domain.service.ResponseNormalizer
import br.com.screening.domain.service.RoutingClassifier
import br.com.shared.domain.valueobject.TransactionId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.time.Instant
import kotlin.random.Random

/**
 * Property-based tests para [ContextualScreeningService].
 *
 * Property 7: Idempotência de avaliação — Validates: Requirements 9.1, 9.2, 12.6
 * Property 11: Completude do resultado para entrada válida — Validates: Requirements 5.3, 12.9
 */
class ContextualScreeningServicePropertyTest {

    private val autoCloseThreshold = 0.95

    private fun randomNonBlankString(maxLength: Int = 50): String {
        val length = Random.nextInt(1, maxLength + 1)
        return buildString { repeat(length) { append(('a'..'z').random()) } }
    }

    @RepeatedTest(200)
    @DisplayName("Property 7: para qualquer (transactionId, ruleId), quando audit existe, LLM não é invocado e resultado é idêntico ao cached")
    fun idempotencyWhenAuditExists() {
        val rawTransactionId = randomNonBlankString(50)
        val ruleId = randomNonBlankString(30)
        val keyword = randomNonBlankString(30)
        val classification = Classification.entries[Random.nextInt(Classification.entries.size)]
        val confidence = Random.nextDouble(0.0, 1.0)
        val reason = randomNonBlankString(100)
        val transactionId = TransactionId(rawTransactionId)

        val auditRepository = mockk<ContextualScreeningAuditRepository>()
        val historicalDecisionRepository = mockk<HistoricalDecisionRepository>()
        val llmClassifier = mockk<LlmClassifierPort>()
        val promptBuilder = mockk<PromptBuilder>()
        val routingClassifier = RoutingClassifier()
        val responseNormalizer = ResponseNormalizer()

        val requiresReview = routingClassifier.requiresAnalystReview(classification, confidence, autoCloseThreshold)

        val existingAudit = ContextualScreeningAudit(
            id = 1L,
            transactionId = transactionId,
            ruleId = ruleId,
            keyword = keyword,
            prompt = "some prompt",
            modelResponse = """{"decisao":"COMUNICAR"}""",
            llmClassification = "COMUNICAR",
            llmConfidence = confidence,
            finalClassification = classification,
            finalConfidence = confidence,
            requiresAnalystReview = requiresReview,
            reason = reason,
            createdAt = Instant.now()
        )

        every { auditRepository.findByTransactionIdAndRuleId(transactionId, ruleId) } returns existingAudit

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
            transactionId = transactionId,
            ruleId = ruleId,
            description = "any description",
            matchedKeyword = keyword
        )

        val firstResult = service.execute(command)
        val secondResult = service.execute(command)

        verify(exactly = 0) { llmClassifier.classify(any()) }

        assertEquals(firstResult, secondResult)
        assertEquals(classification.name, firstResult.classification)
        assertEquals(confidence, firstResult.confidence)
        assertEquals(reason, firstResult.reason)
        assertEquals(requiresReview, firstResult.requiresAnalystReview)
    }

    @RepeatedTest(200)
    @DisplayName("Property 11: resultado completo para qualquer entrada válida")
    fun completeResultForValidInput() {
        val rawTransactionId = randomNonBlankString(50)
        val description = randomNonBlankString(140)
        val matchedKeyword = randomNonBlankString(50)
        val decisao = listOf("COMUNICAR", "NAO_COMUNICAR", "REVISAO_MANUAL")[Random.nextInt(3)]
        val confidence = Random.nextDouble(0.0, 1.0)
        val transactionId = TransactionId(rawTransactionId)

        val auditRepository = mockk<ContextualScreeningAuditRepository>()
        val historicalDecisionRepository = mockk<HistoricalDecisionRepository>()
        val llmClassifier = mockk<LlmClassifierPort>()
        val promptBuilder = PromptBuilder()
        val routingClassifier = RoutingClassifier()
        val responseNormalizer = ResponseNormalizer()

        every { auditRepository.findByTransactionIdAndRuleId(any(), any()) } returns null
        every { historicalDecisionRepository.findByKeyword(any()) } returns emptyList()
        every { llmClassifier.classify(any()) } returns LlmResponse(
            classification = decisao,
            confidence = confidence,
            reason = "Justificativa gerada pelo LLM para $decisao",
            rawResponse = """{"decisao":"$decisao","confianca":$confidence,"justificativa":"test"}""",
            success = true
        )
        every { auditRepository.save(any()) } answers { firstArg() }

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
            transactionId = transactionId,
            ruleId = "CONTEXTUAL_SCREENING",
            description = description,
            matchedKeyword = matchedKeyword
        )

        val result = service.execute(command)

        val validClassifications = setOf("FALSE_POSITIVE", "SUSPICIOUS", "UNCERTAIN")
        assertTrue(result.classification in validClassifications)
        assertTrue(result.confidence in 0.0..1.0)
        assertTrue(result.reason.isNotBlank())

        val classificationEnum = Classification.valueOf(result.classification)
        val expectedRequiresReview = routingClassifier.requiresAnalystReview(classificationEnum, result.confidence, autoCloseThreshold)
        assertEquals(expectedRequiresReview, result.requiresAnalystReview)
    }
}
