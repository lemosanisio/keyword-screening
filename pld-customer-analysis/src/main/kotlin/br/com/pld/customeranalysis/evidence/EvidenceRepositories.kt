package br.com.pld.customeranalysis.evidence

import org.springframework.data.jpa.repository.JpaRepository

interface EvidenceCollectionJpaRepository : JpaRepository<EvidenceCollectionEntity, String> {
    fun findByCaseId(caseId: String): EvidenceCollectionEntity?
}

interface AnalysisRequirementJpaRepository : JpaRepository<AnalysisRequirementEntity, String> {
    fun findByEvidenceCollectionIdOrderByDisplayOrderAsc(evidenceCollectionId: String): List<AnalysisRequirementEntity>
    fun findByIdAndEvidenceCollectionId(id: String, evidenceCollectionId: String): AnalysisRequirementEntity?
}

interface SourceExecutionJpaRepository : JpaRepository<SourceExecutionEntity, String> {
    fun findByRequirementIdOrderByAttemptAsc(requirementId: String): List<SourceExecutionEntity>
    fun findTopByRequirementIdAndSourceCodeOrderByAttemptDesc(requirementId: String, sourceCode: String): SourceExecutionEntity?
}

interface EvidenceRecordJpaRepository : JpaRepository<EvidenceRecordEntity, String> {
    fun findBySourceExecutionIdIn(sourceExecutionIds: Collection<String>): List<EvidenceRecordEntity>
}

interface FactVersionJpaRepository : JpaRepository<FactVersionEntity, String> {
    fun findByEvidenceIdIn(evidenceIds: Collection<String>): List<FactVersionEntity>
}
