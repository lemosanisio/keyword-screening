package br.com.alert.infrastructure.output.persistence.mapper

import br.com.alert.domain.model.Alert
import br.com.alert.domain.model.enums.AlertStatus
import br.com.alert.domain.model.vo.AlertId
import br.com.alert.infrastructure.output.persistence.entity.AlertEntity
import br.com.decision.domain.model.vo.RuleId
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TraceId
import br.com.shared.domain.valueobject.TransactionId
import org.springframework.stereotype.Component

@Component
class AlertMapper {

    fun toDomain(entity: AlertEntity): Alert =
        Alert(
            id = AlertId(entity.id),
            transactionId = TransactionId(entity.transactionId),
            ruleId = RuleId(entity.ruleId),
            customerId = CustomerId(entity.customerId),
            facts = entity.facts,
            configurationVersion = entity.configurationVersion,
            traceId = entity.traceId.takeIf { it.isNotBlank() }?.let { TraceId(it) },
            actions = entity.actions,
            explanation = entity.explanation,
            status = AlertStatus.valueOf(entity.status),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    fun toEntity(domain: Alert): AlertEntity =
        AlertEntity(
            id = domain.id.value,
            transactionId = domain.transactionId.value,
            ruleId = domain.ruleId.value,
            customerId = domain.customerId.value,
            facts = domain.facts ?: emptyMap(),
            configurationVersion = domain.configurationVersion ?: 0,
            traceId = domain.traceId?.value ?: "",
            actions = domain.actions ?: emptyList(),
            explanation = domain.explanation ?: emptyMap(),
            status = domain.status.name,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
}
