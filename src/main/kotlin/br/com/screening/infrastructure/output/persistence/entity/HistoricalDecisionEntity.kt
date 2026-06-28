package br.com.screening.infrastructure.output.persistence.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "historical_decision")
class HistoricalDecisionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val keyword: String = "",

    @Column(columnDefinition = "TEXT", nullable = false)
    val description: String = "",

    @Column(name = "analyst_decision", length = 50, nullable = false)
    val analystDecision: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
