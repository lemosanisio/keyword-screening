package br.com.pld.customeranalysis.timeline

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TimelineService(
    private val timelineRepository: TimelineEntryJpaRepository,
) {
    @Transactional(readOnly = true)
    fun getByPartyId(partyId: String): TimelineView = TimelineView(
        entries = timelineRepository.findByPartyIdOrderByRecordedAtAsc(partyId).map(TimelineEntryView::from),
    )
}

data class TimelineView(
    val entries: List<TimelineEntryView>,
)

data class TimelineEntryView(
    val timelineEntryId: String,
    val partyId: String,
    val analysisCycleId: String?,
    val entryType: String,
    val businessOccurredAt: Instant,
    val recordedAt: Instant,
    val actorType: String,
    val actorId: String,
    val summaryCode: String,
    val objectType: String,
    val objectId: String,
    val objectVersion: String?,
    val correlationId: String,
    val causationId: String?,
    val visibilityClassification: VisibilityClassification,
) {
    companion object {
        fun from(entity: TimelineEntryEntity) = TimelineEntryView(
            timelineEntryId = entity.id,
            partyId = entity.partyId,
            analysisCycleId = entity.analysisCycleId,
            entryType = entity.entryType,
            businessOccurredAt = entity.businessOccurredAt,
            recordedAt = entity.recordedAt,
            actorType = entity.actorType,
            actorId = entity.actorId,
            summaryCode = entity.summaryCode,
            objectType = entity.objectType,
            objectId = entity.objectId,
            objectVersion = entity.objectVersion,
            correlationId = entity.correlationId,
            causationId = entity.causationId,
            visibilityClassification = entity.visibilityClassification,
        )
    }
}
