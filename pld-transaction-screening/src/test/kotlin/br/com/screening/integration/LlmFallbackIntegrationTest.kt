package br.com.screening.integration

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Integration test verifying LLM fallback behavior when the coaf-analyzer API
 * returns errors (HTTP 500, invalid JSON, timeout).
 *
 * Validates: Requirements 4.4, 4.5, 7.4, 10.1, 10.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LlmFallbackIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("screening_fallback_test")
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
            registry.add("coaf.analyzer.timeout-seconds") { "2" }
        }
    }

    private fun callEvaluateEndpoint(transactionId: String): org.springframework.http.ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val requestBody = """
            {
                "transactionId": "$transactionId",
                "description": "Transferência de R$ 50.000 para conta no exterior",
                "matchedKeyword": "terrorismo"
            }
        """.trimIndent()
        val entity = HttpEntity(requestBody, headers)

        @Suppress("UNCHECKED_CAST")
        return restTemplate.postForEntity(
            "/v1/rules/contextual-screening/evaluate", entity, Map::class.java
        ) as org.springframework.http.ResponseEntity<Map<*, *>>
    }

    private fun queryAudit(transactionId: String): Map<String, Any?>? {
        val results = jdbcTemplate.queryForList(
            """
            SELECT transaction_id, final_classification, final_confidence, 
                   requires_analyst_review, model_response::text as model_response
            FROM contextual_screening_audit 
            WHERE transaction_id = ? AND rule_id = ?
            """.trimIndent(),
            transactionId, "CONTEXTUAL_SCREENING"
        )
        return results.firstOrNull()
    }

    @Test
    @DisplayName("LLM returns HTTP 500 - should fallback to UNCERTAIN with confidence 0.00 and persist audit with error")
    fun llmHttp500Fallback() {
        val transactionId = "txn-fallback-500-${UUID.randomUUID()}"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"error": "Internal Server Error"}""")
        )

        val response = callEvaluateEndpoint(transactionId)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals("INCONCLUSIVE", body["classification"])
        assertEquals(0.0, (body["confidence"] as Number).toDouble())
        assertEquals(true, body["requiresAnalystReview"])

        val audit = queryAudit(transactionId)
        assertNotNull(audit)
        assertEquals("UNCERTAIN", audit!!["final_classification"])
        assertEquals(0.0, (audit["final_confidence"] as Number).toDouble())
        assertEquals(true, audit["requires_analyst_review"] as Boolean)
        val modelResponse = audit["model_response"] as String?
        assertNotNull(modelResponse)
        assertTrue(modelResponse!!.contains("Erro"))
    }

    @Test
    @DisplayName("LLM returns invalid JSON - should fallback to UNCERTAIN with confidence 0.00 and persist audit with error")
    fun llmInvalidJsonFallback() {
        val transactionId = "txn-fallback-invalid-json-${UUID.randomUUID()}"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("this is not valid json {{{")
        )

        val response = callEvaluateEndpoint(transactionId)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals("INCONCLUSIVE", body["classification"])
        assertEquals(0.0, (body["confidence"] as Number).toDouble())
        assertEquals(true, body["requiresAnalystReview"])

        val audit = queryAudit(transactionId)
        assertNotNull(audit)
        assertEquals("UNCERTAIN", audit!!["final_classification"])
        assertEquals(0.0, (audit["final_confidence"] as Number).toDouble())
        assertEquals(true, audit["requires_analyst_review"] as Boolean)
        assertNotNull(audit["model_response"])
    }

    @Test
    @DisplayName("LLM times out - should fallback to UNCERTAIN with confidence 0.00 and persist audit with error")
    fun llmTimeoutFallback() {
        val transactionId = "txn-fallback-timeout-${UUID.randomUUID()}"

        mockWebServer.enqueue(
            MockResponse()
                .setBody("""{"decisao": "NAO_COMUNICAR", "confianca": 0.95, "justificativa": "Nada suspeito"}""")
                .addHeader("Content-Type", "application/json")
                .setBodyDelay(5, TimeUnit.SECONDS)
        )

        val response = callEvaluateEndpoint(transactionId)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals("INCONCLUSIVE", body["classification"])
        assertEquals(0.0, (body["confidence"] as Number).toDouble())
        assertEquals(true, body["requiresAnalystReview"])

        val audit = queryAudit(transactionId)
        assertNotNull(audit)
        assertEquals("UNCERTAIN", audit!!["final_classification"])
        assertEquals(0.0, (audit["final_confidence"] as Number).toDouble())
        assertEquals(true, audit["requires_analyst_review"] as Boolean)
        val modelResponse = audit["model_response"] as String?
        assertNotNull(modelResponse)
        assertTrue(modelResponse!!.contains("Erro"))
    }
}
