package br.com.pld.customeranalysis.operations

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

@Configuration
class IntegrationMetricsConfiguration(
    meterRegistry: MeterRegistry,
    private val jdbcTemplate: JdbcTemplate,
) {
    init {
        registerStatusGauge(meterRegistry, "pld.outbox.events", "PENDING")
        registerStatusGauge(meterRegistry, "pld.outbox.events", "PROCESSED")
        registerInboxStatusGauge(meterRegistry, "PROCESSED")
        registerInboxStatusGauge(meterRegistry, "FAILED")
    }

    private fun registerStatusGauge(meterRegistry: MeterRegistry, name: String, status: String) {
        Gauge.builder(name) { count("outbox_event", status).toDouble() }
            .tag("status", status)
            .description("Outbox events by status")
            .register(meterRegistry)
    }

    private fun registerInboxStatusGauge(meterRegistry: MeterRegistry, status: String) {
        Gauge.builder("pld.inbox.events") { count("inbox_event", status).toDouble() }
            .tag("status", status)
            .description("Inbox events by status")
            .register(meterRegistry)
    }

    private fun count(tableName: String, status: String): Long = jdbcTemplate.queryForObject(
        "select count(*) from $tableName where status = ?",
        Long::class.java,
        status,
    )
}
