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
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

/**
 * Property-based test para [ContextualScreeningService].
 *
 * Property 8 — Completude do registro de auditoria
 *
 * Validates: Requirements 7.1, 7.3, 7.4, 12.8
 */
class ContextualScreeningServiceAuditPropertyTest : StringSpec({

    /**
     * Para qualquer EvaluateContextualScreeningCommand válido, com mock LLM variado
     * (sucesso e falha), o registro de auditoria persistido deve ter todos os campos
     * obrigatórios não nulos/não vazios.
     *
     * **Validates: Requirements 7.1, 7.3, 7.4, 12.8**
     */
    "Property 8: campos obrigatórios do audit são não nulos para qualquer comando válido e resposta LLM variada" {
        // Custom generator for EvaluateContextualScreeningCommand with non-blank fields
        val arbNonBlankString = Arb.string(1..50).filter { it.isNotBlank() }
        val arbDescription = Arb.string(1..140).filter { it.isNotBlank() }

        val arbCommand: Arb<EvaluateContextualScreeningCommand> = arbitrary {
            EvaluateContextualScreeningCommand(
                transactionId = arbNonBlankString.bind(),
                ruleId = "CONTEXTUAL_SCREENING",
                description = arbDescription.bind(),
                matchedKeyword = arbNonBlankString.bind()
            )
        }

        // Generator for varied LLM responses (success and failure)
        val arbLlmResponse: Arb<LlmResponse> = arbitrary {
            val success = Arb.boolean().bind()
            if (success) {
                LlmResponse(
                    classification = Arb.element("COMUNICAR", "NAO_COMUNICAR", "REVISAO_MANUAL").bind(),
                    confidence = Arb.double(0.0, 1.0).bind(),
                    reason = Arb.string(1..200).filter { it.isNotBlank() }.bind(),
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
                    errorMessage = Arb.string(1..100).filter { it.isNotBlank() }.bind()
                )
            }
        }

        forAll(arbCommand, arbLlmResponse) { command, llmResponse ->
            // Setup mocks
            val auditRepository = mockk<ContextualScreeningAuditRepository>()
            val historicalDecisionRepository = mockk<HistoricalDecisionRepository>()
            val llmClassifier = mockk<LlmClassifierPort>()
            val promptBuilder = PromptBuilder()
            val routingClassifier = RoutingClassifier()
            val responseNormalizer = ResponseNormalizer()
            val autoCloseThreshold = 0.95

            // No existing audit (not idempotent call)
            every {
                auditRepository.findByTransactionIdAndRuleId(command.transactionId, command.ruleId)
            } returns null

            // Return empty historical decisions
            every {
                historicalDecisionRepository.findByKeyword(command.matchedKeyword)
            } returns emptyList()

            // Mock LLM to return our arbitrary response
            every { llmClassifier.classify(any()) } returns llmResponse

            // Capture the audit passed to save()
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

            // Execute
            service.execute(command)

            // Verify mandatory fields of the captured audit are non-null and non-blank
            val audit = auditSlot.captured

            audit.transactionId.shouldNotBeBlank()
            audit.ruleId.shouldNotBeBlank()
            audit.keyword.shouldNotBeBlank()
            audit.prompt.shouldNotBeBlank()
            audit.finalClassification shouldNotBe null
            Classification.entries.contains(audit.finalClassification)
            audit.createdAt shouldNotBe null
            audit.reason.shouldNotBeBlank()

            true
        }
    }
})
