package br.com.screening.infrastructure.output.persistence.mapper

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.MatchResult
import br.com.screening.domain.model.ScreeningResult
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import kotlin.random.Random

/**
 * Property test para round-trip de persistência do ScreeningResult.
 *
 * Property 8 — Validates: Requirements 5.4, 5.6
 */
class RuleExecutionMapperPropertyTest {

    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private fun randomTerm(): String {
        val length = Random.nextInt(1, 21)
        return buildString { repeat(length) { append(('a'..'z').random()) } }
    }

    private fun randomScreeningResult(): ScreeningResult {
        val matched = Random.nextBoolean()
        val matchCount = Random.nextInt(0, 6)
        val matches = (1..matchCount).map {
            MatchResult(
                term = randomTerm(),
                category = Category.entries[Random.nextInt(Category.entries.size)]
            )
        }
        return ScreeningResult(
            ruleCode = "KEYWORD_SCREENING",
            matched = matched,
            matches = matches
        )
    }

    @RepeatedTest(200)
    @DisplayName("deserialize(serialize(result)) == result para qualquer ScreeningResult")
    fun serializationRoundTrip() {
        val original = randomScreeningResult()
        val json = objectMapper.writeValueAsString(original)
        val deserialized = objectMapper.readValue(json, ScreeningResult::class.java)
        assertEquals(original, deserialized)
    }
}
