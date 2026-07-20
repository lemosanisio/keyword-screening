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

/**
 * Integration test for the analyst feedback flow (POST /v1/rules/contextual-screening/decisions).
 *
 * Validates: Requirements 8.1, 8.2, 8.3, 8.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnalystFeedbackIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

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
            MockResponse().setBody(responseJson).addHeader("Content-Type", "application/json")
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

        val response = restTemplate.postForEntity("/v1/rules/contextual-screening/evaluate", entity, Map::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
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

        return restTemplate.postForEntity("/v1/rules/contextual-screening/decisions", entity, Map::class.java)
    }

    @Test
    @DisplayName("POST decisions persists HistoricalDecision and updates analystDecision in audit - Req 8.1, 8.3")
    fun decisionPersistsAndUpdatesAudit() {
        val transactionId = "txn-feedback-001"
        val keyword = "terrorismo"

        createAuditViaEvaluate(transactionId, keyword)
        val response = postDecision(transactionId, "FALSE_POSITIVE")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(transactionId, response.body!!["transactionId"])
        assertEquals("FALSE_POSITIVE", response.body!!["analystDecision"])

        val historicalCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM historical_decision WHERE keyword = ? AND analyst_decision = ?",
            Long::class.java, keyword, "FALSE_POSITIVE"
        )
        assertNotNull(historicalCount)
        assertEquals(1L, historicalCount)

        val analystDecisionInAudit = jdbcTemplate.queryForObject(
            "SELECT analyst_decision FROM contextual_screening_audit WHERE transaction_id = ? AND rule_id = ?",
            String::class.java, transactionId, "CONTEXTUAL_SCREENING"
        )
        assertEquals("FALSE_POSITIVE", analystDecisionInAudit)
    }

    @Test
    @DisplayName("HistoricalDecision is retrievable by keyword after analyst feedback - Req 8.2")
    fun historicalDecisionRetrievableByKeyword() {
        val transactionId = "txn-feedback-002"
        val keyword = "lavagem"

        createAuditViaEvaluate(transactionId, keyword)
        val response = postDecision(transactionId, "SUSPICIOUS")
        assertEquals(HttpStatus.OK, response.statusCode)

        val decisions = jdbcTemplate.queryForList(
            "SELECT keyword, analyst_decision FROM historical_decision WHERE keyword = ? ORDER BY created_at DESC",
            keyword
        )
        assertEquals(1, decisions.size)
        assertEquals(keyword, decisions[0]["keyword"])
        assertEquals("SUSPICIOUS", decisions[0]["analyst_decision"])
    }

    @Test
    @DisplayName("POST decisions with non-existent transactionId returns 404 - Req 8.4")
    fun nonExistentTransactionIdReturns404() {
        val response = postDecision("txn-non-existent-999", "FALSE_POSITIVE")
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
