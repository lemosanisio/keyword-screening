package br.com.pld.customeranalysis.party

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PartyRelationshipJpaRepository : JpaRepository<PartyRelationshipEntity, String> {
    @Query("SELECT r FROM PartyRelationshipEntity r WHERE r.fromPartyId = :partyId OR r.toPartyId = :partyId ORDER BY r.createdAt DESC")
    fun findByPartyId(partyId: String): List<PartyRelationshipEntity>

    fun findByFromPartyIdAndToPartyIdAndRelationshipType(
        fromPartyId: String,
        toPartyId: String,
        type: RelationshipType,
    ): PartyRelationshipEntity?
}
