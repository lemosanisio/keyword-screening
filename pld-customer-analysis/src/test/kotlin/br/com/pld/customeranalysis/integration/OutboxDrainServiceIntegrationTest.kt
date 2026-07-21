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
@SpringBootTest
class OutboxDrainServiceIntegrationTest {

    @Autowired
    private lateinit var outboxService: OutboxService

    @Autowired
    private lateinit var outboxDrainService: OutboxDrainService

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
    fun `publishes pending events and marks them as processed`() {
        outboxService.append(
            eventType = "PartyCreated",
            aggregateType = "Party",
            aggregateId = "pty_01J6ZK7Q3W8K0M2N4P6R8T0X1A",
            payload = mapOf("partyId" to "pty_01J6ZK7Q3W8K0M2N4P6R8T0X1A"),
        )
        outboxService.append(
            eventType = "AnalysisCycleCreated",
            aggregateType = "AnalysisCycle",
            aggregateId = "acy_01J6ZK7Q3W8K0M2N4P6R8T0X1B",
            payload = mapOf("analysisCycleId" to "acy_01J6ZK7Q3W8K0M2N4P6R8T0X1B"),
        )

        val published = outboxDrainService.publishPending(limit = 10)
        val secondRun = outboxDrainService.publishPending(limit = 10)

        assertThat(published).isEqualTo(2)
        assertThat(secondRun).isZero()
        assertThat(publisher.published.map { it.eventType })
            .containsExactly("PartyCreated", "AnalysisCycleCreated")
        assertThat(outboxStatuses()).containsExactly("PROCESSED", "PROCESSED")
        assertThat(publishedAtCount()).isEqualTo(2)
    }

    private fun outboxStatuses(): List<String> = jdbcTemplate.queryForList(
        "select status from outbox_event order by occurred_at, id",
        String::class.java,
    )

    private fun publishedAtCount(): Int = jdbcTemplate.queryForObject(
        "select count(*) from outbox_event where published_at is not null",
        Int::class.java,
    ) ?: 0

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

class RecordingOutboxPublisher : OutboxPublisher {
    val published = mutableListOf<OutboxMessage>()

    override fun publish(message: OutboxMessage) {
        published += message
    }
}
