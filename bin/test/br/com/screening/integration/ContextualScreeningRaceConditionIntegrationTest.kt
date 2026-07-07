package br.com.screening.integration

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
 * Validates: Requirement 9.3
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContextualScreeningRaceConditionIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

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

    @Test
    @DisplayName("concurrent calls with same transactionId/ruleId produce only 1 audit record and identical results")
    fun concurrentCallsProduceOneAuditRecord() {
        val transactionId = "txn-ctx-race-001"
        val ruleId = "CONTEXTUAL_SCREENING"
        val concurrency = 3

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
                MockResponse().setBody(llmResponseJson).addHeader("Content-Type", "application/json")
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

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(requestBody, headers)

        val executor = Executors.newFixedThreadPool(concurrency)
        try {
            val futures = (1..concurrency).map {
                CompletableFuture.supplyAsync({
                    restTemplate.postForEntity("/v1/rules/contextual-screening/evaluate", entity, Map::class.java)
                }, executor)
            }

            val responses = futures.map { it.get() }

            responses.forEach { response ->
                assertEquals(HttpStatus.OK, response.statusCode)
                assertNotNull(response.body)
            }

            val classifications = responses.map { it.body!!["classification"] }
            val confidences = responses.map { it.body!!["confidence"] }
            val requiresReviews = responses.map { it.body!!["requiresAnalystReview"] }

            assertEquals(1, classifications.distinct().size)
            assertEquals(1, confidences.distinct().size)
            assertEquals(1, requiresReviews.distinct().size)
            assertEquals("FALSE_POSITIVE", classifications.first())

            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contextual_screening_audit WHERE transaction_id = ? AND rule_id = ?",
                Long::class.java, transactionId, ruleId
            )
            assertEquals(1L, count)
        } finally {
            executor.shutdown()
        }
    }
}
