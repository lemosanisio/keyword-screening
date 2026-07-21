package br.com.pld.customeranalysis.casemanagement

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "case_source")
class CaseSourceEntity(
    @Id
    var id: String = "",

    var caseId: String = "",

    var sourceSystem: String = "",

    var sourceId: String = "",

    var sourceType: String = "",

    var severity: String = "",

    var reasonCode: String = "",

    var evaluationId: String? = null,

    var transactionId: String? = null,

    var signalType: String? = null,

    var recommendedRoute: String? = null,

    var riskProfileVersion: Int? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    var ruleMatches: String = "[]",

    var groupingPolicyVersion: String = "",

    var correlationId: String = "",

    var causationId: String = "",

    var attachedAt: Instant = Instant.EPOCH,
)
