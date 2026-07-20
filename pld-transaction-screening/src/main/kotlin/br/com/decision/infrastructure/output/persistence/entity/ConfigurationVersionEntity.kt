package br.com.decision.infrastructure.output.persistence.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "configuration_version")
class ConfigurationVersionEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "configuration_id", nullable = false)
    val configurationId: UUID = UUID.randomUUID(),

    @Column(name = "version", nullable = false)
    val version: Int = 1,

    @Type(JsonType::class)
    @Column(name = "expressions", columnDefinition = "jsonb", nullable = false)
    val expressions: List<Map<String, Any?>> = emptyList(),

    @Type(JsonType::class)
    @Column(name = "actions", columnDefinition = "jsonb", nullable = false)
    val actions: List<String> = emptyList(),

    @Column(name = "active", nullable = false)
    val active: Boolean = false,

    @Column(name = "created_by", nullable = false, length = 100)
    val createdBy: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
