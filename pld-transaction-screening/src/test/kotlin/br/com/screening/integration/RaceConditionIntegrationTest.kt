package br.com.screening.integration

import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.application.usecase.EvaluateKeywordScreeningResult
import br.com.screening.application.usecase.EvaluateKeywordScreeningUseCase
import br.com.shared.domain.valueobject.CustomerId
import br.com.shared.domain.valueobject.TransactionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RaceConditionIntegrationTest {

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

    @Test
    @DisplayName("concurrent calls with same transactionId produce only 1 rule_execution and identical results")
    fun concurrentCallsProduceOneRecord() {
        val transactionId = "txn-race-condition-001"
        val description = "Operacao de terrorismo financeiro"

        val executor = Executors.newFixedThreadPool(2)
        try {
            val command = EvaluateKeywordScreeningCommand(
                transactionId = TransactionId(transactionId),
                customerId = CustomerId("CUST-RACE-001"),
                description = description
            )

            val future1 = CompletableFuture.supplyAsync({ useCase.execute(command) }, executor)
            val future2 = CompletableFuture.supplyAsync({ useCase.execute(command) }, executor)

            val result1: EvaluateKeywordScreeningResult = future1.get()
            val result2: EvaluateKeywordScreeningResult = future2.get()

            assertEquals(result1.ruleCode, result2.ruleCode)
            assertEquals(result1.matched, result2.matched)
            assertEquals(result1.matches, result2.matches)

            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_execution WHERE transaction_id = ? AND rule_code = ?",
                Long::class.java, transactionId, "KEYWORD_SCREENING"
            )
            assertEquals(1L, count)
        } finally {
            executor.shutdown()
        }
    }
}
