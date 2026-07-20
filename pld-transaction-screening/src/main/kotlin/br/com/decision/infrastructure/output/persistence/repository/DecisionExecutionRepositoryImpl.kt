package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.domain.model.DecisionExecution
import br.com.decision.domain.model.enums.Decision
import br.com.decision.domain.port.DecisionExecutionRepository
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.infrastructure.output.persistence.mapper.DecisionExecutionMapper
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class DecisionExecutionRepositoryImpl(
    private val jpaRepository: DecisionExecutionJpaRepository,
    private val mapper: DecisionExecutionMapper
) : DecisionExecutionRepository {

    override fun save(execution: DecisionExecution): DecisionExecution {
        val entity = mapper.toEntity(execution)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun findById(id: UUID): DecisionExecution? =
        jpaRepository.findById(id).orElse(null)?.let { mapper.toDomain(it) }

    override fun findByTransactionIdAndRuleId(transactionId: TransactionId, ruleId: RuleId): DecisionExecution? =
        jpaRepository.findByTransactionIdAndRuleId(transactionId.value, ruleId.value)
            ?.let { mapper.toDomain(it) }

    override fun findByTransactionId(transactionId: TransactionId, pageable: Pageable): Page<DecisionExecution> =
        jpaRepository.findByTransactionId(transactionId.value, pageable).map { mapper.toDomain(it) }

    override fun findByRuleId(ruleId: RuleId, pageable: Pageable): Page<DecisionExecution> =
        jpaRepository.findByRuleId(ruleId.value, pageable).map { mapper.toDomain(it) }

    override fun findByDecision(decision: Decision, pageable: Pageable): Page<DecisionExecution> =
        jpaRepository.findByDecision(decision.name, pageable).map { mapper.toDomain(it) }

    override fun findByTraceId(traceId: TraceId): DecisionExecution? =
        jpaRepository.findByTraceId(traceId.value)?.let { mapper.toDomain(it) }
}
