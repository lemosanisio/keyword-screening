package br.com.decision.domain.model

import br.com.decision.domain.model.vo.FactName
import java.util.UUID

/**
 * Catálogo de entidades do sistema de decisão.
 * Cada entidade agrupa um conjunto de fatos de uma mesma fonte.
 */
data class EntityDefinition(
    val id: UUID,
    val name: String,
    val displayName: String,
    val sourceSystem: String,
    val factNames: List<FactName>
)
