package br.com.screening.integration

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
class KeywordScreeningEndToEndTest(
    @Autowired private val restTemplate: TestRestTemplate,
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
        "POST evaluate with description containing terrorismo should return 200 with matched=true" {
            val requestBody = """
                {
                    "transactionId": "txn-e2e-001",
                    "description": "Pagamento relacionado a terrorismo internacional"
                }
            """.trimIndent()

            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val entity = HttpEntity(requestBody, headers)

            val response = restTemplate.postForEntity(
                "/v1/rules/keyword-screening/evaluate",
                entity,
                Map::class.java
            )

            response.statusCode shouldBe HttpStatus.OK
            response.body shouldNotBe null
            response.body!!["ruleCode"] shouldBe "KEYWORD_SCREENING"
            response.body!!["matched"] shouldBe true

            @Suppress("UNCHECKED_CAST")
            val matches = response.body!!["matches"] as List<Map<String, String>>
            matches.any { it["term"] == "terrorismo" } shouldBe true
        }

        "idempotency: second call with same transactionId returns identical result" {
            val transactionId = "txn-idempotent-e2e-001"
            val requestBody = """
                {
                    "transactionId": "$transactionId",
                    "description": "Operacao suspeita de lavagem de dinheiro"
                }
            """.trimIndent()

            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val entity = HttpEntity(requestBody, headers)

            val response1 = restTemplate.postForEntity(
                "/v1/rules/keyword-screening/evaluate",
                entity,
                Map::class.java
            )

            val response2 = restTemplate.postForEntity(
                "/v1/rules/keyword-screening/evaluate",
                entity,
                Map::class.java
            )

            response1.statusCode shouldBe HttpStatus.OK
            response2.statusCode shouldBe HttpStatus.OK
            response1.body shouldBe response2.body
        }

        "only 1 rule_execution record exists after 2 calls with same transactionId" {
            val transactionId = "txn-single-record-e2e-001"
            val requestBody = """
                {
                    "transactionId": "$transactionId",
                    "description": "Transacao envolvendo fraude comprovada"
                }
            """.trimIndent()

            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val entity = HttpEntity(requestBody, headers)

            restTemplate.postForEntity(
                "/v1/rules/keyword-screening/evaluate",
                entity,
                Map::class.java
            )

            restTemplate.postForEntity(
                "/v1/rules/keyword-screening/evaluate",
                entity,
                Map::class.java
            )

            val count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_execution WHERE transaction_id = ? AND rule_code = ?",
                Long::class.java,
                transactionId,
                "KEYWORD_SCREENING"
            )

            count shouldBe 1L
        }
    }
}
