package br.com.pld.customeranalysis.evidence

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "evidence_collection")
class EvidenceCollectionEntity(
    @Id
    var id: String = "",
    var caseId: String = "",
    var partyId: String = "",
    var analysisCycleId: String = "",
    @Enumerated(EnumType.STRING)
    var scenario: EvidenceScenario = EvidenceScenario.CLEAR,
    var policyVersion: String = "",
    var revision: Int = 1,
    var createdAt: Instant = Instant.EPOCH,
    var updatedAt: Instant = Instant.EPOCH,
)

@Entity
@Table(name = "analysis_requirement")
class AnalysisRequirementEntity(
    @Id
    var id: String = "",
    var evidenceCollectionId: String = "",
    var code: String = "",
    var title: String = "",
    var category: String = "",
    var mandatory: Boolean = true,
    @Enumerated(EnumType.STRING)
    var outcome: RequirementOutcome = RequirementOutcome.PENDING,
    var outcomeReason: String = "",
    var displayOrder: Int = 0,
    var evaluatedAt: Instant = Instant.EPOCH,
)

@Entity
@Table(name = "source_execution")
class SourceExecutionEntity(
    @Id
    var id: String = "",
    var evidenceCollectionId: String = "",
    var requirementId: String = "",
    var sourceCode: String = "",
    var sourceName: String = "",
    var attempt: Int = 1,
    @Enumerated(EnumType.STRING)
    var status: SourceExecutionStatus = SourceExecutionStatus.SUCCESS_NO_RESULTS,
    var adapterVersion: String = "simulated-v1",
    var startedAt: Instant = Instant.EPOCH,
    var completedAt: Instant = Instant.EPOCH,
    var validUntil: Instant? = null,
    var summary: String = "",
    var errorCode: String? = null,
    var correlationId: String = "",
)

@Entity
@Table(name = "evidence_record")
class EvidenceRecordEntity(
    @Id
    var id: String = "",
    var sourceExecutionId: String = "",
    var partyId: String = "",
    var analysisCycleId: String = "",
    var evidenceType: String = "",
    var title: String = "",
    var summary: String = "",
    var observedAt: Instant = Instant.EPOCH,
    var validUntil: Instant? = null,
    var referenceKey: String = "",
    var integrityHash: String = "",
    @Enumerated(EnumType.STRING)
    var classification: EvidenceClassification = EvidenceClassification.CONFIDENTIAL,
    @JdbcTypeCode(SqlTypes.JSON)
    var structuredData: String = "{}",
)

@Entity
@Table(name = "fact_version")
class FactVersionEntity(
    @Id
    var id: String = "",
    var evidenceId: String = "",
    var partyId: String = "",
    var analysisCycleId: String = "",
    var factCode: String = "",
    var label: String = "",
    @JdbcTypeCode(SqlTypes.JSON)
    var value: String = "null",
    @Enumerated(EnumType.STRING)
    var quality: FactQuality = FactQuality.PRESENT,
    var observedAt: Instant = Instant.EPOCH,
    var validUntil: Instant? = null,
)
