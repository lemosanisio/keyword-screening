package br.com.integration

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

enum class OutboxStatus { PENDING, PUBLISHED }

@Entity
@Table(name = "integration_outbox")
class IntegrationOutboxEntity(
    @Id
    var eventId: String = "",
    var eventType: String = "",
    var eventVersion: Int = 1,
    var aggregateType: String = "",
    var aggregateId: String = "",
    var logicalId: String = "",
    @JdbcTypeCode(SqlTypes.JSON)
    var envelope: String = "{}",
    var occurredAt: Instant = Instant.EPOCH,
    var publishedAt: Instant? = null,
    @Enumerated(EnumType.STRING)
    var status: OutboxStatus = OutboxStatus.PENDING,
    var attemptCount: Int = 0,
    var nextAttemptAt: Instant = Instant.EPOCH,
    var lastError: String? = null,
)
