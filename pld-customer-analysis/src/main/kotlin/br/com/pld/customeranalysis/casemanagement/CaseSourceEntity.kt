package br.com.pld.customeranalysis.casemanagement

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
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

    var groupingPolicyVersion: String = "",

    var correlationId: String = "",

    var causationId: String = "",

    var attachedAt: Instant = Instant.EPOCH,
)
