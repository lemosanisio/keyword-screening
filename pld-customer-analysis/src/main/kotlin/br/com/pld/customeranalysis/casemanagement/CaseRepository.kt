package br.com.pld.customeranalysis.casemanagement

import org.springframework.data.jpa.repository.JpaRepository

interface CaseJpaRepository : JpaRepository<CaseEntity, String> {
    fun findTopByPartyIdAndOriginAndStatusAndGroupingPolicyVersionOrderByCreatedAtAsc(
        partyId: String,
        origin: CaseOrigin,
        status: CaseStatus,
        groupingPolicyVersion: String,
    ): CaseEntity?

    fun findByStatusOrderByCreatedAtAsc(status: CaseStatus): List<CaseEntity>
}

interface CaseSourceJpaRepository : JpaRepository<CaseSourceEntity, String> {
    fun existsBySourceSystemAndSourceIdAndGroupingPolicyVersion(
        sourceSystem: String,
        sourceId: String,
        groupingPolicyVersion: String,
    ): Boolean

    fun findByCaseIdOrderByAttachedAtAsc(caseId: String): List<CaseSourceEntity>
}
