package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.Action
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.enums.RuleStatus
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.RuleCode
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.infrastructure.output.persistence.entity.RuleDefinitionEntity
import org.springframework.stereotype.Component

@Component
class RuleDefinitionMapper {

    fun toDomain(entity: RuleDefinitionEntity): RuleDefinition =
        RuleDefinition(
            id = RuleId(entity.id),
            code = RuleCode(entity.code),
            name = entity.name,
            description = entity.description,
            context = RuleContext.valueOf(entity.context),
            category = RuleCategory.valueOf(entity.category),
            supportedFacts = entity.supportedFacts.map { FactName(it) },
            supportedActions = entity.supportedActions.map { Action.valueOf(it) },
            status = RuleStatus.valueOf(entity.status),
            createdAt = entity.createdAt
        )

    fun toEntity(domain: RuleDefinition): RuleDefinitionEntity =
        RuleDefinitionEntity(
            id = domain.id.value,
            code = domain.code.value,
            name = domain.name,
            description = domain.description,
            context = domain.context.name,
            category = domain.category.name,
            supportedFacts = domain.supportedFacts.map { it.value },
            supportedActions = domain.supportedActions.map { it.name },
            status = domain.status.name,
            createdAt = domain.createdAt
        )
}
