package br.com.screening.integration

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
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
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Integration test verifying LLM fallback behavior when the coaf-analyzer API
 * returns errors (HTTP 500, invalid JSON, timeout).
 *
 * When the LLM fails, the system must:
 * - Return classification = UNCERTAIN, confidence = 0.00, requiresAnalystReview = true
 * - Persist the audit record with the error information in modelResponse
 *
 * **Validates: Requirements 4.4, 4.5, 7.4, 10.1, 10.4**
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LlmFallbackIntegrationTest(
    @Autowired private val restTemplate: TestRestTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate
) : StringSpec() {

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

    override fun extensions() = listOf(SpringExtension)

    init {
        /**
         * Test 1: MockWebServer returns HTTP 500 → verify response is UNCERTAIN/0.00/requiresReview=true
         * and verify audit persisted with error in modelResponse.
         *
         * **Validates: Requirements 4.4, 10.1, 10.4, 7.4**
         */
        "LLM returns HTTP 500 - should fallback to UNCERTAIN with confidence 0.00 and persist audit with error" {
            val transactionId = "txn-fallback-500-${UUID.randomUUID()}"

            // Enqueue HTTP 500 response
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .addHeader("Content-Type", "application/json")
                    .setBody("""{"error": "Internal Server Error"}""")
            )

            val response = callEvaluateEndpoint(transactionId)

            // Verify fallback response
            response.statusCode shouldBe HttpStatus.OK
            val body = response.body!!
            body["classification"] shouldBe "UNCERTAIN"
            (body["confidence"] as Number).toDouble() shouldBe 0.0
            body["requiresAnalystReview"] shouldBe true

            // Verify audit persisted with error
            val audit = queryAudit(transactionId)
            audit shouldNotBe null
            audit!!["final_classification"] shouldBe "UNCERTAIN"
            (audit["final_confidence"] as Number).toDouble() shouldBe 0.0
            (audit["requires_analyst_review"] as Boolean) shouldBe true
            // modelResponse should contain the error message
            val modelResponse = audit["model_response"] as String?
            modelResponse shouldNotBe null
            modelResponse!! shouldContain "Erro"
        }

        /**
         * Test 2: MockWebServer returns invalid JSON → verify same UNCERTAIN fallback and audit persisted.
         * When the adapter receives unparseable JSON, it returns success=false with rawResponse set to
         * the raw body and errorMessage describing the parse error. The service persists rawResponse
         * (or errorMessage if rawResponse is null) as modelResponse.
         *
         * **Validates: Requirements 4.4, 10.1, 10.4, 7.4**
         */
        "LLM returns invalid JSON - should fallback to UNCERTAIN with confidence 0.00 and persist audit with error" {
            val transactionId = "txn-fallback-invalid-json-${UUID.randomUUID()}"
            val invalidJsonBody = "this is not valid json {{{"

            // Enqueue valid HTTP 200 but with unparseable/invalid JSON response
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(invalidJsonBody)
            )

            val response = callEvaluateEndpoint(transactionId)

            // Verify fallback response
            response.statusCode shouldBe HttpStatus.OK
            val body = response.body!!
            body["classification"] shouldBe "UNCERTAIN"
            (body["confidence"] as Number).toDouble() shouldBe 0.0
            body["requiresAnalystReview"] shouldBe true

            // Verify audit persisted with the raw response or error message
            val audit = queryAudit(transactionId)
            audit shouldNotBe null
            audit!!["final_classification"] shouldBe "UNCERTAIN"
            (audit["final_confidence"] as Number).toDouble() shouldBe 0.0
            (audit["requires_analyst_review"] as Boolean) shouldBe true
            // modelResponse should contain either the raw invalid JSON or the error message
            val modelResponse = audit["model_response"] as String?
            modelResponse shouldNotBe null
        }

        /**
         * Test 3: MockWebServer delays beyond timeout → verify same UNCERTAIN fallback and audit persisted.
         *
         * **Validates: Requirements 4.5, 10.1, 10.4, 7.4**
         */
        "LLM times out - should fallback to UNCERTAIN with confidence 0.00 and persist audit with error" {
            val transactionId = "txn-fallback-timeout-${UUID.randomUUID()}"

            // Enqueue response with delay beyond the configured timeout (2s)
            mockWebServer.enqueue(
                MockResponse()
                    .setBody("""{"decisao": "NAO_COMUNICAR", "confianca": 0.95, "justificativa": "Nada suspeito"}""")
                    .addHeader("Content-Type", "application/json")
                    .setBodyDelay(5, TimeUnit.SECONDS)
            )

            val response = callEvaluateEndpoint(transactionId)

            // Verify fallback response
            response.statusCode shouldBe HttpStatus.OK
            val body = response.body!!
            body["classification"] shouldBe "UNCERTAIN"
            (body["confidence"] as Number).toDouble() shouldBe 0.0
            body["requiresAnalystReview"] shouldBe true

            // Verify audit persisted with error
            val audit = queryAudit(transactionId)
            audit shouldNotBe null
            audit!!["final_classification"] shouldBe "UNCERTAIN"
            (audit["final_confidence"] as Number).toDouble() shouldBe 0.0
            (audit["requires_analyst_review"] as Boolean) shouldBe true
            val modelResponse = audit["model_response"] as String?
            modelResponse shouldNotBe null
            modelResponse!! shouldContain "Erro"
        }
    }

    private fun callEvaluateEndpoint(transactionId: String): org.springframework.http.ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
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
            "/v1/rules/contextual-screening/evaluate",
            entity,
            Map::class.java
        ) as org.springframework.http.ResponseEntity<Map<*, *>>
    }

    private fun queryAudit(transactionId: String): Map<String, Any?>? {
        val results = jdbcTemplate.queryForList(
            """
            SELECT transaction_id, final_classification, final_confidence, 
                   requires_analyst_review, model_response
            FROM contextual_screening_audit 
            WHERE transaction_id = ? AND rule_id = ?
            """.trimIndent(),
            transactionId,
            "CONTEXTUAL_SCREENING"
        )
        return results.firstOrNull()
    }
}
