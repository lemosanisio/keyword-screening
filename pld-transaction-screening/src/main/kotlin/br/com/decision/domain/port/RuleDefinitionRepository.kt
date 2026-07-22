package br.com.decision.domain.port

import br.com.decision.domain.model.RuleDefinition
import br.com.decision.domain.model.enums.RuleCategory
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.vo.RuleCode

/**
 * Output port para consulta e persistência de definições de regras.
 */
interface RuleDefinitionRepository {
    fun findByCode(code: RuleCode): RuleDefinition?
    fun findAll(): List<RuleDefinition>
    fun findByContextAndCategory(context: RuleContext?, category: RuleCategory?): List<RuleDefinition>
    fun save(definition: RuleDefinition): RuleDefinition
}
