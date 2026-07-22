package br.com.pld.customeranalysis.integration

import br.com.pld.customeranalysis.analysis.AnalysisCycleService
import br.com.pld.customeranalysis.analysis.AnalysisCycleType
import br.com.pld.customeranalysis.analysis.OpenAnalysisCycleCommand
import br.com.pld.customeranalysis.common.PrefixedUlid
import br.com.pld.customeranalysis.identityaccess.Actor
import br.com.pld.customeranalysis.identityaccess.ActorRole
import br.com.pld.customeranalysis.party.*
import br.com.pld.customeranalysis.timeline.TimelineEntryEntity
import br.com.pld.customeranalysis.timeline.TimelineEntryJpaRepository
import br.com.pld.customeranalysis.timeline.VisibilityClassification
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Consome eventos do sistema mestre (onboarding, data change, relationships)
 * e executa as ações correspondentes no domínio de customer-analysis.
 *
 * Em produção seria ativado por SQS consumer; aqui pode ser invocado via endpoint dev.
 */
@Service
class OnboardingEventConsumer(
    private val partyService: PartyService,
    private val partyRepository: PartyJpaRepository,
    private val snapshotRepository: PartySnapshotJpaRepository,
    private val relationshipRepository: PartyRelationshipJpaRepository,
    private val analysisCycleService: AnalysisCycleService,
    private val timelineRepository: TimelineEntryJpaRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(OnboardingEventConsumer::class.java)
    private val systemActor = Actor("onboarding-consumer", ActorRole.SYSTEM)

    @Transactional
    fun processEvent(eventType: String, payload: JsonNode, correlationId: String) {
        when (eventType) {
            "CustomerOnboardingStarted" -> handleOnboarding(payload, correlationId)
            "CustomerDataChanged" -> handleDataChange(payload, correlationId)
            "PartyRelationshipChanged" -> handleRelationshipChange(payload, correlationId)
            else -> logger.debug("Ignoring event type: {}", eventType)
        }
    }

    private fun handleOnboarding(payload: JsonNode, correlationId: String) {
        val externalId = payload.path("externalId").asText("")
        val sourceSystem = payload.path("sourceSystem").asText("ONBOARDING_MOCK")
        val officialName = payload.path("officialName").asText("Party Onboarded")
        val partyType = if (payload.path("partyType").asText("PERSON") == "ORGANIZATION") PartyType.ORGANIZATION else PartyType.PERSON

        // Deduplicação por externalId + sourceSystem
        val existing = snapshotRepository.findByOfficialNameContainingIgnoreCase(externalId)
            .firstOrNull { it.sourceSystem == sourceSystem }
        if (existing != null) {
            logger.debug("Party already exists for externalId={}, skipping", externalId)
            return
        }

        val party = partyService.create(
            CreatePartyCommand(
                partyType = partyType,
                officialName = officialName,
                sourceSystem = sourceSystem,
                actor = systemActor,
                correlationId = correlationId,
            ),
        )

        // Open analysis cycle for onboarding
        analysisCycleService.open(
            OpenAnalysisCycleCommand(
                partyId = party.partyId,
                cycleType = AnalysisCycleType.ONBOARDING,
                policyVersion = "onboarding-policy-v1",
                actor = systemActor,
                correlationId = correlationId,
            ),
        )

        logger.info("Onboarding processed: partyId={}, name={}", party.partyId, officialName)
    }

    private fun handleDataChange(payload: JsonNode, correlationId: String) {
        val partyId = payload.path("partyId").asText("")
        if (partyId.isBlank() || !partyRepository.existsById(partyId)) {
            logger.warn("CustomerDataChanged for unknown party: {}", partyId)
            return
        }

        val now = Instant.now(clock)
        val changedFields = payload.path("changedFields").map { it.asText() }

        // Create new snapshot version
        val currentSnapshot = snapshotRepository.findTopByPartyIdOrderBySnapshotVersionDesc(partyId)
        val newVersion = (currentSnapshot?.snapshotVersion ?: 0) + 1
        snapshotRepository.save(
            PartySnapshotEntity(
                id = PrefixedUlid.next("psn_"),
                partyId = partyId,
                snapshotVersion = newVersion,
                officialName = payload.path("officialName").asText(currentSnapshot?.officialName ?: ""),
                sourceSystem = payload.path("sourceSystem").asText("DATA_CHANGE_MOCK"),
                effectiveAt = now,
                createdAt = now,
            ),
        )

        // Timeline
        timelineRepository.save(
            TimelineEntryEntity(
                id = PrefixedUlid.next("tml_"),
                partyId = partyId,
                entryType = "CUSTOMER_DATA_CHANGED",
                businessOccurredAt = now,
                recordedAt = now,
                actorType = "SYSTEM",
                actorId = "onboarding-consumer",
                summaryCode = "DATA_CHANGED_${changedFields.joinToString("_").take(50)}",
                objectType = "PartySnapshot",
                objectId = partyId,
                objectVersion = newVersion.toString(),
                correlationId = correlationId,
                visibilityClassification = VisibilityClassification.CONFIDENTIAL,
            ),
        )

        // Trigger event-driven review for material changes
        val materialChange = changedFields.any { it in listOf("country", "pepStatus", "riskLevel") }
        if (materialChange) {
            analysisCycleService.open(
                OpenAnalysisCycleCommand(
                    partyId = partyId,
                    cycleType = AnalysisCycleType.EVENT_DRIVEN_REVIEW,
                    policyVersion = "event-driven-review-v1",
                    actor = systemActor,
                    correlationId = correlationId,
                ),
            )
            logger.info("Event-driven review triggered for party={}, fields={}", partyId, changedFields)
        }
    }

    private fun handleRelationshipChange(payload: JsonNode, correlationId: String) {
        val fromPartyId = payload.path("fromPartyId").asText("")
        val toPartyId = payload.path("toPartyId").asText("")
        val type = try {
            RelationshipType.valueOf(payload.path("relationshipType").asText("PARTNER"))
        } catch (_: Exception) {
            RelationshipType.PARTNER
        }
        val action = payload.path("action").asText("ADD")

        if (action == "REMOVE") {
            relationshipRepository.findByFromPartyIdAndToPartyIdAndRelationshipType(fromPartyId, toPartyId, type)?.let {
                it.endDate = LocalDate.now()
            }
            logger.info("Relationship removed: {} -> {} ({})", fromPartyId, toPartyId, type)
        } else {
            val existing = relationshipRepository.findByFromPartyIdAndToPartyIdAndRelationshipType(fromPartyId, toPartyId, type)
            if (existing == null) {
                relationshipRepository.save(
                    PartyRelationshipEntity(
                        id = PrefixedUlid.next("rel_"),
                        fromPartyId = fromPartyId,
                        toPartyId = toPartyId,
                        relationshipType = type,
                        participationPercentage = payload.path("participationPercentage").decimalValue().takeIf { it > BigDecimal.ZERO },
                        startDate = LocalDate.now(),
                        sourceSystem = payload.path("sourceSystem").asText("RELATIONSHIP_MOCK"),
                        sourceEventId = correlationId,
                        createdAt = Instant.now(clock),
                    ),
                )
            }
            logger.info("Relationship added: {} -> {} ({})", fromPartyId, toPartyId, type)
        }

        // Timeline for both parties
        val now = Instant.now(clock)
        listOf(fromPartyId, toPartyId).filter { partyRepository.existsById(it) }.forEach { pid ->
            timelineRepository.save(
                TimelineEntryEntity(
                    id = PrefixedUlid.next("tml_"),
                    partyId = pid,
                    entryType = "RELATIONSHIP_CHANGED",
                    businessOccurredAt = now,
                    recordedAt = now,
                    actorType = "SYSTEM",
                    actorId = "onboarding-consumer",
                    summaryCode = "RELATIONSHIP_${type}_${action}",
                    correlationId = correlationId,
                    visibilityClassification = VisibilityClassification.CONFIDENTIAL,
                ),
            )
        }
    }
}
