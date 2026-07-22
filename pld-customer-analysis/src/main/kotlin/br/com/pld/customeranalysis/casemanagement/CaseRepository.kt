package br.com.pld.customeranalysis.casemanagement

import org.springframework.data.jpa.repository.JpaRepository

interface CaseJpaRepository : JpaRepository<CaseEntity, String> {
    fun findTopByPartyIdAndOriginAndStatusInAndGroupingPolicyVersionOrderByCreatedAtAsc(
        partyId: String,
        origin: CaseOrigin,
        statuses: Collection<CaseStatus>,
        groupingPolicyVersion: String,
    ): CaseEntity?

    fun findByStatusOrderByCreatedAtAsc(status: CaseStatus): List<CaseEntity>

    fun findByStatusInOrderByCreatedAtAsc(statuses: Collection<CaseStatus>): List<CaseEntity>

    fun findByPartyIdOrderByCreatedAtDesc(partyId: String): List<CaseEntity>
}

interface CaseSourceJpaRepository : JpaRepository<CaseSourceEntity, String> {
    fun existsBySourceSystemAndSourceIdAndGroupingPolicyVersion(
        sourceSystem: String,
        sourceId: String,
        groupingPolicyVersion: String,
    ): Boolean

    fun findBySourceSystemAndSourceIdAndGroupingPolicyVersion(
        sourceSystem: String,
        sourceId: String,
        groupingPolicyVersion: String,
    ): CaseSourceEntity?

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
