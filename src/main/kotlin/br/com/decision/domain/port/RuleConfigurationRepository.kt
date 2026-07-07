package br.com.decision.domain.port

import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.model.vo.RuleId
import java.util.UUID

/**
 * Output port para persistência e consulta de configurações de regras.
 * Configurações são editáveis pelo analista e possuem ciclo de vida operacional.
 */
interface RuleConfigurationRepository {
    fun save(config: RuleConfiguration): RuleConfiguration
    fun findById(id: UUID): RuleConfiguration?
    fun findActiveByRuleId(ruleId: RuleId): RuleConfiguration?
    fun findByRuleId(ruleId: RuleId): List<RuleConfiguration>
}
