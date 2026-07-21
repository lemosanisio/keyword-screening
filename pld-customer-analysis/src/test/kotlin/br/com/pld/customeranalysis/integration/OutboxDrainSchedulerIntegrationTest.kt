package br.com.pld.customeranalysis.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = [
        "pld.integration.outbox-drain.enabled=true",
        "pld.integration.outbox-drain.initial-delay=PT1H",
        "pld.integration.outbox-drain.fixed-delay=PT1H",
        "pld.integration.outbox-drain.limit=1",
    ],
)
class OutboxDrainSchedulerIntegrationTest {

    @Autowired
    private lateinit var outboxService: OutboxService

    @Autowired
    private lateinit var outboxDrainScheduler: OutboxDrainScheduler

    @Autowired
    private lateinit var publisher: RecordingOutboxPublisher

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.execute("truncate table outbox_event restart identity cascade")
        publisher.published.clear()
    }

    @Test
    fun `scheduled run drains using configured limit`() {
        appendEvent("PartyCreated", "pty_01J6ZK7Q3W8K0M2N4P6R8T0X1A")
        appendEvent("PartyCreated", "pty_01J6ZK7Q3W8K0M2N4P6R8T0X1B")

        outboxDrainScheduler.publishPending()

        assertThat(publisher.published).hasSize(1)
        assertThat(processedCount()).isEqualTo(1)
        assertThat(pendingCount()).isEqualTo(1)
    }

    private fun appendEvent(eventType: String, partyId: String) {
        outboxService.append(
            eventType = eventType,
            aggregateType = "Party",
            aggregateId = partyId,
            payload = mapOf("partyId" to partyId),
        )
    }

    private fun processedCount(): Int = countByStatus("PROCESSED")

    private fun pendingCount(): Int = countByStatus("PENDING")

    private fun countByStatus(status: String): Int = jdbcTemplate.queryForObject(
        "select count(*) from outbox_event where status = ?",
        Int::class.java,
        status,
    )

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun recordingOutboxPublisher(): RecordingOutboxPublisher = RecordingOutboxPublisher()
    }

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
