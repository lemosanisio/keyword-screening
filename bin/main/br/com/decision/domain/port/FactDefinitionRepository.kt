package br.com.decision.domain.port

import br.com.decision.domain.model.FactDefinition
import br.com.decision.domain.model.vo.FactName

/**
 * Output port para consulta do catálogo de fatos disponíveis.
 * FactDefinitions são mantidas pela engenharia — somente leitura para o domínio.
 */
interface FactDefinitionRepository {
    fun findByName(name: FactName): FactDefinition?
    fun findAll(): List<FactDefinition>
    fun findEnabled(): List<FactDefinition>
}
