package br.com.pld.customeranalysis.transactionprojection

import br.com.pld.customeranalysis.integration.InboxProcessingResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.nio.file.Path

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class TransactionEvaluationConsumersIntegrationTest {
    @Autowired
    private lateinit var evaluationConsumer: TransactionEvaluationConsumer

    @Autowired
    private lateinit var manualReviewConsumer: ManualReviewConsumer

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.execute(
            "truncate table manual_review_request, transaction_signal_projection, transaction_evaluation_projection, inbox_event restart identity cascade",
        )
    }

    @Test
    fun `projects evaluation once by inbox and business identity`() {
        val event = fixture("TransactionEvaluationCompletedV2")

        assertThat(evaluationConsumer.consume(event)).isEqualTo(InboxProcessingResult.PROCESSED)
        assertThat(evaluationConsumer.consume(event)).isEqualTo(InboxProcessingResult.DUPLICATE)

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from transaction_evaluation_projection",
            Long::class.java,
        )).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject(
            "select snapshot_hash from transaction_evaluation_projection",
            String::class.java,
        )).matches("^[a-f0-9]{64}$")
    }

    @Test
    fun `projects manual review in legacy mode without creating case`() {
        val event = fixture("ManualReviewRequestedV2")

        assertThat(manualReviewConsumer.consume(event)).isEqualTo(InboxProcessingResult.PROCESSED)
        assertThat(manualReviewConsumer.consume(event)).isEqualTo(InboxProcessingResult.DUPLICATE)

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from manual_review_request",
            Long::class.java,
        )).isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject(
            "select trigger_mode from manual_review_request",
            String::class.java,
        )).isEqualTo("LEGACY")
        assertThat(jdbcTemplate.queryForObject("select count(*) from pld_case", Long::class.java)).isZero()
    }

    private fun fixture(name: String): String = Files.readString(
        Path.of(System.getProperty("user.dir"))
            .resolveSibling("pld-platform-docs/schemas/v1/fixtures/$name.json"),
    )

    companion object {
        @Container
        private val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
