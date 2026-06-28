package br.com.screening.integration

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.HistoricalDecision
import br.com.screening.domain.port.HistoricalDecisionRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.forAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Property-based integration test for historical decisions filtering and ordering.
 *
 * **Property 5: Filtragem e ordenação de decisões históricas**
 * **Validates: Requirements 2.1, 2.2, 2.4**
 *
 * Uses Testcontainers + real PostgreSQL to verify that:
 * - findByKeyword returns only decisions matching the given keyword
 * - Results are ordered by createdAt DESC (most recent first)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HistoricalDecisionFilteringPropertyTest(
    @Autowired private val historicalDecisionRepository: HistoricalDecisionRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate
) : StringSpec() {

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

    override fun extensions() = listOf(SpringExtension)

    private fun arbClassification(): Arb<Classification> =
        Arb.element(Classification.FALSE_POSITIVE, Classification.SUSPICIOUS, Classification.UNCERTAIN)

    private fun arbKeyword(): Arb<String> =
        Arb.string(3..20, Codepoint.alphanumeric()).filter { it.isNotBlank() }

    private fun arbDescription(): Arb<String> =
        Arb.string(5..100, Codepoint.alphanumeric()).filter { it.isNotBlank() }

    init {
        "Property 5: findByKeyword retorna apenas decisões com keyword correspondente e ordenadas por createdAt DESC" {
            forAll(
                PropTestConfig(iterations = 30),
                arbKeyword(),
                arbKeyword(),
                Arb.int(1..5),
                Arb.int(1..5)
            ) { targetKeyword, otherKeyword, targetCount, otherCount ->
                // Ensure keywords are different
                val safeOtherKeyword = if (targetKeyword == otherKeyword) "${otherKeyword}x" else otherKeyword
                val uniqueSuffix = UUID.randomUUID().toString().take(8)
                val uniqueTargetKeyword = "${targetKeyword}_$uniqueSuffix"
                val uniqueOtherKeyword = "${safeOtherKeyword}_$uniqueSuffix"

                // Insert decisions for target keyword with varying timestamps
                val baseTime = Instant.now().minus(1, ChronoUnit.HOURS)
                val targetDecisions = (1..targetCount).map { i ->
                    HistoricalDecision(
                        keyword = uniqueTargetKeyword,
                        description = "Desc target $i $uniqueSuffix",
                        analystDecision = Classification.entries[i % Classification.entries.size],
                        createdAt = baseTime.plus(i.toLong(), ChronoUnit.MINUTES)
                    )
                }

                // Insert decisions for other keyword (should NOT appear in results)
                val otherDecisions = (1..otherCount).map { i ->
                    HistoricalDecision(
                        keyword = uniqueOtherKeyword,
                        description = "Desc other $i $uniqueSuffix",
                        analystDecision = Classification.entries[i % Classification.entries.size],
                        createdAt = baseTime.plus(i.toLong(), ChronoUnit.MINUTES)
                    )
                }

                // Save all decisions
                (targetDecisions + otherDecisions).forEach { historicalDecisionRepository.save(it) }

                // Query by target keyword
                val results = historicalDecisionRepository.findByKeyword(uniqueTargetKeyword)

                // Verify: only target keyword decisions returned
                val allMatchKeyword = results.all { it.keyword == uniqueTargetKeyword }
                val correctCount = results.size == targetCount

                // Verify: ordered by createdAt DESC (most recent first)
                val orderedDesc = results.zipWithNext().all { (a, b) ->
                    !a.createdAt.isBefore(b.createdAt)
                }

                // Verify: other keyword returns its own decisions, not target's
                val otherResults = historicalDecisionRepository.findByKeyword(uniqueOtherKeyword)
                val otherAllMatchKeyword = otherResults.all { it.keyword == uniqueOtherKeyword }

                allMatchKeyword && correctCount && orderedDesc && otherAllMatchKeyword
            }
        }

        "Property 5 (edge): findByKeyword retorna lista vazia para keyword inexistente" {
            forAll(
                PropTestConfig(iterations = 10),
                arbKeyword()
            ) { keyword ->
                val uniqueKeyword = "${keyword}_nonexistent_${UUID.randomUUID()}"
                val results = historicalDecisionRepository.findByKeyword(uniqueKeyword)
                results.isEmpty()
            }
        }
    }
}
