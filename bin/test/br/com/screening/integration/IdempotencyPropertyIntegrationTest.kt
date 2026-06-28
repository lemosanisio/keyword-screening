package br.com.screening.integration

import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.application.usecase.EvaluateKeywordScreeningUseCase
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Integration property-based test for idempotency with a real PostgreSQL database (Testcontainers).
 *
 * Property 7: idempotência verificada com banco real — duas execuções com o mesmo transactionId
 * retornam resultados idênticos e COUNT(rule_execution) = 1.
 *
 * Validates: Requirements 5.1, 5.2, 5.3
 */
// Feature: mf09-keyword-screening, Property 7: Idempotência de avaliação (integration)
@OptIn(ExperimentalKotest::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class IdempotencyPropertyIntegrationTest(
    @Autowired private val useCase: EvaluateKeywordScreeningUseCase,
    @Autowired private val jdbcTemplate: JdbcTemplate
) : StringSpec() {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("keyword_screening_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    override fun extensions() = listOf(SpringExtension)

    init {
        /**
         * Property 7 (integration): for any valid transactionId and description,
         * executing the use case twice with the same parameters returns identical results
         * and only 1 rule_execution record exists in the database.
         *
         * Each iteration uses a unique transactionId (UUID suffix) to avoid conflicts
         * between property test runs.
         *
         * **Validates: Requirements 5.1, 5.2, 5.3**
         */
        "Property 7: idempotencia com banco real - duas execucoes retornam resultado identico e COUNT(rule_execution) = 1" {
            val arbTransactionId = Arb.string(1..30)
                .filter { it.isNotBlank() }
                .map { base -> "txn-${base.hashCode().toUInt()}-${UUID.randomUUID()}" }

            val arbDescription = Arb.string(1..140)
                .filter { it.isNotBlank() }

            forAll(PropTestConfig(iterations = 20), arbTransactionId, arbDescription) { transactionId, description ->
                val command = EvaluateKeywordScreeningCommand(
                    transactionId = transactionId,
                    description = description
                )

                // First execution
                val firstResult = useCase.execute(command)

                // Second execution with same transactionId/description
                val secondResult = useCase.execute(command)

                // Both results must be identical
                val resultsMatch = firstResult.ruleCode == secondResult.ruleCode &&
                    firstResult.matched == secondResult.matched &&
                    firstResult.matches == secondResult.matches

                // Only 1 rule_execution record should exist for this transactionId
                val count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM rule_execution WHERE transaction_id = ? AND rule_code = ?",
                    Long::class.java,
                    transactionId,
                    "KEYWORD_SCREENING"
                )

                resultsMatch && count == 1L
            }
        }
    }
}
