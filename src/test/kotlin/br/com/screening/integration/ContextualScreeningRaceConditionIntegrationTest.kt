package br.com.screening.integration

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Integration test verifying that concurrent calls with the same transactionId/ruleId
 * produce only one audit record in the database (UNIQUE constraint) and return identical results.
 *
 * The ContextualScreeningAuditRepositoryImpl handles DataIntegrityViolationException by
 * catching the exception and falling back to findByTransactionIdAndRuleId, ensuring that
 * race conditions on concurrent inserts are gracefully handled.
 *
 * **Validates: Requirement 9.3**
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContextualScreeningRaceConditionIntegrationTest(
    @Autowired private val restTemplate: TestRestTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate
) : StringSpec() {

    companion object {
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("screening_race_test")
            .withUsername("test")
            .withPassword("test")

        val mockWebServer = MockWebServer()

        init {
            postgres.start()
            mockWebServer.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("coaf.analyzer.base-url") { mockWebServer.url("/").toString().trimEnd('/') }
            registry.add("coaf.analyzer.timeout-seconds") { "10" }
        }
    }

    override fun extensions() = listOf(SpringExtension)

    init {
        "concurrent calls with same transactionId/ruleId produce only 1 audit record and identical results" {
            val transactionId = "txn-ctx-race-001"
            val ruleId = "CONTEXTUAL_SCREENING"
            val concurrency = 3

            // Enqueue enough MockWebServer responses for all concurrent calls that might reach the LLM.
            // Due to the race condition, only 1 call should actually persist an audit record,
            // but multiple threads may pass the idempotency check before the first insert completes.
            val llmResponseJson = """
                {
                    "decisao": "NAO_COMUNICAR",
                    "justificativa": "Operação compatível com perfil do cliente",
                    "enquadramentoLegal": ["Circular BACEN 3.978/2020"],
                    "fundamentacaoTecnica": "Análise detalhada",
                    "confianca": 0.92,
                    "alertas": [],
                    "timestamp": "2024-01-15T14:30:00.000Z"
                }
            """.trimIndent()

            repeat(concurrency) {
                mockWebServer.enqueue(
                    MockResponse()
                        .setBody(llmResponseJson)
                        .addHeader("Content-Type", "application/json")
                )
            }

            val requestBody = """
                {
                    "transactionId": "$transactionId",
                    "ruleId": "$ruleId",
                    "description": "Transferência de R$ 5.000 para conta corrente do mesmo titular",
                    "matchedKeyword": "terrorismo"
                }
            """.trimIndent()

            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val entity = HttpEntity(requestBody, headers)

            val executor = Executors.newFixedThreadPool(concurrency)
            try {
                val futures = (1..concurrency).map {
                    CompletableFuture.supplyAsync({
                        restTemplate.postForEntity(
                            "/v1/rules/contextual-screening/evaluate",
                            entity,
                            Map::class.java
                        )
                    }, executor)
                }

                val responses = futures.map { it.get() }

                // All responses should be HTTP 200
                responses.forEach { response ->
                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldNotBe null
                }

                // All responses should return the same classification and confidence
                val classifications = responses.map { it.body!!["classification"] }
                val confidences = responses.map { it.body!!["confidence"] }
                val requiresReviews = responses.map { it.body!!["requiresAnalystReview"] }

                classifications.distinct().size shouldBe 1
                confidences.distinct().size shouldBe 1
                requiresReviews.distinct().size shouldBe 1

                // Verify the classification matches expected value
                classifications.first() shouldBe "FALSE_POSITIVE"

                // Verify EXACTLY 1 audit record exists in the database (UNIQUE constraint)
                val count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM contextual_screening_audit WHERE transaction_id = ? AND rule_id = ?",
                    Long::class.java,
                    transactionId,
                    ruleId
                )
                count shouldBe 1L
            } finally {
                executor.shutdown()
            }
        }
    }
}
