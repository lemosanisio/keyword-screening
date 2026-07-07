package br.com.screening.integration

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.HistoricalDecision
import br.com.screening.domain.port.HistoricalDecisionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

/**
 * Property-based integration test for historical decisions filtering and ordering.
 *
 * Property 5: Filtragem e ordenação de decisões históricas
 * Validates: Requirements 2.1, 2.2, 2.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HistoricalDecisionFilteringPropertyTest {

    @Autowired
    private lateinit var historicalDecisionRepository: HistoricalDecisionRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("screening_property_test")
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

    @RepeatedTest(30)
    @DisplayName("Property 5: findByKeyword retorna apenas decisões com keyword correspondente e ordenadas por createdAt DESC")
    fun findByKeywordFiltersAndOrders() {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val targetKeyword = "${randomAlphanumeric(3, 20)}_$uniqueSuffix"
        val otherKeyword = "${randomAlphanumeric(3, 20)}_other_$uniqueSuffix"
        val targetCount = Random.nextInt(1, 6)
        val otherCount = Random.nextInt(1, 6)

        val baseTime = Instant.now().minus(1, ChronoUnit.HOURS)

        val targetDecisions = (1..targetCount).map { i ->
            HistoricalDecision(
                keyword = targetKeyword,
                description = "Desc target $i $uniqueSuffix",
                analystDecision = Classification.entries[i % Classification.entries.size],
                createdAt = baseTime.plus(i.toLong(), ChronoUnit.MINUTES)
            )
        }

        val otherDecisions = (1..otherCount).map { i ->
            HistoricalDecision(
                keyword = otherKeyword,
                description = "Desc other $i $uniqueSuffix",
                analystDecision = Classification.entries[i % Classification.entries.size],
                createdAt = baseTime.plus(i.toLong(), ChronoUnit.MINUTES)
            )
        }

        (targetDecisions + otherDecisions).forEach { historicalDecisionRepository.save(it) }

        val results = historicalDecisionRepository.findByKeyword(targetKeyword)

        assertTrue(results.all { it.keyword == targetKeyword })
        assertEquals(targetCount, results.size)

        val orderedDesc = results.zipWithNext().all { (a, b) -> !a.createdAt.isBefore(b.createdAt) }
        assertTrue(orderedDesc)

        val otherResults = historicalDecisionRepository.findByKeyword(otherKeyword)
        assertTrue(otherResults.all { it.keyword == otherKeyword })
    }

    @RepeatedTest(10)
    @DisplayName("Property 5 (edge): findByKeyword retorna lista vazia para keyword inexistente")
    fun findByKeywordReturnsEmptyForNonExistentKeyword() {
        val keyword = "${randomAlphanumeric(3, 20)}_nonexistent_${UUID.randomUUID()}"
        val results = historicalDecisionRepository.findByKeyword(keyword)
        assertTrue(results.isEmpty())
    }
}
