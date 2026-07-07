package br.com.decision.domain.port

import br.com.decision.domain.model.EntityDefinition

/**
 * Output port para consulta do catálogo de entidades de negócio.
 * EntityDefinitions são mantidas pela engenharia — somente leitura para o domínio.
 */
interface EntityDefinitionRepository {
    fun findByName(name: String): EntityDefinition?
    fun findAll(): List<EntityDefinition>
}
