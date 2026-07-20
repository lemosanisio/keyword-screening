package br.com.alert.infrastructure.output.persistence.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "alert")
class AlertEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String = "",

    @Column(name = "rule_id", nullable = false)
    val ruleId: UUID = UUID.randomUUID(),

    @Column(name = "customer_id", nullable = false, length = 64)
    val customerId: String = "",

    @Type(JsonType::class)
    @Column(name = "facts", columnDefinition = "jsonb", nullable = false)
    val facts: Map<String, Any?> = emptyMap(),

    @Column(name = "configuration_version", nullable = false)
    val configurationVersion: Int = 0,

    @Column(name = "trace_id", nullable = false, length = 64)
    val traceId: String = "",

    @Type(JsonType::class)
    @Column(name = "actions", columnDefinition = "jsonb", nullable = false)
    val actions: List<String> = emptyList(),

    @Type(JsonType::class)
    @Column(name = "explanation", columnDefinition = "jsonb", nullable = false)
    val explanation: Map<String, Any?> = emptyMap(),

    @Column(name = "status", nullable = false, length = 20)
    val status: String = "OPEN",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
