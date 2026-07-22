package br.com.evaluation.infrastructure

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "screening_quarantine")
class ScreeningQuarantineEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "source_system", nullable = false)
    val sourceSystem: String = "",
    @Column(name = "external_id")
    val externalId: String? = null,
    @Column(name = "reason_code", nullable = false)
    val reasonCode: String = "",
    @Column(name = "reason_detail")
    val reasonDetail: String? = null,
    @Column(name = "purpose", nullable = false)
    val purpose: String = "LIVE",
    @Type(JsonType::class)
    @Column(name = "raw_payload", columnDefinition = "jsonb", nullable = false)
    val rawPayload: Map<String, Any?> = emptyMap(),
    @Column(name = "correlation_id")
    val correlationId: String? = null,
    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant = Instant.now(),
)
