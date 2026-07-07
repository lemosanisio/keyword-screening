package br.com.decision.domain.resolver

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.service.Fact
import br.com.decision.domain.service.FactResolver
import br.com.decision.domain.model.vo.FactName
import br.com.decision.domain.model.vo.FactValue
import java.time.Instant

/**
 * Extrai facts do DetectionEvent sem chamadas externas.
 * Entity: Screening, sourceSystem: Screening.
 *
 * Produz o fact "keywordMatched" (Boolean) a partir do campo matched do DetectionResult.
 */
class ScreeningResolver : FactResolver {

    override val producedFacts: Set<FactName> = setOf(FactName("keywordMatched"))

    override val entity: String = "Screening"

    override fun resolve(event: DetectionEvent): List<Fact> {
        return listOf(
            Fact(
                name = FactName("keywordMatched"),
                value = FactValue.BooleanValue(event.detectionResult.matched),
                entity = entity,
                resolvedAt = Instant.now()
            )
        )
    }
}
