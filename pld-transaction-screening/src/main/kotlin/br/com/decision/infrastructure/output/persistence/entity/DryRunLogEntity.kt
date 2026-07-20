package br.com.decision.infrastructure.output.persistence.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "dry_run_log")
class DryRunLogEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "configuration_id", nullable = false)
    val configurationId: UUID = UUID.randomUUID(),

    @Column(name = "version", nullable = false)
    val version: Int = 1,

    @Type(JsonType::class)
    @Column(name = "facts", columnDefinition = "jsonb", nullable = false)
    val facts: Map<String, Any?> = emptyMap(),

    @Type(JsonType::class)
    @Column(name = "result", columnDefinition = "jsonb", nullable = false)
    val result: Map<String, Any?> = emptyMap(),

    @Column(name = "executed_by", nullable = false, length = 100)
    val executedBy: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
