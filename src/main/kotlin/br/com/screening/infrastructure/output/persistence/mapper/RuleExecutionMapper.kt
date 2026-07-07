package br.com.screening.infrastructure.output.persistence.mapper

import br.com.screening.domain.model.RuleExecution
import br.com.screening.domain.model.ScreeningResult
import br.com.screening.infrastructure.output.persistence.entity.RuleExecutionEntity
import br.com.shared.domain.valueobject.TransactionId
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class RuleExecutionMapper(private val objectMapper: ObjectMapper) {

    fun toEntity(domain: RuleExecution): RuleExecutionEntity =
        RuleExecutionEntity(
            id = domain.id ?: 0,
            transactionId = domain.transactionId.value,
            ruleCode = domain.ruleCode,
            result = objectMapper.writeValueAsString(domain.result),
            createdAt = domain.createdAt
        )

    fun toDomain(entity: RuleExecutionEntity): RuleExecution =
        RuleExecution(
            id = entity.id,
            transactionId = TransactionId(entity.transactionId),
            ruleCode = entity.ruleCode,
            result = objectMapper.readValue(entity.result, ScreeningResult::class.java),
            createdAt = entity.createdAt
        )
}
