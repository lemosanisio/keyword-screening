package br.com.screening.infrastructure.output.persistence

import br.com.screening.domain.model.RuleExecution
import br.com.screening.domain.repository.RuleExecutionRepository
import br.com.screening.infrastructure.output.persistence.mapper.RuleExecutionMapper
import br.com.screening.infrastructure.output.persistence.repository.RuleExecutionJpaRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

@Repository
class RuleExecutionRepositoryImpl(
    private val jpaRepository: RuleExecutionJpaRepository,
    private val mapper: RuleExecutionMapper
) : RuleExecutionRepository {
    override fun findByTransactionIdAndRuleCode(transactionId: String, ruleCode: String): RuleExecution? =
        jpaRepository.findByTransactionIdAndRuleCode(transactionId, ruleCode)?.let { mapper.toDomain(it) }

    override fun save(ruleExecution: RuleExecution): RuleExecution {
        return try {
            val entity = mapper.toEntity(ruleExecution)
            val saved = jpaRepository.save(entity)
            mapper.toDomain(saved)
        } catch (ex: DataIntegrityViolationException) {
            // Race condition: another thread persisted first
            val existing = jpaRepository.findByTransactionIdAndRuleCode(
                ruleExecution.transactionId, ruleExecution.ruleCode
            )
            existing?.let { mapper.toDomain(it) } ?: throw ex
        }
    }
}
