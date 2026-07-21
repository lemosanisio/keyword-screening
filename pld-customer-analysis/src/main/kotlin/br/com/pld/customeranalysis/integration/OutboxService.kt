package br.com.pld.customeranalysis.integration

import br.com.pld.customeranalysis.common.PrefixedUlid
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class OutboxService(
    private val outboxRepository: OutboxEventJpaRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun append(
        eventType: String,
        aggregateType: String,
        aggregateId: String,
        payload: Map<String, Any?>,
    ) {
        val now = Instant.now(clock)
        outboxRepository.save(
            OutboxEventEntity(
                id = PrefixedUlid.next("evt_"),
                eventType = eventType,
                eventVersion = 1,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                payload = objectMapper.writeValueAsString(payload),
                occurredAt = now,
                status = IntegrationRecordStatus.PENDING,
            ),
        )
    }
}
