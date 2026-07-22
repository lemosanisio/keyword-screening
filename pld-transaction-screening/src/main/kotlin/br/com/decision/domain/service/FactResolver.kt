package br.com.decision.domain.service

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import java.time.Instant

/**
 * Interface base para Fact Resolvers.
 * Cada implementação conhece apenas seu domínio e é responsável
 * por produzir um conjunto específico de Facts a partir de um DetectionEvent.
 */
interface FactResolver {
    /** Quais FactNames este resolver é capaz de produzir */
    val producedFacts: Set<FactName>

    /** Entity à qual os facts pertencem */
    val entity: String

    /** Origem auditável usada no resultado do fato. */
    val sourceSystem: String get() = entity

    /** Resolve os facts a partir do contexto do evento */
    fun resolve(event: DetectionEvent): List<Fact>
}

/**
 * Fato resolvido por um FactResolver.
 */
data class Fact(
    val name: FactName,
    val value: FactValue,
    val entity: String,
    val resolvedAt: Instant
)
