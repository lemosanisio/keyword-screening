package br.com.decision.infrastructure.output.persistence.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "fact_definition")
class FactDefinitionEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, length = 100)
    val name: String = "",

    @Column(name = "display_name", nullable = false, length = 255)
    val displayName: String = "",

    @Column(name = "entity", nullable = false, length = 100)
    val entity: String = "",

    @Column(name = "type", nullable = false, length = 50)
    val type: String = "",

    @Column(name = "context", nullable = false, length = 100)
    val context: String = "",

    @Column(name = "source", nullable = false, length = 100)
    val source: String = "",

    @Type(JsonType::class)
    @Column(name = "supported_operators", columnDefinition = "jsonb", nullable = false)
    val supportedOperators: List<String> = emptyList(),

    @Column(name = "enabled", nullable = false)
    val enabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
