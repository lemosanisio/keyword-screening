package br.com.decision.infrastructure.output.persistence.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "rule_configuration")
class RuleConfigurationEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "rule_id", nullable = false)
    val ruleId: UUID = UUID.randomUUID(),

    @Type(JsonType::class)
    @Column(name = "expressions", columnDefinition = "jsonb", nullable = false)
    val expressions: List<Map<String, Any?>> = emptyList(),

    @Type(JsonType::class)
    @Column(name = "actions", columnDefinition = "jsonb", nullable = false)
    val actions: List<String> = emptyList(),

    @Column(name = "active", nullable = false)
    val active: Boolean = false,

    @Column(name = "draft", nullable = false)
    val draft: Boolean = true,

    @Column(name = "current_version", nullable = false)
    val currentVersion: Int = 1,

    @Column(name = "created_by", nullable = false, length = 100)
    val createdBy: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),

    @Column(name = "pending_activation", nullable = false)
    val pendingActivation: Boolean = false,

    @Column(name = "activation_requested_by", length = 100)
    val activationRequestedBy: String? = null,

    @Column(name = "activation_approved_by", length = 100)
    val activationApprovedBy: String? = null,

    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuration_id", insertable = false, updatable = false)
    val versions: List<ConfigurationVersionEntity> = emptyList()
)
