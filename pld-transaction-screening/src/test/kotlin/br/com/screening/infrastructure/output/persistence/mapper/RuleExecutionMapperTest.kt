package br.com.screening.infrastructure.output.persistence.mapper

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.MatchResult
import br.com.screening.domain.model.RuleExecution
import br.com.screening.domain.model.ScreeningResult
import br.com.screening.infrastructure.output.persistence.entity.RuleExecutionEntity
import br.com.shared.domain.valueobject.TransactionId
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("RuleExecutionMapper")
class RuleExecutionMapperTest {

    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
    private val mapper = RuleExecutionMapper(objectMapper)

    @Test
    @DisplayName("toEntity serializes result to JSON")
    fun toEntitySerializesResult() {
        val now = Instant.now()
        val domain = RuleExecution(
            id = 1L,
            transactionId = TransactionId("TX-001"),
            ruleCode = "KEYWORD_SCREENING",
            result = ScreeningResult(
                ruleCode = "KEYWORD_SCREENING",
                matched = true,
                matches = listOf(MatchResult("terrorismo", Category.TERRORISM))
            ),
            createdAt = now
        )

        val entity = mapper.toEntity(domain)

        assertEquals(1L, entity.id)
        assertEquals("TX-001", entity.transactionId)
        assertEquals("KEYWORD_SCREENING", entity.ruleCode)
        assertTrue(entity.result.contains("KEYWORD_SCREENING"))
        assertTrue(entity.result.contains("terrorismo"))
        assertEquals(now, entity.createdAt)
    }

    @Test
    @DisplayName("toDomain deserializes result from JSON")
    fun toDomainDeserializesResult() {
        val now = Instant.now()
        val jsonResult = """{"ruleCode":"KEYWORD_SCREENING","matched":true,"matches":[{"term":"lavagem","category":"AML"}]}"""
        val entity = RuleExecutionEntity(
            id = 2L,
            transactionId = "TX-002",
            ruleCode = "KEYWORD_SCREENING",
            result = jsonResult,
            createdAt = now
        )

        val domain = mapper.toDomain(entity)

        assertEquals(2L, domain.id)
        assertEquals("TX-002", domain.transactionId.value)
        assertEquals("KEYWORD_SCREENING", domain.ruleCode)
        assertTrue(domain.result.matched)
        assertEquals(1, domain.result.matches.size)
        assertEquals("lavagem", domain.result.matches[0].term)
        assertEquals(Category.AML, domain.result.matches[0].category)
        assertEquals(now, domain.createdAt)
    }

    @Test
    @DisplayName("toEntity uses 0 when domain id is null")
    fun toEntityUsesZeroForNullId() {
        val domain = RuleExecution(
            id = null,
            transactionId = TransactionId("TX-003"),
            ruleCode = "KEYWORD_SCREENING",
            result = ScreeningResult(
                ruleCode = "KEYWORD_SCREENING",
                matched = false,
                matches = emptyList()
            ),
            createdAt = Instant.now()
        )

        val entity = mapper.toEntity(domain)

        assertEquals(0L, entity.id)
    }

    @Test
    @DisplayName("round-trip domain -> entity -> domain preserves all data with matches")
    fun roundTripWithMatches() {
        val now = Instant.now()
        val original = RuleExecution(
            id = 10L,
            transactionId = TransactionId("TX-100"),
            ruleCode = "KEYWORD_SCREENING",
            result = ScreeningResult(
                ruleCode = "KEYWORD_SCREENING",
                matched = true,
                matches = listOf(
                    MatchResult("terrorismo", Category.TERRORISM),
                    MatchResult("lavagem", Category.AML)
                )
            ),
            createdAt = now
        )

        val result = mapper.toDomain(mapper.toEntity(original))

        assertEquals(original, result)
    }

    @Test
    @DisplayName("round-trip domain -> entity -> domain preserves data without matches")
    fun roundTripWithoutMatches() {
        val now = Instant.now()
        val original = RuleExecution(
            id = 20L,
            transactionId = TransactionId("TX-200"),
            ruleCode = "KEYWORD_SCREENING",
            result = ScreeningResult(
                ruleCode = "KEYWORD_SCREENING",
                matched = false,
                matches = emptyList()
            ),
            createdAt = now
        )

        val result = mapper.toDomain(mapper.toEntity(original))

        assertEquals(original, result)
    }

    @Test
    @DisplayName("round-trip with all categories")
    fun roundTripAllCategories() {
        val now = Instant.now()
        val allMatches = Category.entries.map { MatchResult("term_${it.name}", it) }
        val original = RuleExecution(
            id = 30L,
            transactionId = TransactionId("TX-300"),
            ruleCode = "KEYWORD_SCREENING",
            result = ScreeningResult(
                ruleCode = "KEYWORD_SCREENING",
                matched = true,
                matches = allMatches
            ),
            createdAt = now
        )

        val result = mapper.toDomain(mapper.toEntity(original))

        assertEquals(original.result.matches.size, result.result.matches.size)
        Category.entries.forEachIndexed { index, category ->
            assertEquals(category, result.result.matches[index].category)
        }
    }
}
