package br.com.pld.customeranalysis.timeline

import org.springframework.data.jpa.repository.JpaRepository

interface TimelineEntryJpaRepository : JpaRepository<TimelineEntryEntity, String> {
    fun findByPartyIdOrderByRecordedAtAsc(partyId: String): List<TimelineEntryEntity>
}
