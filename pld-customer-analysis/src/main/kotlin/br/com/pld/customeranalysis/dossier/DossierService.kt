package br.com.pld.customeranalysis.dossier

import br.com.pld.customeranalysis.casemanagement.*
import br.com.pld.customeranalysis.common.PrefixedUlid
import br.com.pld.customeranalysis.evidence.EvidenceService
import br.com.pld.customeranalysis.evidence.RequirementOutcome
import br.com.pld.customeranalysis.timeline.TimelineEntryEntity
import br.com.pld.customeranalysis.timeline.TimelineEntryJpaRepository
import br.com.pld.customeranalysis.timeline.VisibilityClassification
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

@Service
class DossierService(
    private val dossierRepository: DossierJpaRepository,
    private val sectionRepository: DossierSectionJpaRepository,
    private val caseJpaRepository: CaseJpaRepository,
    private val suspicionDecisionRepository: SuspicionDecisionJpaRepository,
    private val accountDecisionRepository: AccountDecisionJpaRepository,
    private val evidenceService: EvidenceService,
    private val timelineRepository: TimelineEntryJpaRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) {

    @Transactional
    fun generate(caseId: String, partyId: String, correlationId: String): DossierView {
        val now = Instant.now(clock)
        val existing = dossierRepository.findTopByCaseIdOrderByVersionDesc(caseId)
        val version = (existing?.version ?: 0) + 1

        val dossier = dossierRepository.save(
            DossierEntity(
                id = PrefixedUlid.next("dos_"),
                caseId = caseId,
                partyId = partyId,
                version = version,
                status = DossierStatus.GENERATING,
                asOf = now,
                policyVersion = "dossier-policy-v1",
                createdAt = now,
            ),
        )

        val sections = mutableListOf<DossierSectionEntity>()
        val gaps = mutableListOf<String>()

        // Section: Party summary
        sections += sectionRepository.save(
            DossierSectionEntity(
                id = PrefixedUlid.next("dsc_"),
                dossierId = dossier.id,
                sectionCode = "PARTY_SUMMARY",
                title = "Resumo da parte",
                objectType = "Party",
                objectId = partyId,
                included = true,
                content = objectMapper.writeValueAsString(mapOf("partyId" to partyId)),
                displayOrder = 1,
            ),
        )

        // Section: Evidence matrix
        val matrix = evidenceService.matrixForCase(caseId)
        val pendingReqs = matrix.requirements.filter { it.outcome != RequirementOutcome.SATISFIED && it.outcome != RequirementOutcome.WAIVED }
        sections += sectionRepository.save(
            DossierSectionEntity(
                id = PrefixedUlid.next("dsc_"),
                dossierId = dossier.id,
                sectionCode = "EVIDENCE_MATRIX",
                title = "Matriz de evidências",
                objectType = "EvidenceCollection",
                objectId = matrix.collectionId,
                objectVersion = matrix.revision.toString(),
                included = true,
                gapReason = if (pendingReqs.isNotEmpty()) pendingReqs.joinToString(", ") { "${it.code}: ${it.outcome.name}" } else null,
                content = objectMapper.writeValueAsString(mapOf("requirementCount" to matrix.requirements.size, "pendingCount" to pendingReqs.size)),
                displayOrder = 2,
            ),
        )
        if (pendingReqs.isNotEmpty()) gaps += pendingReqs.map { "${it.code}: ${it.outcome.name}" }

        // Section: Decisions
        val suspicionDecisions = suspicionDecisionRepository.findByCaseIdOrderByDecisionVersionAsc(caseId)
        val accountDecisions = accountDecisionRepository.findByCaseIdOrderByDecisionVersionAsc(caseId)
        val hasDecisions = suspicionDecisions.isNotEmpty() || accountDecisions.isNotEmpty()
        sections += sectionRepository.save(
            DossierSectionEntity(
                id = PrefixedUlid.next("dsc_"),
                dossierId = dossier.id,
                sectionCode = "DECISIONS",
                title = "Decisões",
                included = hasDecisions,
                gapReason = if (!hasDecisions) "NO_DECISIONS_YET" else null,
                content = objectMapper.writeValueAsString(mapOf(
                    "suspicionDecisions" to suspicionDecisions.size,
                    "accountDecisions" to accountDecisions.size,
                )),
                displayOrder = 3,
            ),
        )
        if (!hasDecisions) gaps += "DECISIONS: NO_DECISIONS_YET"

        // Section: Timeline
        sections += sectionRepository.save(
            DossierSectionEntity(
                id = PrefixedUlid.next("dsc_"),
                dossierId = dossier.id,
                sectionCode = "TIMELINE",
                title = "Timeline regulatória",
                objectType = "Case",
                objectId = caseId,
                included = true,
                content = objectMapper.writeValueAsString(mapOf("included" to true)),
                displayOrder = 4,
            ),
        )

        // Section: Signals
        sections += sectionRepository.save(
            DossierSectionEntity(
                id = PrefixedUlid.next("dsc_"),
                dossierId = dossier.id,
                sectionCode = "SIGNALS",
                title = "Sinais transacionais",
                objectType = "Case",
                objectId = caseId,
                included = true,
                content = objectMapper.writeValueAsString(mapOf("included" to true)),
                displayOrder = 5,
            ),
        )

        // Finalize
        val manifestHash = sha256(sections.joinToString("|") { "${it.sectionCode}:${it.objectId}:${it.objectVersion}" })
        dossier.status = DossierStatus.READY
        dossier.generatedAt = Instant.now(clock)
        dossier.manifestHash = manifestHash

        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = partyId,
                entryType = "DOSSIER_GENERATED",
                businessOccurredAt = now,
                recordedAt = Instant.now(clock),
                actorType = "SYSTEM",
                actorId = "dossier-generator",
                summaryCode = "DOSSIER_V${version}_GENERATED",
                objectType = "Dossier",
                objectId = dossier.id,
                objectVersion = version.toString(),
                correlationId = correlationId,
                visibilityClassification = VisibilityClassification.CONFIDENTIAL,
            ),
        )

        return toView(dossier, sections, gaps)
    }

    @Transactional(readOnly = true)
    fun get(dossierId: String): DossierView? {
        val dossier = dossierRepository.findById(dossierId).orElse(null) ?: return null
        val sections = sectionRepository.findByDossierIdOrderByDisplayOrderAsc(dossierId)
        val gaps = sections.filter { it.gapReason != null }.map { "${it.sectionCode}: ${it.gapReason}" }
        return toView(dossier, sections, gaps)
    }

    @Transactional(readOnly = true)
    fun listForCase(caseId: String): List<DossierSummaryView> {
        return dossierRepository.findByCaseIdOrderByVersionDesc(caseId).map { dossier ->
            DossierSummaryView(
                dossierId = dossier.id,
                version = dossier.version,
                status = dossier.status.name,
                asOf = dossier.asOf,
                generatedAt = dossier.generatedAt,
                manifestHash = dossier.manifestHash,
                policyVersion = dossier.policyVersion,
            )
        }
    }

    private fun toView(dossier: DossierEntity, sections: List<DossierSectionEntity>, gaps: List<String>): DossierView = DossierView(
        dossierId = dossier.id,
        caseId = dossier.caseId,
        partyId = dossier.partyId,
        version = dossier.version,
        status = dossier.status.name,
        asOf = dossier.asOf,
        generatedAt = dossier.generatedAt,
        manifestHash = dossier.manifestHash,
        policyVersion = dossier.policyVersion,
        sections = sections.map { sec ->
            DossierSectionView(
                sectionId = sec.id,
                code = sec.sectionCode,
                title = sec.title,
                included = sec.included,
                gapReason = sec.gapReason,
                objectType = sec.objectType,
                objectId = sec.objectId,
                objectVersion = sec.objectVersion,
            )
        },
        gaps = gaps,
    )

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}

data class DossierView(
    val dossierId: String,
    val caseId: String,
    val partyId: String,
    val version: Int,
    val status: String,
    val asOf: Instant,
    val generatedAt: Instant?,
    val manifestHash: String?,
    val policyVersion: String,
    val sections: List<DossierSectionView>,
    val gaps: List<String>,
)

data class DossierSectionView(
    val sectionId: String,
    val code: String,
    val title: String,
    val included: Boolean,
    val gapReason: String?,
    val objectType: String?,
    val objectId: String?,
    val objectVersion: String?,
)

data class DossierSummaryView(
    val dossierId: String,
    val version: Int,
    val status: String,
    val asOf: Instant,
    val generatedAt: Instant?,
    val manifestHash: String?,
    val policyVersion: String,
)
