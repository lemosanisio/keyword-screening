package br.com.pld.customeranalysis.analysis

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "analysis_cycle")
class AnalysisCycleEntity(
    @Id
    var id: String = "",

    var partyId: String = "",

    @Enumerated(EnumType.STRING)
    var cycleType: AnalysisCycleType = AnalysisCycleType.MANUAL_REVIEW,

    @Enumerated(EnumType.STRING)
    var status: AnalysisCycleStatus = AnalysisCycleStatus.CREATED,

    var policyVersion: String = "",

    var createdAt: Instant = Instant.EPOCH,

    var updatedAt: Instant = Instant.EPOCH,
)
