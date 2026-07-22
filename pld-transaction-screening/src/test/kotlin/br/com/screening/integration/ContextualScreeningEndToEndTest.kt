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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContextualScreeningEndToEndTest {

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

    private fun buildEvaluateRequest(transactionId: String, description: String, matchedKeyword: String): HttpEntity<String> {
        val requestBody = """
            {
                "transactionId": "$transactionId",
                "description": "$description",
                "matchedKeyword": "$matchedKeyword"
            }
        """.trimIndent()
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(requestBody, headers)
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

    @Test
    @DisplayName("POST evaluate with NAO_COMUNICAR response should return 200 with FALSE_POSITIVE classification")
    fun evaluateWithNaoComunicarReturnsFalsePositive() {
        enqueueLlmResponse("NAO_COMUNICAR", 0.97, "Operação compatível com perfil do cliente")

        val entity = buildEvaluateRequest(
            transactionId = "txn-ctx-e2e-001",
            description = "Pagamento de aluguel mensal referente terrorismo residencial",
            matchedKeyword = "terrorismo"
        )

        val response = restTemplate.postForEntity("/v1/rules/contextual-screening/evaluate", entity, Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals("NOT_SUSPICIOUS", response.body!!["classification"])
        assertEquals(0.97, response.body!!["confidence"])
        assertEquals("Operação compatível com perfil do cliente", response.body!!["reason"])
        assertEquals(false, response.body!!["requiresAnalystReview"])
    }

    @Test
    @DisplayName("POST evaluate with COMUNICAR response should return 200 with SUSPICIOUS classification")
    fun evaluateWithComunicarReturnsSuspicious() {
        enqueueLlmResponse("COMUNICAR", 0.92, "Indícios de atividade suspeita")

        val entity = buildEvaluateRequest(
            transactionId = "txn-ctx-e2e-002",
            description = "Transferência internacional para país com lavagem de dinheiro",
            matchedKeyword = "lavagem"
        )

        val response = restTemplate.postForEntity("/v1/rules/contextual-screening/evaluate", entity, Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals("SUSPICIOUS", response.body!!["classification"])
        assertEquals(true, response.body!!["requiresAnalystReview"])
    }

    @Test
    @DisplayName("idempotency: second call with same transactionId returns identical result without invoking LLM again")
    fun idempotencyWithoutInvokingLlmAgain() {
        val transactionId = "txn-ctx-idempotent-001"

        enqueueLlmResponse("NAO_COMUNICAR", 0.98, "Falso positivo identificado")

        val entity = buildEvaluateRequest(
            transactionId = transactionId,
            description = "Compra em loja com nome suspeito de fraude",
            matchedKeyword = "fraude"
        )

        val response1 = restTemplate.postForEntity("/v1/rules/contextual-screening/evaluate", entity, Map::class.java)
        val response2 = restTemplate.postForEntity("/v1/rules/contextual-screening/evaluate", entity, Map::class.java)

        assertEquals(HttpStatus.OK, response1.statusCode)
        assertEquals(HttpStatus.OK, response2.statusCode)
        assertEquals(response1.body, response2.body)
        assertEquals("NOT_SUSPICIOUS", response2.body!!["classification"])
        assertEquals(0.98, response2.body!!["confidence"])
    }

    @Test
    @DisplayName("only 1 contextual_screening_audit record exists after 2 calls with same transactionId")
    fun onlyOneAuditRecordAfterTwoCalls() {
        val transactionId = "txn-ctx-single-record-001"

        enqueueLlmResponse("REVISAO_MANUAL", 0.50, "Análise inconclusiva")

        val entity = buildEvaluateRequest(
            transactionId = transactionId,
            description = "Operação com termos ambíguos de corrupção",
            matchedKeyword = "corrupção"
        )

        restTemplate.postForEntity("/v1/rules/contextual-screening/evaluate", entity, Map::class.java)
        restTemplate.postForEntity("/v1/rules/contextual-screening/evaluate", entity, Map::class.java)

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM contextual_screening_audit WHERE transaction_id = ? AND rule_id = ?",
            Long::class.java, transactionId, "CONTEXTUAL_SCREENING"
        )
        assertEquals(1L, count)
    }

    @Test
    @DisplayName("MockWebServer received only 1 request for idempotent call flow")
    fun mockWebServerReceivedOnlyOneRequest() {
        val transactionId = "txn-ctx-verify-single-llm-call-001"

        enqueueLlmResponse("NAO_COMUNICAR", 0.96, "Transação normal")

        val entity = buildEvaluateRequest(
            transactionId = transactionId,
            description = "Depósito referente a financiamento imobiliário",
            matchedKeyword = "financiamento"
        )

        val requestCountBefore = mockWebServer.requestCount

        restTemplate.postForEntity("/v1/rules/contextual-screening/evaluate", entity, Map::class.java)
        restTemplate.postForEntity("/v1/rules/contextual-screening/evaluate", entity, Map::class.java)

        val requestCountAfter = mockWebServer.requestCount
        assertEquals(1, requestCountAfter - requestCountBefore)
    }
}
