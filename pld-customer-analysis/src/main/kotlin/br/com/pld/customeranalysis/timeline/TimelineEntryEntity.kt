package br.com.pld.customeranalysis.timeline

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "timeline_entry")
class TimelineEntryEntity(
    @Id
    var id: String = "",

    var partyId: String = "",

    var analysisCycleId: String? = null,

    var entryType: String = "",

    var businessOccurredAt: Instant = Instant.EPOCH,

    var recordedAt: Instant = Instant.EPOCH,

    var actorType: String = "",

    var actorId: String = "",

    var summaryCode: String = "",

    var objectType: String = "",

    var objectId: String = "",

    var objectVersion: String? = null,

    var correlationId: String = "",

    var causationId: String? = null,

    @Enumerated(EnumType.STRING)
    var visibilityClassification: VisibilityClassification = VisibilityClassification.CONFIDENTIAL,
)
