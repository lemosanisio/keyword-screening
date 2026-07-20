package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.FactDefinition
import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.FactType
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.vo.FactName
import br.com.decision.infrastructure.output.persistence.entity.FactDefinitionEntity
import org.springframework.stereotype.Component

@Component
class FactDefinitionMapper {

    fun toDomain(entity: FactDefinitionEntity): FactDefinition =
        FactDefinition(
            id = entity.id,
            name = FactName(entity.name),
            displayName = entity.displayName,
            entity = entity.entity,
            type = FactType.valueOf(entity.type),
            context = RuleContext.valueOf(entity.context),
            source = entity.source,
            supportedOperators = entity.supportedOperators.map { ComparisonOperator.valueOf(it) },
            enabled = entity.enabled
        )

    fun toEntity(domain: FactDefinition): FactDefinitionEntity =
        FactDefinitionEntity(
            id = domain.id,
            name = domain.name.value,
            displayName = domain.displayName,
            entity = domain.entity,
            type = domain.type.name,
            context = domain.context.name,
            source = domain.source,
            supportedOperators = domain.supportedOperators.map { it.name },
            enabled = domain.enabled
        )
}
