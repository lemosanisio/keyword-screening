package br.com.screening.integration

import br.com.screening.application.usecase.EvaluateKeywordScreeningCommand
import br.com.screening.application.usecase.EvaluateKeywordScreeningResult
import br.com.screening.application.usecase.EvaluateKeywordScreeningUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
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
class RaceConditionIntegrationTest(
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
        "concurrent calls with same transactionId produce only 1 rule_execution and identical results" {
            val transactionId = "txn-race-condition-001"
            val description = "Operacao de terrorismo financeiro"

            val executor = Executors.newFixedThreadPool(2)
            try {
                val command = EvaluateKeywordScreeningCommand(
                    transactionId = transactionId,
                    description = description
                )

                val future1 = CompletableFuture.supplyAsync({
                    useCase.execute(command)
                }, executor)

                val future2 = CompletableFuture.supplyAsync({
                    useCase.execute(command)
                }, executor)

                val result1: EvaluateKeywordScreeningResult = future1.get()
                val result2: EvaluateKeywordScreeningResult = future2.get()

                // Both threads get the same ScreeningResult
                result1.ruleCode shouldBe result2.ruleCode
                result1.matched shouldBe result2.matched
                result1.matches shouldBe result2.matches

                // Only 1 rule_execution record exists (UNIQUE constraint)
                val count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM rule_execution WHERE transaction_id = ? AND rule_code = ?",
                    Long::class.java,
                    transactionId,
                    "KEYWORD_SCREENING"
                )
                count shouldBe 1L
            } finally {
                executor.shutdown()
            }
        }
    }
}
