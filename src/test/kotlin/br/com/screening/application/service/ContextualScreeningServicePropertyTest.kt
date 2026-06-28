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
import io.kotest.core.spec.style.StringSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

/**
 * Property-based tests para [ContextualScreeningService].
 *
 * **Property 7: Idempotência de avaliação**
 * **Validates: Requirements 9.1, 9.2, 12.6**
 *
 * **Property 11: Completude do resultado para entrada válida**
 * **Validates: Requirements 5.3, 12.9**
 */
class ContextualScreeningServicePropertyTest : StringSpec({

    val autoCloseThreshold = 0.95

    /**
     * Property 7: Idempotência de avaliação
     *
     * Para qualquer (transactionId, ruleId), quando um audit já existe no repositório,
     * o LLM NÃO é invocado e o resultado retornado é idêntico aos dados do audit cached.
     *
     * **Validates: Requirements 9.1, 9.2, 12.6**
     */
    "Property 7: para qualquer (transactionId, ruleId), quando audit existe, LLM nao e invocado e resultado e identico ao cached" {
        forAll(
            Arb.string(1..50).filter { it.isNotBlank() },
            Arb.string(1..30).filter { it.isNotBlank() },
            Arb.string(1..100).filter { it.isNotBlank() },
            Arb.element(Classification.FALSE_POSITIVE, Classification.SUSPICIOUS, Classification.UNCERTAIN),
            Arb.double(0.0, 1.0),
            Arb.string(1..200).filter { it.isNotBlank() }
        ) { transactionId, ruleId, keyword, classification, confidence, reason ->
            // Setup mocks
            val auditRepository = mockk<ContextualScreeningAuditRepository>()
            val historicalDecisionRepository = mockk<HistoricalDecisionRepository>()
            val llmClassifier = mockk<LlmClassifierPort>()
            val promptBuilder = mockk<PromptBuilder>()
            val routingClassifier = RoutingClassifier()
            val responseNormalizer = ResponseNormalizer()

            val requiresReview = routingClassifier.requiresAnalystReview(
                classification, confidence, autoCloseThreshold
            )

            // Create existing audit that simulates a previously persisted result
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

            // Mock repository to return existing audit (simulating idempotency check)
            every {
                auditRepository.findByTransactionIdAndRuleId(transactionId, ruleId)
            } returns existingAudit

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

            // Execute twice
            val firstResult = service.execute(command)
            val secondResult = service.execute(command)

            // Verify LLM was never called
            verify(exactly = 0) { llmClassifier.classify(any()) }

            // Both results must be identical and match the cached audit data
            firstResult == secondResult &&
                firstResult.classification == classification.name &&
                firstResult.confidence == confidence &&
                firstResult.reason == reason &&
                firstResult.requiresAnalystReview == requiresReview
        }
    }

    "Property 11: resultado completo para qualquer entrada válida — classificação válida, confidence em range, reason não vazio, routing consistente" {
        forAll(
            Arb.string(1..50).filter { it.isNotBlank() },
            Arb.string(1..140).filter { it.isNotBlank() },
            Arb.string(1..50).filter { it.isNotBlank() },
            Arb.element("COMUNICAR", "NAO_COMUNICAR", "REVISAO_MANUAL"),
            Arb.double(0.0, 1.0)
        ) { transactionId, description, matchedKeyword, decisao, confidence ->

            // Mocks
            val auditRepository = mockk<ContextualScreeningAuditRepository>()
            val historicalDecisionRepository = mockk<HistoricalDecisionRepository>()
            val llmClassifier = mockk<LlmClassifierPort>()
            val promptBuilder = PromptBuilder()
            val routingClassifier = RoutingClassifier()
            val responseNormalizer = ResponseNormalizer()

            // No existing audit (no idempotency hit)
            every { auditRepository.findByTransactionIdAndRuleId(any(), any()) } returns null

            // Historical decisions: return empty list
            every { historicalDecisionRepository.findByKeyword(any()) } returns emptyList()

            // LLM returns a valid response with varied decisao and confidence
            every { llmClassifier.classify(any()) } returns LlmResponse(
                classification = decisao,
                confidence = confidence,
                reason = "Justificativa gerada pelo LLM para $decisao",
                rawResponse = """{"decisao":"$decisao","confianca":$confidence,"justificativa":"test"}""",
                success = true
            )

            // Capture the saved audit
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

            // 1. classification is one of the valid values
            val validClassifications = setOf("FALSE_POSITIVE", "SUSPICIOUS", "UNCERTAIN")
            val classificationValid = result.classification in validClassifications

            // 2. confidence is in [0.00, 1.00]
            val confidenceValid = result.confidence in 0.0..1.0

            // 3. reason is not blank
            val reasonValid = result.reason.isNotBlank()

            // 4. requiresAnalystReview is consistent with routing rules
            val classification = Classification.valueOf(result.classification)
            val expectedRequiresReview = routingClassifier.requiresAnalystReview(
                classification, result.confidence, autoCloseThreshold
            )
            val routingConsistent = result.requiresAnalystReview == expectedRequiresReview

            classificationValid && confidenceValid && reasonValid && routingConsistent
        }
    }
})
