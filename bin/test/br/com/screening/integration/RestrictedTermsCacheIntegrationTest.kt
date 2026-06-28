package br.com.screening.integration

import br.com.screening.application.cache.RestrictedTermsCache
import br.com.screening.domain.service.TextNormalizer
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RestrictedTermsCacheIntegrationTest(
    @Autowired private val restrictedTermsCache: RestrictedTermsCache,
    @Autowired private val textNormalizer: TextNormalizer,
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
        "reload reflects DB changes: new term inserted via JDBC is present in cache after reload" {
            val newTerm = "contrabando"
            jdbcTemplate.update(
                "INSERT INTO restricted_term (term, category, active, created_at, updated_at) VALUES (?, ?, TRUE, NOW(), NOW())",
                newTerm,
                "FRAUD"
            )

            restrictedTermsCache.reload()

            val terms = restrictedTermsCache.getActiveTerms()
            terms.any { it.term == newTerm } shouldBe true
        }

        "all terms in cache are normalized after load" {
            val terms = restrictedTermsCache.getActiveTerms()

            terms.forEach { term ->
                term.term shouldBe textNormalizer.normalize(term.term)
            }
        }
    }
}
