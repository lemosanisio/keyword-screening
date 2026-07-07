package br.com.screening.integration

import br.com.screening.application.cache.RestrictedTermsCache
import br.com.screening.domain.service.TextNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
class RestrictedTermsCacheIntegrationTest {

    @Autowired
    private lateinit var restrictedTermsCache: RestrictedTermsCache

    @Autowired
    private lateinit var textNormalizer: TextNormalizer

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
    @DisplayName("reload reflects DB changes: new term inserted via JDBC is present in cache after reload")
    fun reloadReflectsDbChanges() {
        val newTerm = "contrabando"
        jdbcTemplate.update(
            "INSERT INTO restricted_term (term, category, active, created_at, updated_at) VALUES (?, ?, TRUE, NOW(), NOW())",
            newTerm, "FRAUD"
        )

        restrictedTermsCache.reload()

        val terms = restrictedTermsCache.getActiveTerms()
        assertTrue(terms.any { it.term == newTerm })
    }

    @Test
    @DisplayName("all terms in cache are normalized after load")
    fun allTermsInCacheAreNormalized() {
        val terms = restrictedTermsCache.getActiveTerms()

        terms.forEach { term ->
            assertEquals(textNormalizer.normalize(term.term), term.term)
        }
    }
}
