package br.com.screening.infrastructure.output.persistence.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant

@Entity
@Table(
    name = "contextual_screening_audit",
    uniqueConstraints = [UniqueConstraint(columnNames = ["transaction_id", "rule_id"])]
)
class ContextualScreeningAuditEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transaction_id", nullable = false, length = 100)
    val transactionId: String = "",

    @Column(name = "rule_id", nullable = false, length = 50)
    val ruleId: String = "",

    @Column(nullable = false, length = 255)
    val keyword: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    val prompt: String = "",

    @Type(JsonType::class)
    @Column(name = "model_response", columnDefinition = "jsonb")
    val modelResponse: String? = null,

    @Column(name = "llm_classification", length = 50)
    val llmClassification: String? = null,

    @Column(name = "llm_confidence", columnDefinition = "numeric")
    val llmConfidence: Double? = null,

    @Column(name = "final_classification", nullable = false, length = 50)
    val finalClassification: String = "",

    @Column(name = "final_confidence", nullable = false, columnDefinition = "numeric")
    val finalConfidence: Double = 0.0,

    @Column(name = "requires_analyst_review", nullable = false)
    val requiresAnalystReview: Boolean = false,

    @Column(nullable = false, columnDefinition = "TEXT")
    val reason: String = "",

    @Column(name = "analyst_decision", length = 50)
    val analystDecision: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
