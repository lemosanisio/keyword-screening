package br.com.pld.customeranalysis.party

import br.com.pld.customeranalysis.common.PrefixedUlid
import br.com.pld.customeranalysis.identityaccess.Actor
import br.com.pld.customeranalysis.integration.OutboxService
import br.com.pld.customeranalysis.timeline.TimelineEntryEntity
import br.com.pld.customeranalysis.timeline.TimelineEntryJpaRepository
import br.com.pld.customeranalysis.timeline.VisibilityClassification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class PartyService(
    private val partyRepository: PartyJpaRepository,
    private val snapshotRepository: PartySnapshotJpaRepository,
    private val timelineRepository: TimelineEntryJpaRepository,
    private val outboxService: OutboxService,
    private val clock: Clock = Clock.systemUTC(),
) {

    @Transactional
    fun create(command: CreatePartyCommand): PartyView {
        val now = Instant.now(clock)
        val partyId = PrefixedUlid.next("pty_")
        val snapshotId = PrefixedUlid.next("psn_")

        partyRepository.save(
            PartyEntity(
                id = partyId,
                partyType = command.partyType,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val snapshot = snapshotRepository.save(
            PartySnapshotEntity(
                id = snapshotId,
                partyId = partyId,
                snapshotVersion = 1,
                officialName = command.officialName,
                sourceSystem = command.sourceSystem,
                effectiveAt = now,
                createdAt = now,
            ),
        )

        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = partyId,
                entryType = "PARTY_CREATED",
                businessOccurredAt = now,
                recordedAt = now,
                actorType = command.actor.role.name,
                actorId = command.actor.id,
                summaryCode = "PARTY_CREATED",
                objectType = "Party",
                objectId = partyId,
                objectVersion = "1",
                correlationId = command.correlationId,
                visibilityClassification = VisibilityClassification.CONFIDENTIAL,
            ),
        )

        outboxService.append(
            eventType = "PartyCreated",
            aggregateType = "Party",
            aggregateId = partyId,
            payload = mapOf(
                "partyId" to partyId,
                "partyType" to command.partyType.name,
                "snapshotId" to snapshot.id,
                "snapshotVersion" to snapshot.snapshotVersion,
                "correlationId" to command.correlationId,
            ),
        )

        return PartyView.from(partyRepository.getReferenceById(partyId), snapshot)
    }

    @Transactional(readOnly = true)
    fun get(partyId: String): PartyView {
        val party = partyRepository.findById(partyId).orElseThrow { PartyNotFoundException(partyId) }
        val snapshot = snapshotRepository.findTopByPartyIdOrderBySnapshotVersionDesc(partyId)
            ?: throw PartySnapshotNotFoundException(partyId)

        return PartyView.from(party, snapshot)
    }
}

data class CreatePartyCommand(
    val partyType: PartyType,
    val officialName: String,
    val sourceSystem: String,
    val actor: Actor,
    val correlationId: String,
)

data class PartyView(
    val partyId: String,
    val partyType: PartyType,
    val currentSnapshot: PartySnapshotView,
) {
    companion object {
        fun from(party: PartyEntity, snapshot: PartySnapshotEntity) = PartyView(
            partyId = party.id,
            partyType = party.partyType,
            currentSnapshot = PartySnapshotView(
                snapshotId = snapshot.id,
                snapshotVersion = snapshot.snapshotVersion,
                officialName = snapshot.officialName,
                sourceSystem = snapshot.sourceSystem,
                effectiveAt = snapshot.effectiveAt,
            ),
        )
    }
}

data class PartySnapshotView(
    val snapshotId: String,
    val snapshotVersion: Int,
    val officialName: String,
    val sourceSystem: String,
    val effectiveAt: Instant,
)

class PartyNotFoundException(partyId: String) : RuntimeException("Party not found: $partyId")

class PartySnapshotNotFoundException(partyId: String) : RuntimeException("Party snapshot not found for party: $partyId")
