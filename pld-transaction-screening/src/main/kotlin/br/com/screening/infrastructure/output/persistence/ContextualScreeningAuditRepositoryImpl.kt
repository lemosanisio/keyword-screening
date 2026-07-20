package br.com.screening.infrastructure.output.persistence

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.ContextualScreeningAudit
import br.com.screening.domain.port.ContextualScreeningAuditRepository
import br.com.screening.infrastructure.output.persistence.mapper.ContextualScreeningAuditMapper
import br.com.screening.infrastructure.output.persistence.repository.ContextualScreeningAuditJpaRepository
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

@Repository
class ContextualScreeningAuditRepositoryImpl(
    private val jpaRepository: ContextualScreeningAuditJpaRepository,
    private val mapper: ContextualScreeningAuditMapper
) : ContextualScreeningAuditRepository {

    override fun findByTransactionIdAndRuleId(transactionId: TransactionId, ruleId: String): ContextualScreeningAudit? =
        jpaRepository.findByTransactionIdAndRuleId(transactionId.value, ruleId)?.let(mapper::toDomain)

    override fun save(audit: ContextualScreeningAudit): ContextualScreeningAudit =
        try {
            val entity = mapper.toEntity(audit)
            mapper.toDomain(jpaRepository.save(entity))
        } catch (e: DataIntegrityViolationException) {
            // Race condition: outro thread já persistiu — retorna o existente
            findByTransactionIdAndRuleId(audit.transactionId, audit.ruleId) ?: throw e
        }

    override fun updateAnalystDecision(transactionId: TransactionId, ruleId: String, decision: Classification) {
        jpaRepository.updateAnalystDecision(transactionId.value, ruleId, decision.name)
    }
}
