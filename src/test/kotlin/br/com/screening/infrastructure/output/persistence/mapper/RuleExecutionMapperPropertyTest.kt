package br.com.screening.infrastructure.output.persistence.mapper

import br.com.screening.domain.model.Category
import br.com.screening.domain.model.MatchResult
import br.com.screening.domain.model.ScreeningResult
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll

/**
 * Property test para round-trip de persistência do ScreeningResult.
 *
 * Property 8 — Validates: Requirements 5.4, 5.6
 */
class RuleExecutionMapperPropertyTest : StringSpec({

    val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    val arbCategory = Arb.element(Category.entries)

    val arbMatchResult = Arb.bind(
        Arb.string(1..20).map { it.lowercase().filter { c -> c in 'a'..'z' } }.filter { it.isNotEmpty() },
        arbCategory
    ) { term, cat -> MatchResult(term, cat) }

    val arbScreeningResult = Arb.bind(
        Arb.constant("KEYWORD_SCREENING"),
        Arb.boolean(),
        Arb.list(arbMatchResult, 0..5)
    ) { ruleCode, matched, matches -> ScreeningResult(ruleCode, matched, matches) }

    // Feature: mf09-keyword-screening, Property 8: Round-trip de persistência do ScreeningResult
    "deserialize(serialize(result)) == result para qualquer ScreeningResult" {
        forAll(arbScreeningResult) { original ->
            val json = objectMapper.writeValueAsString(original)
            val deserialized = objectMapper.readValue(json, ScreeningResult::class.java)
            deserialized == original
        }
    }
})
