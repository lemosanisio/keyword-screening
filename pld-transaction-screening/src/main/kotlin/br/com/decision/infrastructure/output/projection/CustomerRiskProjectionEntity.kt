package br.com.decision.infrastructure.output.projection

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.time.Instant

@Entity
@Table(name = "customer_risk_projection")
class CustomerRiskProjectionEntity(
    @Id
    @Column(name = "party_id")
    val partyId: String = "",
    @Column(name = "risk_level", nullable = false)
    val riskLevel: String = "LOW",
    @Type(JsonType::class)
    @Column(name = "segments", columnDefinition = "jsonb", nullable = false)
    val segments: List<String> = emptyList(),
    @Type(JsonType::class)
    @Column(name = "transaction_facts", columnDefinition = "jsonb", nullable = false)
    val transactionFacts: Map<String, Any?> = emptyMap(),
    @Column(name = "profile_version", nullable = false)
    val profileVersion: Int = 1,
    @Column(name = "risk_profile_id", nullable = false)
    val riskProfileId: String = "",
    @Column(name = "policy_version", nullable = false)
    val policyVersion: String = "",
    @Column(name = "effective_from", nullable = false)
    val effectiveFrom: Instant = Instant.now(),
    @Column(name = "valid_until", nullable = false)
    val validUntil: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)
