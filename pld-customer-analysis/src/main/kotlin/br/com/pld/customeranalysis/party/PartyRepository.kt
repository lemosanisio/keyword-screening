package br.com.pld.customeranalysis.party

import org.springframework.data.jpa.repository.JpaRepository

interface PartyJpaRepository : JpaRepository<PartyEntity, String>

interface PartySnapshotJpaRepository : JpaRepository<PartySnapshotEntity, String> {
    fun findTopByPartyIdOrderBySnapshotVersionDesc(partyId: String): PartySnapshotEntity?
    fun findByOfficialNameContainingIgnoreCase(name: String): List<PartySnapshotEntity>
}
