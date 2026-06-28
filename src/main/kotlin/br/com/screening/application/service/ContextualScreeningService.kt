package br.com.screening.application.service

import br.com.screening.application.usecase.ContextualScreeningResultDto
import br.com.screening.application.usecase.EvaluateContextualScreeningCommand
import br.com.screening.application.usecase.EvaluateContextualScreeningUseCase
import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.ContextualScreeningAudit
import br.com.screening.domain.model.HistoricalDecision
import br.com.screening.domain.port.ContextualScreeningAuditRepository
import br.com.screening.domain.port.HistoricalDecisionRepository
import br.com.screening.domain.port.LlmClassifierPort
import br.com.screening.domain.service.PromptBuilder
import br.com.screening.domain.service.ResponseNormalizer
import br.com.screening.domain.service.RoutingClassifier
import br.com.screening.infrastructure.configuration.ContextualScreeningProperties
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ContextualScreeningService(
    private val auditRepository: ContextualScreeningAuditRepository,
    private val historicalDecisionRepository: HistoricalDecisionRepository,
    private val llmClassifier: LlmClassifierPort,
    private val promptBuilder: PromptBuilder,
    private val routingClassifier: RoutingClassifier,
    private val responseNormalizer: ResponseNormalizer,
    private val properties: ContextualScreeningProperties
) : EvaluateContextualScreeningUseCase {

    override fun execute(command: EvaluateContextualScreeningCommand): ContextualScreeningResultDto {
        // 1. Idempotência: verifica se já existe auditoria para (transactionId, ruleId)
        val existingAudit = auditRepository.findByTransactionIdAndRuleId(
            command.transactionId, command.ruleId
        )
        if (existingAudit != null) {
            return existingAudit.toResultDto()
        }

        // 2. Recuperar decisões históricas (fallback: lista vazia em caso de erro)
        val decisions = retrieveHistoricalDecisions(command.matchedKeyword)

        // 3. Construir prompt
        val prompt = promptBuilder.build(
            description = command.description,
            matchedKeyword = command.matchedKeyword,
            decisions = decisions
        )

        // 4. Invocar LLM
        val llmResponse = llmClassifier.classify(prompt)

        // 5. Mapear decisão coaf-analyzer → Classification e normalizar
        val classification: Classification
        val confidence: Double
        val reason: String

        if (llmResponse.success) {
            classification = responseNormalizer.normalizeClassification(
                mapDecisaoToClassification(llmResponse.classification)
            )
            confidence = responseNormalizer.normalizeConfidence(llmResponse.confidence)
        } else {
            // Fallback em falha do LLM: UNCERTAIN com confidence 0.00
            classification = responseNormalizer.normalizeClassification(null)
            confidence = responseNormalizer.normalizeConfidence(null)
        }

        reason = llmResponse.reason ?: llmResponse.errorMessage ?: "Sem justificativa disponível"

        // 6. Determinar roteamento
        val requiresReview = routingClassifier.requiresAnalystReview(
            classification, confidence, properties.autoCloseThreshold
        )

        // 7. Persistir auditoria
        val audit = ContextualScreeningAudit(
            transactionId = command.transactionId,
            ruleId = command.ruleId,
            keyword = command.matchedKeyword,
            prompt = prompt,
            modelResponse = llmResponse.rawResponse ?: llmResponse.errorMessage,
            llmClassification = llmResponse.classification,
            llmConfidence = llmResponse.confidence,
            finalClassification = classification,
            finalConfidence = confidence,
            requiresAnalystReview = requiresReview,
            reason = reason,
            createdAt = Instant.now()
        )
        auditRepository.save(audit)

        // 8. Retornar resultado
        return ContextualScreeningResultDto(
            classification = classification.name,
            confidence = confidence,
            reason = reason,
            requiresAnalystReview = requiresReview
        )
    }

    private fun retrieveHistoricalDecisions(keyword: String): List<HistoricalDecision> =
        try {
            historicalDecisionRepository.findByKeyword(keyword)
        } catch (_: Exception) {
            emptyList()
        }

    /**
     * Mapeia decisões do coaf-analyzer para o domínio de classificação contextual.
     * COMUNICAR → SUSPICIOUS
     * NAO_COMUNICAR → FALSE_POSITIVE
     * REVISAO_MANUAL → UNCERTAIN
     * Outros → passa adiante para normalização pelo ResponseNormalizer
     */
    private fun mapDecisaoToClassification(decisao: String?): String? = when (decisao) {
        "COMUNICAR" -> "SUSPICIOUS"
        "NAO_COMUNICAR" -> "FALSE_POSITIVE"
        "REVISAO_MANUAL" -> "UNCERTAIN"
        else -> decisao
    }

    private fun ContextualScreeningAudit.toResultDto() = ContextualScreeningResultDto(
        classification = finalClassification.name,
        confidence = finalConfidence,
        reason = reason,
        requiresAnalystReview = requiresAnalystReview
    )
}
