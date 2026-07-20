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
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import kotlin.random.Random

/**
 * Property-based test para [ContextualScreeningService].
 *
 * Property 8 — Completude do registro de auditoria
 * Validates: Requirements 7.1, 7.3, 7.4, 12.8
 */
class ContextualScreeningServiceAuditPropertyTest {

    private val autoCloseThreshold = 0.95

    private fun randomNonBlankString(maxLength: Int = 50): String {
        val length = Random.nextInt(1, maxLength + 1)
        return buildString { repeat(length) { append(('a'..'z').random()) } }
    }

    private fun randomLlmResponse(): LlmResponse {
        return if (Random.nextBoolean()) {
            val decisoes = listOf("COMUNICAR", "NAO_COMUNICAR", "REVISAO_MANUAL")
            LlmResponse(
                classification = decisoes[Random.nextInt(3)],
                confidence = Random.nextDouble(0.0, 1.0),
                reason = randomNonBlankString(200),
                rawResponse = """{"decisao":"COMUNICAR","confianca":0.9,"justificativa":"test"}""",
                success = true
            )
        } else {
            LlmResponse(
                classification = null,
                confidence = null,
                reason = null,
                rawResponse = null,
                success = false,
                errorMessage = randomNonBlankString(100)
            )
        }
    }

    @RepeatedTest(200)
    @DisplayName("Property 8: campos obrigatórios do audit são não nulos para qualquer comando válido e resposta LLM variada")
    fun auditMandatoryFieldsAreNonNull() {
        val command = EvaluateContextualScreeningCommand(
            transactionId = TransactionId(randomNonBlankString(50)),
            ruleId = "CONTEXTUAL_SCREENING",
            description = randomNonBlankString(140),
            matchedKeyword = randomNonBlankString(50)
        )

        val llmResponse = randomLlmResponse()

        val auditRepository = mockk<ContextualScreeningAuditRepository>()
        val historicalDecisionRepository = mockk<HistoricalDecisionRepository>()
        val llmClassifier = mockk<LlmClassifierPort>()
        val promptBuilder = PromptBuilder()
        val routingClassifier = RoutingClassifier()
        val responseNormalizer = ResponseNormalizer()

        every { auditRepository.findByTransactionIdAndRuleId(command.transactionId, command.ruleId) } returns null
        every { historicalDecisionRepository.findByKeyword(command.matchedKeyword) } returns emptyList()
        every { llmClassifier.classify(any()) } returns llmResponse

        val auditSlot = slot<ContextualScreeningAudit>()
        every { auditRepository.save(capture(auditSlot)) } answers { auditSlot.captured }

        val service = ContextualScreeningService(
            auditRepository = auditRepository,
            historicalDecisionRepository = historicalDecisionRepository,
            llmClassifier = llmClassifier,
            promptBuilder = promptBuilder,
            routingClassifier = routingClassifier,
            responseNormalizer = responseNormalizer,
            properties = br.com.screening.infrastructure.configuration.ContextualScreeningProperties(autoCloseThreshold = autoCloseThreshold)
        )

        service.execute(command)

        val audit = auditSlot.captured

        assertTrue(audit.transactionId.value.isNotBlank())
        assertTrue(audit.ruleId.isNotBlank())
        assertTrue(audit.keyword.isNotBlank())
        assertTrue(audit.prompt.isNotBlank())
        assertNotNull(audit.finalClassification)
        assertTrue(Classification.entries.contains(audit.finalClassification))
        assertNotNull(audit.createdAt)
        assertTrue(audit.reason.isNotBlank())
    }
}
