package br.com.screening.integration

import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.HistoricalDecision
import br.com.screening.domain.port.HistoricalDecisionRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.forAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Property-based integration test for round-trip persistence of analyst feedback.
 *
 * **Property 6: Round-trip de persistência de feedback**
 * **Validates: Requirements 8.1, 8.2, 12.7**
 *
 * Uses Testcontainers + real PostgreSQL to verify that:
 * - save() followed by findByKeyword() recovers the persisted decision
 * - All fields are preserved through the round-trip (keyword, description, analystDecision)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HistoricalDecisionRoundTripPropertyTest(
    @Autowired private val historicalDecisionRepository: HistoricalDecisionRepository
) : StringSpec() {

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

    override fun extensions() = listOf(SpringExtension)

    private fun arbClassification(): Arb<Classification> =
        Arb.element(Classification.FALSE_POSITIVE, Classification.SUSPICIOUS, Classification.UNCERTAIN)

    private fun arbKeyword(): Arb<String> =
        Arb.string(3..20, Codepoint.alphanumeric()).filter { it.isNotBlank() }

    private fun arbDescription(): Arb<String> =
        Arb.string(5..100, Codepoint.alphanumeric()).filter { it.isNotBlank() }

    init {
        "Property 6: save seguido de findByKeyword recupera a decisão com todos os campos preservados" {
            forAll(
                PropTestConfig(iterations = 50),
                arbKeyword(),
                arbDescription(),
                arbClassification()
            ) { keyword, description, analystDecision ->
                // Generate unique keyword to avoid interference between iterations
                val uniqueKeyword = "${keyword}_${UUID.randomUUID().toString().take(8)}"
                val createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)

                val decision = HistoricalDecision(
                    keyword = uniqueKeyword,
                    description = description,
                    analystDecision = analystDecision,
                    createdAt = createdAt
                )

                // Save
                val saved = historicalDecisionRepository.save(decision)

                // Retrieve by keyword
                val retrieved = historicalDecisionRepository.findByKeyword(uniqueKeyword)

                // Verify round-trip: the saved decision is recoverable
                val found = retrieved.isNotEmpty()
                val matchesKeyword = retrieved.all { it.keyword == uniqueKeyword }

                // Find the decision we just saved (by matching description for uniqueness)
                val matchingDecision = retrieved.find { it.description == description }
                val fieldsPreserved = matchingDecision != null &&
                    matchingDecision.keyword == uniqueKeyword &&
                    matchingDecision.description == description &&
                    matchingDecision.analystDecision == analystDecision &&
                    matchingDecision.id != null

                // Verify saved entity has id assigned
                val idAssigned = saved.id != null

                found && matchesKeyword && fieldsPreserved && idAssigned
            }
        }

        "Property 6 (multiple): save multiple decisions for same keyword, all recoverable via findByKeyword" {
            forAll(
                PropTestConfig(iterations = 20),
                arbKeyword(),
                Arb.int(2..5)
            ) { keyword, count ->
                val uniqueKeyword = "${keyword}_multi_${UUID.randomUUID().toString().take(8)}"
                val baseTime = Instant.now().minus(1, ChronoUnit.HOURS)

                // Save multiple decisions for the same keyword
                val decisions = (1..count).map { i ->
                    val decision = HistoricalDecision(
                        keyword = uniqueKeyword,
                        description = "Description $i for $uniqueKeyword",
                        analystDecision = Classification.entries[i % Classification.entries.size],
                        createdAt = baseTime.plus(i.toLong(), ChronoUnit.MINUTES)
                    )
                    historicalDecisionRepository.save(decision)
                }

                // Retrieve all by keyword
                val retrieved = historicalDecisionRepository.findByKeyword(uniqueKeyword)

                // All saved decisions should be recoverable
                val allFound = retrieved.size == count
                val allMatchKeyword = retrieved.all { it.keyword == uniqueKeyword }

                // Ordered by createdAt DESC
                val orderedDesc = retrieved.zipWithNext().all { (a, b) ->
                    !a.createdAt.isBefore(b.createdAt)
                }

                allFound && allMatchKeyword && orderedDesc
            }
        }
    }
}
