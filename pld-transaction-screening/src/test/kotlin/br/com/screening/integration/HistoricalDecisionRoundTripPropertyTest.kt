package br.com.screening.integration

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.HistoricalDecision
import br.com.screening.domain.port.HistoricalDecisionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

/**
 * Property-based integration test for round-trip persistence of analyst feedback.
 *
 * Property 6: Round-trip de persistência de feedback
 * Validates: Requirements 8.1, 8.2, 12.7
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HistoricalDecisionRoundTripPropertyTest {

    @Autowired
    private lateinit var historicalDecisionRepository: HistoricalDecisionRepository

    companion object {
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("screening_roundtrip_test")
            .withUsername("test")
            .withPassword("test")

        init {
            postgres.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("coaf.analyzer.base-url") { "http://localhost:19999" }
        }
    }

    private fun randomAlphanumeric(minLen: Int, maxLen: Int): String {
        val len = Random.nextInt(minLen, maxLen + 1)
        val chars = ('a'..'z') + ('0'..'9')
        return buildString { repeat(len) { append(chars.random()) } }
    }

    @RepeatedTest(50)
    @DisplayName("Property 6: save seguido de findByKeyword recupera a decisão com todos os campos preservados")
    fun saveAndRetrievePreservesAllFields() {
        val keyword = "${randomAlphanumeric(3, 20)}_${UUID.randomUUID().toString().take(8)}"
        val description = randomAlphanumeric(5, 100)
        val analystDecision = Classification.entries[Random.nextInt(Classification.entries.size)]
        val createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        val decision = HistoricalDecision(
            keyword = keyword,
            description = description,
            analystDecision = analystDecision,
            createdAt = createdAt
        )

        val saved = historicalDecisionRepository.save(decision)
        val retrieved = historicalDecisionRepository.findByKeyword(keyword)

        assertTrue(retrieved.isNotEmpty())
        assertTrue(retrieved.all { it.keyword == keyword })

        val matchingDecision = retrieved.find { it.description == description }
        assertNotNull(matchingDecision)
        assertEquals(keyword, matchingDecision!!.keyword)
        assertEquals(description, matchingDecision.description)
        assertEquals(analystDecision, matchingDecision.analystDecision)
        assertNotNull(matchingDecision.id)
        assertNotNull(saved.id)
    }

    @RepeatedTest(20)
    @DisplayName("Property 6 (multiple): save multiple decisions for same keyword, all recoverable via findByKeyword")
    fun multipleDecisionsSameKeywordAllRecoverable() {
        val keyword = "${randomAlphanumeric(3, 20)}_multi_${UUID.randomUUID().toString().take(8)}"
        val count = Random.nextInt(2, 6)
        val baseTime = Instant.now().minus(1, ChronoUnit.HOURS)

        (1..count).forEach { i ->
            val decision = HistoricalDecision(
                keyword = keyword,
                description = "Description $i for $keyword",
                analystDecision = Classification.entries[i % Classification.entries.size],
                createdAt = baseTime.plus(i.toLong(), ChronoUnit.MINUTES)
            )
            historicalDecisionRepository.save(decision)
        }

        val retrieved = historicalDecisionRepository.findByKeyword(keyword)

        assertEquals(count, retrieved.size)
        assertTrue(retrieved.all { it.keyword == keyword })

        val orderedDesc = retrieved.zipWithNext().all { (a, b) -> !a.createdAt.isBefore(b.createdAt) }
        assertTrue(orderedDesc)
    }
}
