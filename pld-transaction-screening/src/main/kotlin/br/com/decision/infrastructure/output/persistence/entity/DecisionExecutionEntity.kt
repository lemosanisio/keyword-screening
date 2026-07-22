package br.com.decision.infrastructure.output.persistence.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "decision_execution")
class DecisionExecutionEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "transaction_id", nullable = false, length = 100)
    val transactionId: String = "",

    @Column(name = "rule_id", nullable = false)
    val ruleId: UUID = UUID.randomUUID(),

    @Column(name = "configuration_version", nullable = false)
    val configurationVersion: Int = 1,

    @Type(JsonType::class)
    @Column(name = "facts", columnDefinition = "jsonb", nullable = false)
    val facts: Map<String, Any?> = emptyMap(),

    @Type(JsonType::class)
    @Column(name = "fact_results", columnDefinition = "jsonb", nullable = false)
    val factResults: List<Map<String, Any?>> = emptyList(),

    @Column(name = "decision", nullable = false, length = 50)
    val decision: String = "",

    @Type(JsonType::class)
    @Column(name = "actions", columnDefinition = "jsonb", nullable = false)
    val actions: List<String> = emptyList(),

    @Type(JsonType::class)
    @Column(name = "matched_expressions", columnDefinition = "jsonb", nullable = false)
    val matchedExpressions: List<Map<String, Any?>> = emptyList(),

    @Type(JsonType::class)
    @Column(name = "failed_expressions", columnDefinition = "jsonb", nullable = false)
    val failedExpressions: List<Map<String, Any?>> = emptyList(),

    @Type(JsonType::class)
    @Column(name = "explanation", columnDefinition = "jsonb", nullable = false)
    val explanation: Map<String, Any?> = emptyMap(),

    @Column(name = "execution_time_ms", nullable = false)
    val executionTimeMs: Long = 0,

    @Column(name = "trace_id", length = 100)
    val traceId: String? = null,

    @Column(name = "evaluation_id", length = 30)
    val evaluationId: String? = null,

    @Column(name = "party_id", length = 30)
    val partyId: String? = null,

    @Column(name = "correlation_id", length = 128)
    val correlationId: String? = null,

    @Column(name = "causation_id", length = 128)
    val causationId: String? = null,

    @Column(name = "evaluation_status", nullable = false)
    val evaluationStatus: String = "COMPLETED",

    @Column(name = "evaluation_outcome", nullable = false)
    val evaluationOutcome: String = "NO_SIGNAL",

    @Column(name = "review_required", nullable = false)
    val reviewRequired: Boolean = false,

    @Column(name = "recommended_route")
    val recommendedRoute: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
