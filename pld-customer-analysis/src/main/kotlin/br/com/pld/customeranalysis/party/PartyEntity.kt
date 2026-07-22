package br.com.pld.customeranalysis.party

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "party")
class PartyEntity(
    @Id
    var id: String = "",

    @Enumerated(EnumType.STRING)
    var partyType: PartyType = PartyType.PERSON,

    var lastReviewCompletedAt: Instant? = null,

    var currentRiskLevel: String? = "LOW",

    var createdAt: Instant = Instant.EPOCH,

    var updatedAt: Instant = Instant.EPOCH,
)

@Entity
@Table(name = "party_snapshot")
class PartySnapshotEntity(
    @Id
    var id: String = "",

    var partyId: String = "",

    var snapshotVersion: Int = 0,

    var officialName: String = "",

    var sourceSystem: String = "",

    var effectiveAt: Instant = Instant.EPOCH,

    var createdAt: Instant = Instant.EPOCH,
)
