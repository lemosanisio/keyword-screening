package br.com.pld.customeranalysis.evidence

import br.com.pld.customeranalysis.common.PrefixedUlid
import br.com.pld.customeranalysis.timeline.TimelineEntryEntity
import br.com.pld.customeranalysis.timeline.TimelineEntryJpaRepository
import br.com.pld.customeranalysis.timeline.VisibilityClassification
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class EvidenceService(
    private val collectionRepository: EvidenceCollectionJpaRepository,
    private val requirementRepository: AnalysisRequirementJpaRepository,
    private val executionRepository: SourceExecutionJpaRepository,
    private val evidenceRepository: EvidenceRecordJpaRepository,
    private val factRepository: FactVersionJpaRepository,
    private val timelineRepository: TimelineEntryJpaRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional(readOnly = true)
    fun matrixForCase(caseId: String): EvidenceMatrixView {
        val collection = collectionRepository.findByCaseId(caseId)
            ?: return EvidenceMatrixView.empty()
        return matrix(collection)
    }

    @Transactional(readOnly = true)
    fun decisionReadiness(caseId: String): ReadinessView {
        val matrix = matrixForCase(caseId)
        return readiness(matrix)
    }

    @Transactional
    fun createDemoCollection(
        caseId: String,
        partyId: String,
        analysisCycleId: String,
        scenario: EvidenceScenario,
        correlationId: String,
    ): EvidenceMatrixView {
        collectionRepository.findByCaseId(caseId)?.let { return matrix(it) }
        val now = Instant.now(clock)
        val collection = collectionRepository.save(
            EvidenceCollectionEntity(
                id = PrefixedUlid.next("evc_"),
                caseId = caseId,
                partyId = partyId,
                analysisCycleId = analysisCycleId,
                scenario = scenario,
                policyVersion = "evidence-demo-policy-1",
                revision = 1,
                createdAt = now,
                updatedAt = now,
            ),
        )

        requirementDefinitions(scenario).forEachIndexed { index, definition ->
            val requirement = requirementRepository.save(
                AnalysisRequirementEntity(
                    id = PrefixedUlid.next("req_"),
                    evidenceCollectionId = collection.id,
                    code = definition.code,
                    title = definition.title,
                    category = definition.category,
                    mandatory = definition.mandatory,
                    outcome = definition.outcome,
                    outcomeReason = definition.outcomeReason,
                    displayOrder = index + 1,
                    evaluatedAt = now,
                ),
            )
            val execution = createExecution(collection, requirement, definition, attempt = 1, correlationId = correlationId, now = now)
            if (definition.status != SourceExecutionStatus.UNAVAILABLE && definition.status != SourceExecutionStatus.ERROR) {
                createEvidenceAndFacts(collection, execution, definition, now)
            }
        }

        recordTimeline(collection, "EVIDENCE_COLLECTION_CREATED", "EVIDENCE_COLLECTION_CREATED", correlationId, now)
        return matrix(collection)
    }

    @Transactional
    fun retryRequirement(caseId: String, requirementId: String, expectedRevision: Int, correlationId: String): EvidenceMatrixView {
        val collection = collectionRepository.findByCaseId(caseId) ?: throw EvidenceCollectionNotFoundException(caseId)
        if (collection.revision != expectedRevision) {
            throw EvidenceRevisionConflictException(collection.revision, expectedRevision)
        }
        val requirement = requirementRepository.findByIdAndEvidenceCollectionId(requirementId, collection.id)
            ?: throw RequirementNotFoundException(requirementId)
        val executions = executionRepository.findByRequirementIdOrderByAttemptAsc(requirement.id)
        val last = executions.lastOrNull() ?: throw RequirementNotFoundException(requirementId)
        if (last.status != SourceExecutionStatus.UNAVAILABLE && last.status != SourceExecutionStatus.ERROR) {
            throw RequirementRetryNotAllowedException(requirementId)
        }

        val now = Instant.now(clock)
        val definition = retryDefinition(requirement)
        val execution = createExecution(collection, requirement, definition, attempt = last.attempt + 1, correlationId = correlationId, now = now)
        createEvidenceAndFacts(collection, execution, definition, now)
        requirement.outcome = RequirementOutcome.SATISFIED
        requirement.outcomeReason = "RETRY_SUCCESSFUL"
        requirement.evaluatedAt = now
        collection.revision += 1
        collection.updatedAt = now

        recordTimeline(collection, "SOURCE_EXECUTION_RETRIED", "SOURCE_EXECUTION_RETRIED_${requirement.code}", correlationId, now)
        recordTimeline(collection, "REQUIREMENT_OUTCOME_CHANGED", "REQUIREMENT_${requirement.code}_SATISFIED", correlationId, now)
        return matrix(collection)
    }

    fun readiness(matrix: EvidenceMatrixView): ReadinessView {
        val blocking = matrix.requirements
            .filter { it.mandatory && it.outcome != RequirementOutcome.SATISFIED && it.outcome != RequirementOutcome.WAIVED }
            .map { "REQUIREMENT_${it.code}_${it.outcome.name}" }
        return ReadinessView(allowed = blocking.isEmpty(), blockingReasons = blocking)
    }

    private fun matrix(collection: EvidenceCollectionEntity): EvidenceMatrixView {
        val requirements = requirementRepository.findByEvidenceCollectionIdOrderByDisplayOrderAsc(collection.id)
        val executionsByRequirement = requirements.associate { requirement ->
            requirement.id to executionRepository.findByRequirementIdOrderByAttemptAsc(requirement.id)
        }
        val executions = executionsByRequirement.values.flatten()
        val evidenceByExecution = evidenceRepository.findBySourceExecutionIdIn(executions.map { it.id })
            .groupBy { it.sourceExecutionId }
        val factByEvidence = factRepository.findByEvidenceIdIn(evidenceByExecution.values.flatten().map { it.id })
            .groupBy { it.evidenceId }

        return EvidenceMatrixView(
            collectionId = collection.id,
            revision = collection.revision,
            scenario = collection.scenario,
            policyVersion = collection.policyVersion,
            requirements = requirements.map { requirement ->
                RequirementView(
                    requirementId = requirement.id,
                    code = requirement.code,
                    title = requirement.title,
                    category = requirement.category,
                    mandatory = requirement.mandatory,
                    outcome = requirement.outcome,
                    outcomeReason = requirement.outcomeReason,
                    executions = executionsByRequirement.getValue(requirement.id).map { execution ->
                        SourceExecutionView(
                            executionId = execution.id,
                            sourceCode = execution.sourceCode,
                            sourceName = execution.sourceName,
                            attempt = execution.attempt,
                            status = execution.status,
                            startedAt = execution.startedAt,
                            completedAt = execution.completedAt,
                            validUntil = execution.validUntil,
                            summary = execution.summary,
                            errorCode = execution.errorCode,
                            evidence = evidenceByExecution[execution.id].orEmpty().map { evidence ->
                                EvidenceView(
                                    evidenceId = evidence.id,
                                    evidenceType = evidence.evidenceType,
                                    title = evidence.title,
                                    summary = evidence.summary,
                                    observedAt = evidence.observedAt,
                                    validUntil = evidence.validUntil,
                                    referenceKey = evidence.referenceKey,
                                    integrityHash = evidence.integrityHash,
                                    classification = evidence.classification,
                                    structuredData = objectMapper.readTree(evidence.structuredData),
                                    facts = factByEvidence[evidence.id].orEmpty().map { fact ->
                                        FactView(
                                            factId = fact.id,
                                            factCode = fact.factCode,
                                            label = fact.label,
                                            value = objectMapper.readTree(fact.value),
                                            quality = fact.quality,
                                            observedAt = fact.observedAt,
                                            validUntil = fact.validUntil,
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
    }

    private fun createExecution(
        collection: EvidenceCollectionEntity,
        requirement: AnalysisRequirementEntity,
        definition: RequirementDefinition,
        attempt: Int,
        correlationId: String,
        now: Instant,
    ): SourceExecutionEntity = executionRepository.save(
        SourceExecutionEntity(
            id = PrefixedUlid.next("sex_"),
            evidenceCollectionId = collection.id,
            requirementId = requirement.id,
            sourceCode = definition.sourceCode,
            sourceName = definition.sourceName,
            attempt = attempt,
            status = definition.status,
            adapterVersion = "simulated-v1",
            startedAt = now.minus(2, ChronoUnit.SECONDS),
            completedAt = now,
            validUntil = definition.validUntil(now),
            summary = definition.executionSummary,
            errorCode = definition.errorCode,
            correlationId = correlationId,
        ),
    )

    private fun createEvidenceAndFacts(
        collection: EvidenceCollectionEntity,
        execution: SourceExecutionEntity,
        definition: RequirementDefinition,
        now: Instant,
    ) {
        val evidence = evidenceRepository.save(
            EvidenceRecordEntity(
                id = PrefixedUlid.next("evd_"),
                sourceExecutionId = execution.id,
                partyId = collection.partyId,
                analysisCycleId = collection.analysisCycleId,
                evidenceType = definition.evidenceType,
                title = definition.evidenceTitle,
                summary = definition.evidenceSummary,
                observedAt = now,
                validUntil = definition.validUntil(now),
                referenceKey = "demo://${definition.sourceCode}/${collection.caseId}/${execution.attempt}",
                integrityHash = "sha256:${PrefixedUlid.ulid()}",
                classification = EvidenceClassification.CONFIDENTIAL,
                structuredData = objectMapper.writeValueAsString(definition.structuredData),
            ),
        )
        definition.facts.forEach { fact ->
            factRepository.save(
                FactVersionEntity(
                    id = PrefixedUlid.next("fac_"),
                    evidenceId = evidence.id,
                    partyId = collection.partyId,
                    analysisCycleId = collection.analysisCycleId,
                    factCode = fact.code,
                    label = fact.label,
                    value = objectMapper.writeValueAsString(fact.value),
                    quality = fact.quality,
                    observedAt = now,
                    validUntil = definition.validUntil(now),
                ),
            )
        }
    }

    private fun recordTimeline(
        collection: EvidenceCollectionEntity,
        entryType: String,
        summaryCode: String,
        correlationId: String,
        now: Instant,
    ) {
        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = collection.partyId,
                analysisCycleId = collection.analysisCycleId,
                entryType = entryType,
                businessOccurredAt = now,
                recordedAt = now,
                actorType = "SYSTEM",
                actorId = "simulated-evidence",
                summaryCode = summaryCode,
                objectType = "EvidenceCollection",
                objectId = collection.id,
                objectVersion = collection.revision.toString(),
                correlationId = correlationId,
                causationId = collection.caseId,
                visibilityClassification = VisibilityClassification.CONFIDENTIAL,
            ),
        )
    }

    private fun requirementDefinitions(scenario: EvidenceScenario): List<RequirementDefinition> {
        val pep = if (scenario == EvidenceScenario.SOURCE_UNAVAILABLE) {
            RequirementDefinition(
                code = "PEP_SANCTIONS_CHECK",
                title = "PEP e sanções consultadas",
                category = "LISTS",
                sourceCode = "PEP_SANCTIONS_SIM",
                sourceName = "Consulta PEP/Sanções simulada",
                status = SourceExecutionStatus.UNAVAILABLE,
                outcome = RequirementOutcome.TECHNICAL_PENDING,
                outcomeReason = "SOURCE_UNAVAILABLE",
                executionSummary = "Fonte simulada indisponível no momento da coleta.",
                errorCode = "SIMULATED_SOURCE_UNAVAILABLE",
                evidenceType = "LIST_CHECK",
                evidenceTitle = "PEP/Sanções indisponível",
                evidenceSummary = "Nenhuma evidência produzida pela indisponibilidade.",
                structuredData = mapOf("sourceAvailable" to false),
                facts = emptyList(),
            )
        } else {
            clearPepDefinition()
        }

        val media = if (scenario == EvidenceScenario.RISK_CONTEXT) {
            RequirementDefinition(
                code = "ADVERSE_MEDIA_REVIEW",
                title = "Mídia negativa revisada",
                category = "MEDIA",
                sourceCode = "MEDIA_SIM",
                sourceName = "Mídia negativa simulada",
                status = SourceExecutionStatus.SUCCESS_WITH_DATA,
                outcome = RequirementOutcome.SATISFIED,
                outcomeReason = "CONTEXTUAL_FINDING_PRESENT",
                executionSummary = "Mídia contextual encontrada e disponível para análise humana.",
                evidenceType = "ADVERSE_MEDIA",
                evidenceTitle = "Notícia contextual simulada",
                evidenceSummary = "Citação em notícia sobre investigação de terceiros; exige análise contextual.",
                structuredData = mapOf("relevance" to "MEDIUM", "stage" to "ALLEGATION", "vehicle" to "Demo News"),
                facts = listOf(FactDefinition("media.contextualFinding", "Finding contextual de mídia", true, FactQuality.PRESENT)),
            )
        } else {
            noResultsMediaDefinition()
        }

        return listOf(
            RequirementDefinition(
                code = "IDENTITY_VERIFIED",
                title = "Identidade cadastral verificada",
                category = "IDENTITY",
                sourceCode = "PARTY_SNAPSHOT",
                sourceName = "Snapshot cadastral",
                status = SourceExecutionStatus.SUCCESS_WITH_DATA,
                outcome = RequirementOutcome.SATISFIED,
                outcomeReason = "SNAPSHOT_PRESENT",
                executionSummary = "Snapshot cadastral disponível para o ciclo.",
                evidenceType = "PARTY_SNAPSHOT",
                evidenceTitle = "Snapshot cadastral vigente",
                evidenceSummary = "Nome oficial e tipo de parte presentes no cadastro simulado.",
                structuredData = mapOf("snapshot" to "present"),
                facts = listOf(FactDefinition("identity.snapshotPresent", "Snapshot cadastral presente", true, FactQuality.PRESENT)),
            ),
            pep,
            media,
            RequirementDefinition(
                code = "LEGAL_LISTS_REVIEW",
                title = "Processos e listas revisados",
                category = "LEGAL",
                sourceCode = "LEGAL_LISTS_SIM",
                sourceName = "Processos/listas simulados",
                status = if (scenario == EvidenceScenario.RISK_CONTEXT) SourceExecutionStatus.PARTIAL else SourceExecutionStatus.SUCCESS_NO_RESULTS,
                outcome = RequirementOutcome.SATISFIED,
                outcomeReason = if (scenario == EvidenceScenario.RISK_CONTEXT) "PARTIAL_CONTEXT_REVIEWED" else "VALID_EMPTY_RESULT",
                executionSummary = if (scenario == EvidenceScenario.RISK_CONTEXT) "Resultado parcial com contexto suficiente para análise humana." else "Consulta válida sem resultados relevantes.",
                evidenceType = "LEGAL_LISTS",
                evidenceTitle = "Consulta jurídica simulada",
                evidenceSummary = if (scenario == EvidenceScenario.RISK_CONTEXT) "Resultado parcial sem conclusão automática." else "Nenhum processo/lista relevante encontrado.",
                structuredData = mapOf("result" to if (scenario == EvidenceScenario.RISK_CONTEXT) "partial" else "empty"),
                facts = listOf(FactDefinition("legal.reviewStatus", "Status da revisão jurídica", if (scenario == EvidenceScenario.RISK_CONTEXT) "PARTIAL" else "NO_RESULTS", FactQuality.PRESENT)),
            ),
            RequirementDefinition(
                code = "TRANSACTION_SIGNAL_REVIEW",
                title = "Sinal transacional revisado",
                category = "TRANSACTION",
                sourceCode = "TRANSACTION_SIGNAL",
                sourceName = "Sinal transacional projetado",
                status = SourceExecutionStatus.SUCCESS_WITH_DATA,
                outcome = RequirementOutcome.SATISFIED,
                outcomeReason = "SIGNAL_ATTACHED_TO_CASE",
                executionSummary = "Sinal transacional anexado ao caso.",
                evidenceType = "TRANSACTION_SIGNAL",
                evidenceTitle = "Sinal transacional relevante",
                evidenceSummary = "Regra transacional indicou necessidade de análise humana.",
                structuredData = mapOf("signal" to "attached"),
                facts = listOf(FactDefinition("transaction.signalPresent", "Sinal transacional presente", true, FactQuality.PRESENT)),
            ),
        )
    }

    private fun clearPepDefinition(): RequirementDefinition = RequirementDefinition(
        code = "PEP_SANCTIONS_CHECK",
        title = "PEP e sanções consultadas",
        category = "LISTS",
        sourceCode = "PEP_SANCTIONS_SIM",
        sourceName = "Consulta PEP/Sanções simulada",
        status = SourceExecutionStatus.SUCCESS_NO_RESULTS,
        outcome = RequirementOutcome.SATISFIED,
        outcomeReason = "VALID_EMPTY_RESULT",
        executionSummary = "Consulta válida sem resultados em PEP/sanções.",
        evidenceType = "LIST_CHECK",
        evidenceTitle = "PEP/Sanções sem resultado",
        evidenceSummary = "Consulta válida não retornou matches.",
        structuredData = mapOf("matches" to 0),
        facts = listOf(FactDefinition("pepSanctions.matchStatus", "Match PEP/Sanções", "NO_RESULTS", FactQuality.PRESENT)),
    )

    private fun noResultsMediaDefinition(): RequirementDefinition = RequirementDefinition(
        code = "ADVERSE_MEDIA_REVIEW",
        title = "Mídia negativa revisada",
        category = "MEDIA",
        sourceCode = "MEDIA_SIM",
        sourceName = "Mídia negativa simulada",
        status = SourceExecutionStatus.SUCCESS_NO_RESULTS,
        outcome = RequirementOutcome.SATISFIED,
        outcomeReason = "VALID_EMPTY_RESULT",
        executionSummary = "Consulta válida sem mídia negativa relevante.",
        evidenceType = "ADVERSE_MEDIA",
        evidenceTitle = "Mídia negativa sem resultado",
        evidenceSummary = "Nenhum item relevante encontrado na fonte simulada.",
        structuredData = mapOf("items" to 0),
        facts = listOf(FactDefinition("media.adverseItems", "Itens de mídia negativa", 0, FactQuality.PRESENT)),
    )

    private fun retryDefinition(requirement: AnalysisRequirementEntity): RequirementDefinition = when (requirement.code) {
        "PEP_SANCTIONS_CHECK" -> clearPepDefinition().copy(outcomeReason = "RETRY_SUCCESSFUL")
        else -> throw RequirementRetryNotAllowedException(requirement.id)
    }

    private fun RequirementDefinition.validUntil(now: Instant): Instant? = now.plus(90, ChronoUnit.DAYS)
}

data class EvidenceMatrixView(
    val collectionId: String?,
    val revision: Int,
    val scenario: EvidenceScenario?,
    val policyVersion: String?,
    val requirements: List<RequirementView>,
) {
    companion object {
        fun empty(): EvidenceMatrixView = EvidenceMatrixView(
            collectionId = null,
            revision = 0,
            scenario = null,
            policyVersion = null,
            requirements = emptyList(),
        )
    }
}

data class RequirementView(
    val requirementId: String,
    val code: String,
    val title: String,
    val category: String,
    val mandatory: Boolean,
    val outcome: RequirementOutcome,
    val outcomeReason: String,
    val executions: List<SourceExecutionView>,
)

data class SourceExecutionView(
    val executionId: String,
    val sourceCode: String,
    val sourceName: String,
    val attempt: Int,
    val status: SourceExecutionStatus,
    val startedAt: Instant,
    val completedAt: Instant,
    val validUntil: Instant?,
    val summary: String,
    val errorCode: String?,
    val evidence: List<EvidenceView>,
)

data class EvidenceView(
    val evidenceId: String,
    val evidenceType: String,
    val title: String,
    val summary: String,
    val observedAt: Instant,
    val validUntil: Instant?,
    val referenceKey: String,
    val integrityHash: String,
    val classification: EvidenceClassification,
    val structuredData: JsonNode,
    val facts: List<FactView>,
)

data class FactView(
    val factId: String,
    val factCode: String,
    val label: String,
    val value: JsonNode,
    val quality: FactQuality,
    val observedAt: Instant,
    val validUntil: Instant?,
)

data class ReadinessView(
    val allowed: Boolean,
    val blockingReasons: List<String>,
)

private data class RequirementDefinition(
    val code: String,
    val title: String,
    val category: String,
    val sourceCode: String,
    val sourceName: String,
    val status: SourceExecutionStatus,
    val outcome: RequirementOutcome,
    val outcomeReason: String,
    val executionSummary: String,
    val evidenceType: String,
    val evidenceTitle: String,
    val evidenceSummary: String,
    val structuredData: Map<String, Any?>,
    val facts: List<FactDefinition>,
    val mandatory: Boolean = true,
    val errorCode: String? = null,
)

private data class FactDefinition(
    val code: String,
    val label: String,
    val value: Any?,
    val quality: FactQuality,
)

class EvidenceCollectionNotFoundException(caseId: String) : RuntimeException("Evidence collection not found for case: $caseId")
class EvidenceRevisionConflictException(actualRevision: Int, expectedRevision: Int) :
    RuntimeException("Evidence revision conflict: expected $expectedRevision but was $actualRevision")
class RequirementNotFoundException(requirementId: String) : RuntimeException("Requirement not found: $requirementId")
class RequirementRetryNotAllowedException(requirementId: String) : RuntimeException("Requirement retry not allowed: $requirementId")
class EvidenceRequirementsBlockedException(reasons: List<String>) : RuntimeException("Evidence requirements blocked: ${reasons.joinToString(",")}")
