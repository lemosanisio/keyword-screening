package br.com.pld.customeranalysis.party

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class RelationshipType {
    SHAREHOLDER,
    LEGAL_REPRESENTATIVE,
    DIRECTOR,
    ULTIMATE_BENEFICIAL_OWNER,
    ATTORNEY,
    EMPLOYEE,
    SUPPLIER,
    PARTNER,
}

@Entity
@Table(name = "party_relationship")
class PartyRelationshipEntity(
    @Id var id: String = "",
    var fromPartyId: String = "",
    var toPartyId: String = "",
    @Enumerated(EnumType.STRING)
    var relationshipType: RelationshipType = RelationshipType.SHAREHOLDER,
    var participationPercentage: BigDecimal? = null,
    var startDate: LocalDate? = null,
    var endDate: LocalDate? = null,
    var sourceSystem: String = "",
    var sourceEventId: String? = null,
    var createdAt: Instant = Instant.now(),
)
