package br.com.decision.infrastructure.output.persistence.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "entity_definition")
class EntityDefinitionEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, length = 100)
    val name: String = "",

    @Column(name = "display_name", nullable = false, length = 255)
    val displayName: String = "",

    @Column(name = "source_system", nullable = false, length = 100)
    val sourceSystem: String = "",

    @Type(JsonType::class)
    @Column(name = "fact_names", columnDefinition = "jsonb", nullable = false)
    val factNames: List<String> = emptyList(),

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
