package br.com.pld.customeranalysis.analysis

import org.springframework.data.jpa.repository.JpaRepository

interface AnalysisCycleJpaRepository : JpaRepository<AnalysisCycleEntity, String> {
    fun findByPartyIdAndStatusIn(partyId: String, statuses: List<AnalysisCycleStatus>): List<AnalysisCycleEntity>
    fun findTopByPartyIdOrderByCreatedAtDesc(partyId: String): AnalysisCycleEntity?
}
