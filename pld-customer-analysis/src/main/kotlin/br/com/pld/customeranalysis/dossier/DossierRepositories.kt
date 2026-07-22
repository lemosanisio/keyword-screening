package br.com.pld.customeranalysis.dossier

import org.springframework.data.jpa.repository.JpaRepository

interface DossierJpaRepository : JpaRepository<DossierEntity, String> {
    fun findByCaseIdOrderByVersionDesc(caseId: String): List<DossierEntity>
    fun findTopByCaseIdOrderByVersionDesc(caseId: String): DossierEntity?
}

interface DossierSectionJpaRepository : JpaRepository<DossierSectionEntity, String> {
    fun findByDossierIdOrderByDisplayOrderAsc(dossierId: String): List<DossierSectionEntity>
}

interface CoafCommunicationJpaRepository : JpaRepository<CoafCommunicationEntity, String> {
    fun findByCaseIdOrderByCreatedAtDesc(caseId: String): List<CoafCommunicationEntity>
    fun findTopByCaseIdAndStatusNotOrderByCreatedAtDesc(caseId: String, excludeStatus: CoafStatus): CoafCommunicationEntity?
}

interface CoafCommunicationEventJpaRepository : JpaRepository<CoafCommunicationEventEntity, String> {
    fun findByCommunicationIdOrderByOccurredAtAsc(communicationId: String): List<CoafCommunicationEventEntity>
}
