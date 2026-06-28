package br.com.screening.infrastructure.output.persistence.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.Instant

@Entity
@Table(
    name = "rule_execution",
    uniqueConstraints = [UniqueConstraint(columnNames = ["transaction_id", "rule_code"])]
)
class RuleExecutionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "transaction_id")
    val transactionId: String = "",

    @Column(name = "rule_code")
    val ruleCode: String = "",

    @Type(JsonType::class)
    @Column(columnDefinition = "jsonb")
    val result: String = "",   // ScreeningResult serializado como JSON

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)
