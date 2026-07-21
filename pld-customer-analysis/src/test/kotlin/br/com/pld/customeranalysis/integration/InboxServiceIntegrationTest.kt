package br.com.pld.customeranalysis.integration

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

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class InboxServiceIntegrationTest {

    @Autowired
    private lateinit var inboxService: InboxService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.execute("truncate table inbox_event restart identity cascade")
    }

    @Test
    fun `executes effect once for duplicated event`() {
        var executions = 0
        val message = InboxMessage(
            consumerName = "customer-analysis-test-consumer",
            eventId = "01J6ZK7Q3W8K0M2N4P6R8T0X1A",
            eventType = "TransactionSignalDetected",
            eventVersion = 1,
            payload = "{\"signalId\":\"sig_01J6ZK7Q3W8K0M2N4P6R8T0X1B\"}",
        )

        val first = inboxService.processOnce(message) { executions += 1 }
        val duplicate = inboxService.processOnce(message) { executions += 1 }

        assertThat(first).isEqualTo(InboxProcessingResult.PROCESSED)
        assertThat(duplicate).isEqualTo(InboxProcessingResult.DUPLICATE)
        assertThat(executions).isEqualTo(1)
        assertThat(inboxStatuses()).containsExactly("PROCESSED")
    }

    private fun inboxStatuses(): List<String> = jdbcTemplate.queryForList(
        "select status from inbox_event order by received_at",
        String::class.java,
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
