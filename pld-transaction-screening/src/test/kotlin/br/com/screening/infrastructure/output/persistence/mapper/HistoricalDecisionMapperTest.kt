package br.com.screening.infrastructure.output.persistence.mapper

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.HistoricalDecision
import br.com.screening.infrastructure.output.persistence.entity.HistoricalDecisionEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("HistoricalDecisionMapper")
class HistoricalDecisionMapperTest {

    private val mapper = HistoricalDecisionMapper()

    @Test
    @DisplayName("toDomain maps all entity fields correctly")
    fun toDomainMapsAllFields() {
        val now = Instant.now()
        val entity = HistoricalDecisionEntity(
            id = 1L,
            keyword = "terrorismo",
            description = "pagamento referente a evento cultural",
            analystDecision = "FALSE_POSITIVE",
            createdAt = now
        )

        val domain = mapper.toDomain(entity)

        assertEquals(1L, domain.id)
        assertEquals("terrorismo", domain.keyword)
        assertEquals("pagamento referente a evento cultural", domain.description)
        assertEquals(Classification.FALSE_POSITIVE, domain.analystDecision)
        assertEquals(now, domain.createdAt)
    }

    @Test
    @DisplayName("toEntity maps all domain fields correctly")
    fun toEntityMapsAllFields() {
        val now = Instant.now()
        val domain = HistoricalDecision(
            id = 7L,
            keyword = "lavagem",
            description = "transferência suspeita",
            analystDecision = Classification.SUSPICIOUS,
            createdAt = now
        )

        val entity = mapper.toEntity(domain)

        assertEquals(7L, entity.id)
        assertEquals("lavagem", entity.keyword)
        assertEquals("transferência suspeita", entity.description)
        assertEquals("SUSPICIOUS", entity.analystDecision)
        assertEquals(now, entity.createdAt)
    }

    @Test
    @DisplayName("toEntity uses 0 when domain id is null")
    fun toEntityUsesZeroForNullId() {
        val domain = HistoricalDecision(
            id = null,
            keyword = "fraude",
            description = "compra legítima",
            analystDecision = Classification.FALSE_POSITIVE,
            createdAt = Instant.now()
        )

        val entity = mapper.toEntity(domain)

        assertEquals(0L, entity.id)
    }

    @Test
    @DisplayName("round-trip domain -> entity -> domain preserves all data")
    fun roundTripPreservesData() {
        val now = Instant.now()
        val original = HistoricalDecision(
            id = 99L,
            keyword = "sancao",
            description = "pagamento internacional",
            analystDecision = Classification.SUSPICIOUS,
            createdAt = now
        )

        val result = mapper.toDomain(mapper.toEntity(original))

        assertEquals(original, result)
    }

    @Test
    @DisplayName("maps all Classification values correctly")
    fun mapsAllClassifications() {
        val now = Instant.now()
        Classification.entries.forEach { classification ->
            val domain = HistoricalDecision(
                id = 1L,
                keyword = "term",
                description = "desc",
                analystDecision = classification,
                createdAt = now
            )

            val result = mapper.toDomain(mapper.toEntity(domain))

            assertEquals(classification, result.analystDecision)
        }
    }
}
