package br.com.decision.domain.model

import br.com.decision.domain.model.enums.ComparisonOperator
import br.com.decision.domain.model.enums.FactType
import br.com.decision.domain.model.enums.RuleContext
import br.com.decision.domain.model.vo.FactName
import java.util.UUID

/**
 * Catálogo de fatos disponíveis para uso em expressions.
 */
data class FactDefinition(
    val id: UUID,
    val name: FactName,
    val displayName: String,
    val entity: String,
    val type: FactType,
    val context: RuleContext,
    val source: String,
    val supportedOperators: List<ComparisonOperator>,
    val enabled: Boolean
)
