package br.com.pld.customeranalysis.integration

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "outbox_event")
class OutboxEventEntity(
    @Id
    var id: String = "",

    var eventType: String = "",

    var eventVersion: Int = 1,

    var aggregateType: String = "",

    var aggregateId: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    var payload: String = "{}",

    var occurredAt: Instant = Instant.EPOCH,

    var publishedAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    var status: IntegrationRecordStatus = IntegrationRecordStatus.PENDING,
)
