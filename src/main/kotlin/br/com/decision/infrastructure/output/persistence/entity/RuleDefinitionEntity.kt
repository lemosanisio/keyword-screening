package br.com.decision.infrastructure.output.persistence.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "rule_definition")
class RuleDefinitionEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "code", nullable = false, length = 50)
    val code: String = "",

    @Column(name = "name", nullable = false, length = 255)
    val name: String = "",

    @Column(name = "description", columnDefinition = "TEXT")
    val description: String = "",

    @Column(name = "context", nullable = false, length = 100)
    val context: String = "",

    @Column(name = "category", nullable = false, length = 100)
    val category: String = "",

    @Type(JsonType::class)
    @Column(name = "supported_facts", columnDefinition = "jsonb", nullable = false)
    val supportedFacts: List<String> = emptyList(),

    @Type(JsonType::class)
    @Column(name = "supported_actions", columnDefinition = "jsonb", nullable = false)
    val supportedActions: List<String> = emptyList(),

    @Column(name = "status", nullable = false, length = 50)
    val status: String = "ACTIVE",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
