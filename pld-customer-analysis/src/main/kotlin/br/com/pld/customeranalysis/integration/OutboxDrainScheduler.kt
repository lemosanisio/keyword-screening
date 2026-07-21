package br.com.pld.customeranalysis.integration

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "pld.integration.outbox-drain", name = ["enabled"], havingValue = "true")
class OutboxDrainScheduler(
    private val outboxDrainService: OutboxDrainService,
    private val properties: OutboxDrainProperties,
) {
    @Scheduled(
        initialDelayString = "\${pld.integration.outbox-drain.initial-delay}",
        fixedDelayString = "\${pld.integration.outbox-drain.fixed-delay}",
    )
    fun publishPending() {
        outboxDrainService.publishPending(properties.limit)
    }
}
