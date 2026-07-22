package br.com.pld.customeranalysis.dossier

import br.com.pld.customeranalysis.common.PrefixedUlid
import br.com.pld.customeranalysis.timeline.TimelineEntryEntity
import br.com.pld.customeranalysis.timeline.TimelineEntryJpaRepository
import br.com.pld.customeranalysis.timeline.VisibilityClassification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Service
class CoafService(
    private val communicationRepository: CoafCommunicationJpaRepository,
    private val eventRepository: CoafCommunicationEventJpaRepository,
    private val coafSubmissionPort: CoafSubmissionPort,
    private val timelineRepository: TimelineEntryJpaRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    @Transactional
    fun createDraft(command: CreateCoafDraftCommand): CoafCommunicationView {
        val now = Instant.now(clock)
        val comm = communicationRepository.save(
            CoafCommunicationEntity(
                id = PrefixedUlid.next("cof_"),
                caseId = command.caseId,
                partyId = command.partyId,
                dossierId = command.dossierId,
                status = CoafStatus.DRAFT,
                operationType = command.operationType,
                operationValue = command.operationValue,
                operationDate = command.operationDate,
                narrative = command.narrative,
                legalFramework = command.legalFramework,
                deadlineDays = 45, // Prazo regulatório padrão
                deadlineStart = now,
                createdBy = command.actorId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        recordEvent(comm.id, "DRAFT_CREATED", command.actorId, command.actorRole, "Draft criado")
        return toView(comm)
    }

    @Transactional
    fun submitForReview(communicationId: String, actorId: String, actorRole: String): CoafCommunicationView {
        val comm = communicationRepository.findById(communicationId).orElseThrow { CoafNotFoundException(communicationId) }
        require(comm.status == CoafStatus.DRAFT) { "Comunicação deve estar em DRAFT para submeter à revisão" }
        comm.status = CoafStatus.PENDING_REVIEW
        comm.updatedAt = Instant.now(clock)
        recordEvent(comm.id, "SUBMITTED_FOR_REVIEW", actorId, actorRole, null)
        recordTimeline(comm, "COAF_SUBMITTED_FOR_REVIEW", actorId, actorRole)
        return toView(comm)
    }

    @Transactional
    fun approve(communicationId: String, actorId: String, actorRole: String): CoafCommunicationView {
        val comm = communicationRepository.findById(communicationId).orElseThrow { CoafNotFoundException(communicationId) }
        require(comm.status == CoafStatus.PENDING_REVIEW) { "Comunicação deve estar em PENDING_REVIEW para aprovar" }
        require(actorId != comm.createdBy) { "Autor não pode aprovar sua própria comunicação" }
        comm.status = CoafStatus.APPROVED
        comm.updatedAt = Instant.now(clock)
        recordEvent(comm.id, "APPROVED", actorId, actorRole, null)
        recordTimeline(comm, "COAF_APPROVED", actorId, actorRole)
        return toView(comm)
    }

    @Transactional
    fun submit(communicationId: String, actorId: String, actorRole: String): CoafCommunicationView {
        val comm = communicationRepository.findById(communicationId).orElseThrow { CoafNotFoundException(communicationId) }
        require(comm.status == CoafStatus.APPROVED) { "Comunicação deve estar APPROVED para enviar" }

        val result = coafSubmissionPort.submit(comm)
        val now = Instant.now(clock)

        when (result.outcome) {
            SubmissionOutcome.ACKNOWLEDGED -> {
                comm.status = CoafStatus.ACKNOWLEDGED
                comm.protocolNumber = result.protocolNumber
                comm.acknowledgedAt = now
                recordEvent(comm.id, "ACKNOWLEDGED", "SYSTEM", "SYSTEM", "Protocolo: ${result.protocolNumber}")
                recordTimeline(comm, "COAF_ACKNOWLEDGED", actorId, actorRole)
            }
            SubmissionOutcome.REJECTED -> {
                comm.status = CoafStatus.REJECTED
                comm.rejectionReason = result.rejectionReason
                recordEvent(comm.id, "REJECTED", "SYSTEM", "SYSTEM", result.rejectionReason)
                recordTimeline(comm, "COAF_REJECTED", actorId, actorRole)
            }
            SubmissionOutcome.PENDING -> {
                comm.status = CoafStatus.SUBMITTED
                recordEvent(comm.id, "SUBMISSION_PENDING", "SYSTEM", "SYSTEM", "Aguardando confirmação")
            }
        }
        comm.submittedAt = now
        comm.updatedAt = now
        return toView(comm)
    }

    @Transactional
    fun rectify(communicationId: String, command: CreateCoafDraftCommand): CoafCommunicationView {
        val original = communicationRepository.findById(communicationId).orElseThrow { CoafNotFoundException(communicationId) }
        require(original.status == CoafStatus.REJECTED || original.status == CoafStatus.ACKNOWLEDGED) {
            "Retificação só é permitida para comunicações REJECTED ou ACKNOWLEDGED"
        }
        val now = Instant.now(clock)
        val rectified = communicationRepository.save(
            CoafCommunicationEntity(
                id = PrefixedUlid.next("cof_"),
                caseId = command.caseId,
                partyId = command.partyId,
                dossierId = command.dossierId,
                status = CoafStatus.DRAFT,
                version = original.version + 1,
                operationType = command.operationType,
                operationValue = command.operationValue,
                operationDate = command.operationDate,
                narrative = command.narrative,
                legalFramework = command.legalFramework,
                previousId = original.id,
                deadlineDays = 45,
                deadlineStart = now,
                createdBy = command.actorId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        recordEvent(rectified.id, "RECTIFICATION_CREATED", command.actorId, command.actorRole, "Retificação de ${original.id}")
        return toView(rectified)
    }

    @Transactional(readOnly = true)
    fun get(communicationId: String): CoafCommunicationView? {
        val comm = communicationRepository.findById(communicationId).orElse(null) ?: return null
        return toView(comm)
    }

    @Transactional(readOnly = true)
    fun listForCase(caseId: String): List<CoafCommunicationView> =
        communicationRepository.findByCaseIdOrderByCreatedAtDesc(caseId).map(::toView)

    @Transactional(readOnly = true)
    fun getEvents(communicationId: String): List<CoafEventView> =
        eventRepository.findByCommunicationIdOrderByOccurredAtAsc(communicationId).map {
            CoafEventView(eventType = it.eventType, actorId = it.actorId, actorRole = it.actorRole, detail = it.detail, occurredAt = it.occurredAt)
        }

    private fun recordEvent(communicationId: String, eventType: String, actorId: String, actorRole: String, detail: String?) {
        eventRepository.save(
            CoafCommunicationEventEntity(
                id = PrefixedUlid.next("cce_"),
                communicationId = communicationId,
                eventType = eventType,
                actorId = actorId,
                actorRole = actorRole,
                detail = detail,
                occurredAt = Instant.now(clock),
            ),
        )
    }

    private fun recordTimeline(comm: CoafCommunicationEntity, summaryCode: String, actorId: String, actorRole: String) {
        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = comm.partyId,
                entryType = "COAF_STATUS_CHANGED",
                businessOccurredAt = Instant.now(clock),
                recordedAt = Instant.now(clock),
                actorType = actorRole,
                actorId = actorId,
                summaryCode = summaryCode,
                objectType = "CoafCommunication",
                objectId = comm.id,
                objectVersion = comm.version.toString(),
                correlationId = comm.id,
                visibilityClassification = VisibilityClassification.RESTRICTED,
            ),
        )
    }

    private fun toView(comm: CoafCommunicationEntity) = CoafCommunicationView(
        communicationId = comm.id,
        caseId = comm.caseId,
        partyId = comm.partyId,
        dossierId = comm.dossierId,
        status = comm.status.name,
        version = comm.version,
        operationType = comm.operationType,
        narrative = comm.narrative,
        legalFramework = comm.legalFramework,
        protocolNumber = comm.protocolNumber,
        rejectionReason = comm.rejectionReason,
        deadlineDays = comm.deadlineDays,
        deadlineStart = comm.deadlineStart,
        previousId = comm.previousId,
        createdBy = comm.createdBy,
        createdAt = comm.createdAt,
        submittedAt = comm.submittedAt,
        acknowledgedAt = comm.acknowledgedAt,
    )
}

data class CreateCoafDraftCommand(
    val caseId: String,
    val partyId: String,
    val dossierId: String?,
    val operationType: String?,
    val operationValue: String?,
    val operationDate: LocalDate?,
    val narrative: String?,
    val legalFramework: String?,
    val actorId: String,
    val actorRole: String,
)

data class CoafCommunicationView(
    val communicationId: String,
    val caseId: String,
    val partyId: String,
    val dossierId: String?,
    val status: String,
    val version: Int,
    val operationType: String?,
    val narrative: String?,
    val legalFramework: String?,
    val protocolNumber: String?,
    val rejectionReason: String?,
    val deadlineDays: Int?,
    val deadlineStart: Instant?,
    val previousId: String?,
    val createdBy: String,
    val createdAt: Instant,
    val submittedAt: Instant?,
    val acknowledgedAt: Instant?,
)

data class CoafEventView(
    val eventType: String,
    val actorId: String,
    val actorRole: String,
    val detail: String?,
    val occurredAt: Instant,
)

class CoafNotFoundException(id: String) : RuntimeException("Comunicação COAF não encontrada: $id")
