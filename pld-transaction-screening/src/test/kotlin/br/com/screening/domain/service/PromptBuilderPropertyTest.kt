package br.com.screening.domain.service

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.HistoricalDecision
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import java.time.Instant
import kotlin.random.Random

/**
 * Testes de propriedade para [PromptBuilder].
 *
 * Property 4 — Completude do prompt
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6
 */
class PromptBuilderPropertyTest {

    private val promptBuilder = PromptBuilder()

    private fun randomNonBlankString(maxLength: Int = 50): String {
        val length = Random.nextInt(1, maxLength + 1)
        return buildString { repeat(length) { append(('a'..'z').random()) } }
    }

    private fun randomHistoricalDecisions(count: Int): List<HistoricalDecision> =
        (1..count).map {
            HistoricalDecision(
                id = it.toLong(),
                keyword = "keyword",
                description = randomNonBlankString(50),
                analystDecision = Classification.entries[Random.nextInt(Classification.entries.size)],
                createdAt = Instant.now()
            )
        }

    @RepeatedTest(200)
    @DisplayName("Property 4: prompt sempre contém a description para qualquer input válido")
    fun promptAlwaysContainsDescription() {
        val description = randomNonBlankString(100)
        val keyword = randomNonBlankString(30)
        val decisions = randomHistoricalDecisions(Random.nextInt(0, 6))

        val prompt = promptBuilder.build(description, keyword, decisions)

        assertTrue(prompt.contains(description)) {
            "Prompt should contain description '$description'"
        }
    }

    @RepeatedTest(200)
    @DisplayName("Property 4: prompt sempre contém a matchedKeyword para qualquer input válido")
    fun promptAlwaysContainsMatchedKeyword() {
        val description = randomNonBlankString(100)
        val keyword = randomNonBlankString(30)
        val decisions = randomHistoricalDecisions(Random.nextInt(0, 6))

        val prompt = promptBuilder.build(description, keyword, decisions)

        assertTrue(prompt.contains(keyword)) {
            "Prompt should contain keyword '$keyword'"
        }
    }

    @RepeatedTest(200)
    @DisplayName("Property 4: prompt sempre contém instruções de classificação com os três valores")
    fun promptAlwaysContainsClassificationInstructions() {
        val description = randomNonBlankString(100)
        val keyword = randomNonBlankString(30)
        val decisions = randomHistoricalDecisions(Random.nextInt(0, 6))

        val prompt = promptBuilder.build(description, keyword, decisions)

        assertTrue(prompt.contains("FALSE_POSITIVE"))
        assertTrue(prompt.contains("SUSPICIOUS"))
        assertTrue(prompt.contains("UNCERTAIN"))
    }

    @RepeatedTest(200)
    @DisplayName("Property 4: prompt contém todas as decisões históricas quando a lista não está vazia")
    fun promptContainsAllHistoricalDecisions() {
        val description = randomNonBlankString(100)
        val keyword = randomNonBlankString(30)
        val decisions = randomHistoricalDecisions(Random.nextInt(1, 6))

        val prompt = promptBuilder.build(description, keyword, decisions)

        decisions.forEach { decision ->
            assertTrue(prompt.contains(decision.description)) {
                "Prompt should contain historical decision description '${decision.description}'"
            }
        }
    }

    @RepeatedTest(200)
    @DisplayName("Property 4: prompt não contém seção de decisões históricas quando lista está vazia")
    fun promptDoesNotContainHistoricalDecisionsSectionWhenEmpty() {
        val description = randomNonBlankString(100)
        val keyword = randomNonBlankString(30)

        val prompt = promptBuilder.build(description, keyword, emptyList())

        assertTrue(!prompt.contains("Decisões Históricas")) {
            "Prompt should not contain historical decisions section when list is empty"
        }
    }
}
