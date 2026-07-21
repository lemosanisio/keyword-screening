package br.com.pld.customeranalysis.casemanagement

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "suspicion_decision")
class SuspicionDecisionEntity(
    @Id
    var id: String = "",

    var caseId: String = "",

    var partyId: String = "",

    @Enumerated(EnumType.STRING)
    var decision: SuspicionDecisionValue = SuspicionDecisionValue.INCONCLUSIVE,

    var decisionVersion: Int = 1,

    @JdbcTypeCode(SqlTypes.JSON)
    var reasonCodes: String = "[]",

    var narrative: String = "",

    var policyVersion: String = "",

    var decidedByActorId: String = "",

    var decidedByActorRole: String = "",

    var decidedAt: Instant = Instant.EPOCH,

    var correlationId: String = "",

    var previousDecisionId: String? = null,

    @Enumerated(EnumType.STRING)
    var approvalStatus: DecisionApprovalStatus = DecisionApprovalStatus.APPROVED,

    var approvedByActorId: String? = null,

    var approvedByActorRole: String? = null,

    var approvedAt: Instant? = null,
)
