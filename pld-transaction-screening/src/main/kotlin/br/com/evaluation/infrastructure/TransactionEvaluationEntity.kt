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
@Table(name = "transaction_evaluation")
class TransactionEvaluationEntity(
    @Id
    @Column(name = "evaluation_id")
    val evaluationId: String = "",
    @Column(name = "decision_execution_id", nullable = false)
    val decisionExecutionId: UUID = UUID.randomUUID(),
    @Column(name = "transaction_id", nullable = false)
    val transactionId: String = "",
    @Column(name = "source_system", nullable = false)
    val sourceSystem: String = "",
    @Column(name = "external_transaction_id", nullable = false)
    val externalTransactionId: String = "",
    @Column(name = "transaction_version", nullable = false)
    val transactionVersion: Int = 1,
    @Column(name = "purpose", nullable = false)
    val purpose: String = "LIVE",
    @Column(name = "evaluation_request_id")
    val evaluationRequestId: String? = null,
    @Column(name = "input_event_id", nullable = false)
    val inputEventId: String = "",
    @Column(name = "input_event_schema_version", nullable = false)
    val inputEventSchemaVersion: Int = 1,
    @Type(JsonType::class)
    @Column(name = "snapshot", columnDefinition = "jsonb", nullable = false)
    val snapshot: Map<String, Any?> = emptyMap(),
    @Column(name = "snapshot_ref", nullable = false)
    val snapshotRef: String = "",
    @Column(name = "snapshot_format_version", nullable = false)
    val snapshotFormatVersion: String = "transaction-snapshot-v1",
    @Column(name = "snapshot_hash", nullable = false)
    val snapshotHash: String = "",
    @Column(name = "ruleset_version", nullable = false)
    val rulesetVersion: String = "",
    @Type(JsonType::class)
    @Column(name = "risk_context", columnDefinition = "jsonb", nullable = false)
    val riskContext: Map<String, Any?> = emptyMap(),
    @Type(JsonType::class)
    @Column(name = "facts", columnDefinition = "jsonb", nullable = false)
    val facts: List<Map<String, Any?>> = emptyList(),
    @Type(JsonType::class)
    @Column(name = "rules_executed", columnDefinition = "jsonb", nullable = false)
    val rulesExecuted: List<Map<String, Any?>> = emptyList(),
    @Type(JsonType::class)
    @Column(name = "rules_triggered", columnDefinition = "jsonb", nullable = false)
    val rulesTriggered: List<Map<String, Any?>> = emptyList(),
    @Column(name = "execution_status", nullable = false)
    val executionStatus: String = "COMPLETED",
    @Column(name = "evaluation_outcome")
    val evaluationOutcome: String? = null,
    @Column(name = "review_required")
    val reviewRequired: Boolean? = null,
    @Column(name = "recommended_route")
    val recommendedRoute: String? = null,
    @Type(JsonType::class)
    @Column(name = "explanation", columnDefinition = "jsonb", nullable = false)
    val explanation: List<Map<String, String>> = emptyList(),
    @Column(name = "party_id")
    val partyId: String? = null,
    @Column(name = "correlation_id", nullable = false)
    val correlationId: String = "",
    @Column(name = "causation_id")
    val causationId: String? = null,
    @Column(name = "evaluated_at", nullable = false)
    val evaluatedAt: Instant = Instant.now(),
    @Column(name = "failure_stage")
    val failureStage: String? = null,
    @Column(name = "failure_code")
    val failureCode: String? = null,
)
