package br.com.pld.customeranalysis.analysis

import br.com.pld.customeranalysis.common.PrefixedUlid
import br.com.pld.customeranalysis.identityaccess.Actor
import br.com.pld.customeranalysis.integration.OutboxService
import br.com.pld.customeranalysis.party.PartyJpaRepository
import br.com.pld.customeranalysis.party.PartyNotFoundException
import br.com.pld.customeranalysis.timeline.TimelineEntryEntity
import br.com.pld.customeranalysis.timeline.TimelineEntryJpaRepository
import br.com.pld.customeranalysis.timeline.VisibilityClassification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class AnalysisCycleService(
    private val partyRepository: PartyJpaRepository,
    private val analysisCycleRepository: AnalysisCycleJpaRepository,
    private val timelineRepository: TimelineEntryJpaRepository,
    private val outboxService: OutboxService,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun open(command: OpenAnalysisCycleCommand): AnalysisCycleView {
        if (!partyRepository.existsById(command.partyId)) {
            throw PartyNotFoundException(command.partyId)
        }

        val now = Instant.now(clock)
        val cycle = analysisCycleRepository.save(
            AnalysisCycleEntity(
                id = PrefixedUlid.next("acy_"),
                partyId = command.partyId,
                cycleType = command.cycleType,
                status = AnalysisCycleStatus.CREATED,
                policyVersion = command.policyVersion,
                createdAt = now,
                updatedAt = now,
            ),
        )

        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = command.partyId,
                analysisCycleId = cycle.id,
                entryType = "ANALYSIS_CYCLE_CREATED",
                businessOccurredAt = now,
                recordedAt = now,
                actorType = command.actor.role.name,
                actorId = command.actor.id,
                summaryCode = "ANALYSIS_CYCLE_CREATED",
                objectType = "AnalysisCycle",
                objectId = cycle.id,
                objectVersion = "1",
                correlationId = command.correlationId,
                visibilityClassification = VisibilityClassification.CONFIDENTIAL,
            ),
        )

        outboxService.append(
            eventType = "AnalysisCycleCreated",
            aggregateType = "AnalysisCycle",
            aggregateId = cycle.id,
            payload = mapOf(
                "analysisCycleId" to cycle.id,
                "partyId" to command.partyId,
                "cycleType" to command.cycleType.name,
                "status" to cycle.status.name,
                "policyVersion" to command.policyVersion,
                "correlationId" to command.correlationId,
            ),
        )

        return AnalysisCycleView.from(cycle)
    }
}

data class OpenAnalysisCycleCommand(
    val partyId: String,
    val cycleType: AnalysisCycleType,
    val policyVersion: String,
    val actor: Actor,
    val correlationId: String,
)

data class AnalysisCycleView(
    val analysisCycleId: String,
    val partyId: String,
    val cycleType: AnalysisCycleType,
    val status: AnalysisCycleStatus,
    val policyVersion: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(entity: AnalysisCycleEntity) = AnalysisCycleView(
            analysisCycleId = entity.id,
            partyId = entity.partyId,
            cycleType = entity.cycleType,
            status = entity.status,
            policyVersion = entity.policyVersion,
            createdAt = entity.createdAt,
        )
    }
}
