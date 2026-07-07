package br.com.alert.infrastructure.output.persistence.repository

import br.com.alert.domain.model.Alert
import br.com.alert.domain.port.AlertRepository
import br.com.alert.domain.model.vo.AlertId
import br.com.alert.infrastructure.output.persistence.mapper.AlertMapper
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class AlertRepositoryImpl(
    private val jpaRepository: AlertJpaRepository,
    private val mapper: AlertMapper
) : AlertRepository {

    override fun save(alert: Alert): Alert {
        val entity = mapper.toEntity(alert)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun findById(id: AlertId): Alert? =
        jpaRepository.findById(id.value).orElse(null)?.let { mapper.toDomain(it) }

    override fun findByTransactionId(transactionId: TransactionId): List<Alert> =
        jpaRepository.findByTransactionId(transactionId.value).map { mapper.toDomain(it) }

    override fun findByTransactionIdAndRuleId(transactionId: TransactionId, ruleId: RuleId): Alert? =
        jpaRepository.findByTransactionIdAndRuleId(transactionId.value, ruleId.value)?.let { mapper.toDomain(it) }

    override fun findByRuleId(ruleId: RuleId, pageable: Pageable): Page<Alert> =
        jpaRepository.findByRuleId(ruleId.value, pageable).map { mapper.toDomain(it) }
}
