package br.com.screening.infrastructure.output.persistence.mapper

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.HistoricalDecision
import br.com.screening.infrastructure.output.persistence.entity.HistoricalDecisionEntity
import org.springframework.stereotype.Component

@Component
class HistoricalDecisionMapper {

    fun toDomain(entity: HistoricalDecisionEntity): HistoricalDecision =
        HistoricalDecision(
            id = entity.id,
            keyword = entity.keyword,
            description = entity.description,
            analystDecision = Classification.valueOf(entity.analystDecision),
            createdAt = entity.createdAt
        )

    fun toEntity(domain: HistoricalDecision): HistoricalDecisionEntity =
        HistoricalDecisionEntity(
            id = domain.id ?: 0,
            keyword = domain.keyword,
            description = domain.description,
            analystDecision = domain.analystDecision.name,
            createdAt = domain.createdAt
        )
}
