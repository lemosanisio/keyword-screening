package br.com.pld.customeranalysis.casemanagement

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "case_comment")
class CaseCommentEntity(
    @Id
    var id: String = "",

    var caseId: String = "",

    var partyId: String = "",

    var body: String = "",

    var createdByActorId: String = "",

    var createdByActorRole: String = "",

    var correlationId: String = "",

    var createdAt: Instant = Instant.EPOCH,
)
