package br.com.pld.customeranalysis.casemanagement

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "pld_case")
class CaseEntity(
    @Id
    var id: String = "",

    var partyId: String = "",

    @Enumerated(EnumType.STRING)
    var origin: CaseOrigin = CaseOrigin.TRANSACTION_ALERT,

    @Enumerated(EnumType.STRING)
    var status: CaseStatus = CaseStatus.OPEN,

    @Enumerated(EnumType.STRING)
    var priority: CasePriority = CasePriority.MEDIUM,

    var reasonCode: String = "",

    var groupingPolicyVersion: String = "",

    var sourceCount: Int = 0,

    var assignedActorId: String? = null,

    var createdAt: Instant = Instant.EPOCH,

    var updatedAt: Instant = Instant.EPOCH,
)
