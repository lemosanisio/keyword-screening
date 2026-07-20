package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.EntityDefinition
import br.com.decision.domain.model.vo.FactName
import br.com.decision.infrastructure.output.persistence.entity.EntityDefinitionEntity
import org.springframework.stereotype.Component

@Component
class EntityDefinitionMapper {

    fun toDomain(entity: EntityDefinitionEntity): EntityDefinition =
        EntityDefinition(
            id = entity.id,
            name = entity.name,
            displayName = entity.displayName,
            sourceSystem = entity.sourceSystem,
            factNames = entity.factNames.map { FactName(it) }
        )

    fun toEntity(domain: EntityDefinition): EntityDefinitionEntity =
        EntityDefinitionEntity(
            id = domain.id,
            name = domain.name,
            displayName = domain.displayName,
            sourceSystem = domain.sourceSystem,
            factNames = domain.factNames.map { it.value }
        )
}
