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

interface CaseCommentJpaRepository : JpaRepository<CaseCommentEntity, String> {
    fun findByCaseIdOrderByCreatedAtAsc(caseId: String): List<CaseCommentEntity>
}

interface SuspicionDecisionJpaRepository : JpaRepository<SuspicionDecisionEntity, String> {
    fun findByCaseIdOrderByDecisionVersionAsc(caseId: String): List<SuspicionDecisionEntity>
    fun findTopByCaseIdOrderByDecisionVersionDesc(caseId: String): SuspicionDecisionEntity?
}

interface AccountDecisionJpaRepository : JpaRepository<AccountDecisionEntity, String> {
    fun findByCaseIdOrderByDecisionVersionAsc(caseId: String): List<AccountDecisionEntity>
    fun findTopByCaseIdOrderByDecisionVersionDesc(caseId: String): AccountDecisionEntity?
}
