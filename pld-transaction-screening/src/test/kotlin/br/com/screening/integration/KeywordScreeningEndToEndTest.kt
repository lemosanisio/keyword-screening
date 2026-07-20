package br.com.screening.integration

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
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class KeywordScreeningEndToEndTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

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
    @DisplayName("POST evaluate with description containing terrorismo should return 200 with matched=true")
    fun evaluateWithTerrorismoReturnsMatched() {
        val requestBody = """
            {
                "transactionId": "txn-e2e-001",
                "description": "Pagamento relacionado a terrorismo internacional"
            }
        """.trimIndent()

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(requestBody, headers)

        val response = restTemplate.postForEntity(
            "/v1/rules/keyword-screening/evaluate", entity, Map::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals("KEYWORD_SCREENING", response.body!!["ruleCode"])
        assertEquals(true, response.body!!["matched"])

        @Suppress("UNCHECKED_CAST")
        val matches = response.body!!["matches"] as List<Map<String, String>>
        assertTrue(matches.any { it["term"] == "terrorismo" })
    }

    @Test
    @DisplayName("idempotency: second call with same transactionId returns identical result")
    fun idempotencyReturnsSameResult() {
        val transactionId = "txn-idempotent-e2e-001"
        val requestBody = """
            {
                "transactionId": "$transactionId",
                "description": "Operacao suspeita de lavagem de dinheiro"
            }
        """.trimIndent()

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(requestBody, headers)

        val response1 = restTemplate.postForEntity(
            "/v1/rules/keyword-screening/evaluate", entity, Map::class.java
        )
        val response2 = restTemplate.postForEntity(
            "/v1/rules/keyword-screening/evaluate", entity, Map::class.java
        )

        assertEquals(HttpStatus.OK, response1.statusCode)
        assertEquals(HttpStatus.OK, response2.statusCode)
        assertEquals(response1.body, response2.body)
    }

    @Test
    @DisplayName("only 1 rule_execution record exists after 2 calls with same transactionId")
    fun onlyOneRecordAfterTwoCalls() {
        val transactionId = "txn-single-record-e2e-001"
        val requestBody = """
            {
                "transactionId": "$transactionId",
                "description": "Transacao envolvendo fraude comprovada"
            }
        """.trimIndent()

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(requestBody, headers)

        restTemplate.postForEntity("/v1/rules/keyword-screening/evaluate", entity, Map::class.java)
        restTemplate.postForEntity("/v1/rules/keyword-screening/evaluate", entity, Map::class.java)

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rule_execution WHERE transaction_id = ? AND rule_code = ?",
            Long::class.java, transactionId, "KEYWORD_SCREENING"
        )
        assertEquals(1L, count)
    }
}
