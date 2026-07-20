package br.com.decision.application.usecase

import br.com.decision.domain.model.RuleConfiguration
import java.util.UUID

/**
 * Input port: gerenciamento de configurações de regras.
 * Implementado por RuleConfigurationService.
 */
interface ManageRuleConfigurationUseCase {
    fun create(command: CreateRuleConfigurationCommand): RuleConfiguration
    fun update(id: UUID, command: UpdateRuleConfigurationCommand): RuleConfiguration
    fun activate(id: UUID): RuleConfiguration
    fun deactivate(id: UUID): RuleConfiguration
}
