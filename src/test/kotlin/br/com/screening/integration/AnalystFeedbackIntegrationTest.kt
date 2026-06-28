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

/**
 * Integration test for the analyst feedback flow (POST /v1/rules/contextual-screening/decisions).
 *
 * Validates: Requirements 8.1, 8.2, 8.3, 8.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnalystFeedbackIntegrationTest(
    @Autowired private val restTemplate: TestRestTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate
) : StringSpec() {

    companion object {
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("keyword_screening_test")
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
        }
    }

    override fun extensions() = listOf(SpringExtension)

    private fun enqueueLlmResponse(decisao: String, confianca: Double, justificativa: String) {
        val responseJson = """
            {
                "decisao": "$decisao",
                "justificativa": "$justificativa",
                "enquadramentoLegal": ["Art. 10, Circular BACEN 3.978/2020"],
                "fundamentacaoTecnica": "Análise técnica detalhada",
                "confianca": $confianca,
                "alertas": [],
                "timestamp": "2024-01-15T14:30:00.000Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json")
        )
    }

    private fun createAuditViaEvaluate(transactionId: String, keyword: String) {
        enqueueLlmResponse("COMUNICAR", 0.85, "Indícios de atividade suspeita")

        val requestBody = """
            {
                "transactionId": "$transactionId",
                "description": "Transferência suspeita envolvendo $keyword",
                "matchedKeyword": "$keyword"
            }
        """.trimIndent()

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(requestBody, headers)

        val response = restTemplate.postForEntity(
            "/v1/rules/contextual-screening/evaluate",
            entity,
            Map::class.java
        )
        response.statusCode shouldBe HttpStatus.OK
    }

    private fun postDecision(transactionId: String, analystDecision: String): org.springframework.http.ResponseEntity<Map<*, *>> {
        val requestBody = """
            {
                "transactionId": "$transactionId",
                "analystDecision": "$analystDecision"
            }
        """.trimIndent()

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(requestBody, headers)

        return restTemplate.postForEntity(
            "/v1/rules/contextual-screening/decisions",
            entity,
            Map::class.java
        )
    }

    init {
        "POST decisions persists HistoricalDecision and updates analystDecision in audit - Req 8.1, 8.3" {
            val transactionId = "txn-feedback-001"
            val keyword = "terrorismo"

            // Step 1: Create audit via /evaluate
            createAuditViaEvaluate(transactionId, keyword)

            // Step 2: Submit analyst decision
            val response = postDecision(transactionId, "FALSE_POSITIVE")

            // Step 3: Verify HTTP 200
            response.statusCode shouldBe HttpStatus.OK
            response.body shouldNotBe null
            response.body!!["transactionId"] shouldBe transactionId
            response.body!!["analystDecision"] shouldBe "FALSE_POSITIVE"

            // Step 4: Verify HistoricalDecision was created in DB
            val historicalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM historical_decision WHERE keyword = ? AND analyst_decision = ?",
                Long::class.java,
                keyword,
                "FALSE_POSITIVE"
            )
            historicalCount shouldNotBe null
            historicalCount!! shouldBe 1L

            // Step 5: Verify audit.analystDecision was updated
            val analystDecisionInAudit = jdbcTemplate.queryForObject(
                "SELECT analyst_decision FROM contextual_screening_audit WHERE transaction_id = ? AND rule_id = ?",
                String::class.java,
                transactionId,
                "CONTEXTUAL_SCREENING"
            )
            analystDecisionInAudit shouldBe "FALSE_POSITIVE"
        }

        "HistoricalDecision is retrievable by keyword after analyst feedback - Req 8.2" {
            val transactionId = "txn-feedback-002"
            val keyword = "lavagem"

            // Step 1: Create audit via /evaluate
            createAuditViaEvaluate(transactionId, keyword)

            // Step 2: Submit analyst decision
            val response = postDecision(transactionId, "SUSPICIOUS")
            response.statusCode shouldBe HttpStatus.OK

            // Step 3: Verify HistoricalDecision is retrievable by keyword
            val decisions = jdbcTemplate.queryForList(
                "SELECT keyword, analyst_decision FROM historical_decision WHERE keyword = ? ORDER BY created_at DESC",
                keyword
            )
            decisions.size shouldBe 1
            decisions[0]["keyword"] shouldBe keyword
            decisions[0]["analyst_decision"] shouldBe "SUSPICIOUS"
        }

        "POST decisions with non-existent transactionId returns 404 - Req 8.4" {
            val nonExistentTransactionId = "txn-non-existent-999"

            val response = postDecision(nonExistentTransactionId, "FALSE_POSITIVE")

            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }
    }
}
