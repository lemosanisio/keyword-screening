package br.com.screening.integration

import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.application.usecase.EvaluateKeywordScreeningUseCase
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TransactionId
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
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import kotlin.random.Random

/**
 * Integration property-based test for idempotency with a real PostgreSQL database (Testcontainers).
 *
 * Property 7: idempotência verificada com banco real.
 * Validates: Requirements 5.1, 5.2, 5.3
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class IdempotencyPropertyIntegrationTest {

    @Autowired
    private lateinit var useCase: EvaluateKeywordScreeningUseCase

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

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

    @RepeatedTest(20)
    @DisplayName("Property 7: idempotência com banco real - duas execuções retornam resultado idêntico e COUNT(rule_execution) = 1")
    fun idempotencyWithRealDatabase() {
        val base = buildString {
            val len = Random.nextInt(1, 31)
            repeat(len) { append(('a'..'z').random()) }
        }
        val transactionId = "txn-${base.hashCode().toUInt()}-${UUID.randomUUID()}"
        val description = buildString {
            val len = Random.nextInt(1, 141)
            repeat(len) { append(('a'..'z').random()) }
        }

        val command = EvaluateKeywordScreeningCommand(
            transactionId = TransactionId(transactionId),
            customerId = CustomerId("CUST-INTEG-001"),
            description = description
        )

        val firstResult = useCase.execute(command)
        val secondResult = useCase.execute(command)

        assertEquals(firstResult.ruleCode, secondResult.ruleCode)
        assertEquals(firstResult.matched, secondResult.matched)
        assertEquals(firstResult.matches, secondResult.matches)

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rule_execution WHERE transaction_id = ? AND rule_code = ?",
            Long::class.java, transactionId, "KEYWORD_SCREENING"
        )
        assertEquals(1L, count)
    }
}
