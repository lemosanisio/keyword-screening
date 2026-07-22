package br.com.pld.customeranalysis.dossier

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

enum class DossierStatus { GENERATING, READY, FAILED }

@Entity
@Table(name = "dossier")
class DossierEntity(
    @Id var id: String = "",
    var caseId: String = "",
    var partyId: String = "",
    var version: Int = 1,
    @Enumerated(EnumType.STRING) var status: DossierStatus = DossierStatus.GENERATING,
    var asOf: Instant = Instant.now(),
    var generatedAt: Instant? = null,
    var manifestHash: String? = null,
    var policyVersion: String = "",
    var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "dossier_section")
class DossierSectionEntity(
    @Id var id: String = "",
    var dossierId: String = "",
    var sectionCode: String = "",
    var title: String = "",
    var objectType: String? = null,
    var objectId: String? = null,
    var objectVersion: String? = null,
    var included: Boolean = true,
    var gapReason: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    var content: String = "{}",
    var displayOrder: Int = 0,
)

enum class CoafStatus { DRAFT, PENDING_REVIEW, APPROVED, SUBMITTED, ACKNOWLEDGED, REJECTED }

@Entity
@Table(name = "coaf_communication")
class CoafCommunicationEntity(
    @Id var id: String = "",
    var caseId: String = "",
    var partyId: String = "",
    var dossierId: String? = null,
    @Enumerated(EnumType.STRING) var status: CoafStatus = CoafStatus.DRAFT,
    var version: Int = 1,
    var operationType: String? = null,
    var operationValue: String? = null,
    var operationDate: java.time.LocalDate? = null,
    var narrative: String? = null,
    var legalFramework: String? = null,
    var protocolNumber: String? = null,
    var rejectionReason: String? = null,
    var deadlineDays: Int? = null,
    var deadlineStart: Instant? = null,
    var previousId: String? = null,
    var createdBy: String = "",
    var createdAt: Instant = Instant.now(),
    var submittedAt: Instant? = null,
    var acknowledgedAt: Instant? = null,
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "coaf_communication_event")
class CoafCommunicationEventEntity(
    @Id var id: String = "",
    var communicationId: String = "",
    var eventType: String = "",
    var actorId: String = "",
    var actorRole: String = "",
    var detail: String? = null,
    var occurredAt: Instant = Instant.now(),
)
