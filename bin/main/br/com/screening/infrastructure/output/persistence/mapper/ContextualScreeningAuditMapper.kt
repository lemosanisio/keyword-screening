package br.com.screening.infrastructure.output.persistence.mapper

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.ContextualScreeningAudit
import br.com.screening.infrastructure.output.persistence.entity.ContextualScreeningAuditEntity
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.stereotype.Component

@Component
class ContextualScreeningAuditMapper {

    fun toDomain(entity: ContextualScreeningAuditEntity): ContextualScreeningAudit =
        ContextualScreeningAudit(
            id = entity.id,
            transactionId = TransactionId(entity.transactionId),
            ruleId = entity.ruleId,
            keyword = entity.keyword,
            prompt = entity.prompt,
            modelResponse = entity.modelResponse,
            llmClassification = entity.llmClassification,
            llmConfidence = entity.llmConfidence,
            finalClassification = Classification.valueOf(entity.finalClassification),
            finalConfidence = entity.finalConfidence,
            requiresAnalystReview = entity.requiresAnalystReview,
            reason = entity.reason,
            analystDecision = entity.analystDecision?.let { Classification.valueOf(it) },
            createdAt = entity.createdAt
        )

    fun toEntity(domain: ContextualScreeningAudit): ContextualScreeningAuditEntity =
        ContextualScreeningAuditEntity(
            id = domain.id ?: 0,
            transactionId = domain.transactionId.value,
            ruleId = domain.ruleId,
            keyword = domain.keyword,
            prompt = domain.prompt,
            modelResponse = domain.modelResponse,
            llmClassification = domain.llmClassification,
            llmConfidence = domain.llmConfidence,
            finalClassification = domain.finalClassification.name,
            finalConfidence = domain.finalConfidence,
            requiresAnalystReview = domain.requiresAnalystReview,
            reason = domain.reason,
            analystDecision = domain.analystDecision?.name,
            createdAt = domain.createdAt
        )
}
