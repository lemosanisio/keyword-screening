package br.com.pld.customeranalysis.workbenchapi

import br.com.pld.customeranalysis.party.CreatePartyCommand
import br.com.pld.customeranalysis.party.PartyNotFoundException
import br.com.pld.customeranalysis.party.PartyRelationshipJpaRepository
import br.com.pld.customeranalysis.party.PartyService
import br.com.pld.customeranalysis.party.PartySnapshotView
import br.com.pld.customeranalysis.party.PartyType
import br.com.pld.customeranalysis.party.PartyView
import br.com.pld.customeranalysis.analysis.AnalysisCycleService
import br.com.pld.customeranalysis.analysis.AnalysisCycleType
import br.com.pld.customeranalysis.analysis.AnalysisCycleView
import br.com.pld.customeranalysis.analysis.OpenAnalysisCycleCommand
import br.com.pld.customeranalysis.timeline.TimelineService
import br.com.pld.customeranalysis.timeline.TimelineView
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.math.BigDecimal
import java.time.Instant

@RestController
@RequestMapping("/v1/parties")
class PartyController(
    private val partyService: PartyService,
    private val analysisCycleService: AnalysisCycleService,
    private val timelineService: TimelineService,
    private val partyRelationshipRepository: PartyRelationshipJpaRepository,
    private val actorResolver: ActorResolver,
) {
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreatePartyRequest,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
    ): ResponseEntity<PartyResponse> {
        val party = partyService.create(
            CreatePartyCommand(
                partyType = request.partyType,
                officialName = request.officialName,
                sourceSystem = request.sourceSystem,
                actor = actorResolver.commandActor(actorId, actorRole),
                correlationId = actorResolver.correlationId(correlationId),
            ),
        )

        return ResponseEntity
            .created(URI.create("/v1/parties/${party.partyId}"))
            .body(PartyResponse.from(party))
    }

    @GetMapping("/{partyId}")
    fun get(@PathVariable partyId: String): PartyResponse = PartyResponse.from(partyService.get(partyId))

    @PostMapping("/{partyId}/analysis-cycles")
    fun openAnalysisCycle(
        @PathVariable partyId: String,
        @Valid @RequestBody request: OpenAnalysisCycleRequest,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
    ): ResponseEntity<AnalysisCycleResponse> {
        val cycle = analysisCycleService.open(
            OpenAnalysisCycleCommand(
                partyId = partyId,
                cycleType = request.cycleType,
                policyVersion = request.policyVersion,
                actor = actorResolver.commandActor(actorId, actorRole),
                correlationId = actorResolver.correlationId(correlationId),
            ),
        )

        return ResponseEntity
            .created(URI.create("/v1/parties/$partyId/analysis-cycles/${cycle.analysisCycleId}"))
            .body(AnalysisCycleResponse.from(cycle))
    }

    @GetMapping("/{partyId}/timeline")
    fun timeline(@PathVariable partyId: String): TimelineView = timelineService.getByPartyId(partyId)

    @GetMapping("/{partyId}/relationships")
    fun relationships(@PathVariable partyId: String): List<RelationshipView> {
        val relationships = partyRelationshipRepository.findByPartyId(partyId)
        return relationships.map { rel ->
            RelationshipView(
                relationshipId = rel.id,
                fromPartyId = rel.fromPartyId,
                toPartyId = rel.toPartyId,
                type = rel.relationshipType.name,
                participationPercentage = rel.participationPercentage,
                startDate = rel.startDate?.toString(),
                endDate = rel.endDate?.toString(),
                sourceSystem = rel.sourceSystem,
            )
        }
    }

    @ExceptionHandler(PartyNotFoundException::class)
    fun notFound(): ResponseEntity<Void> = ResponseEntity.notFound().build()
}

data class CreatePartyRequest(
    val partyType: PartyType,
    @field:NotBlank
    val officialName: String,
    @field:NotBlank
    val sourceSystem: String,
)

data class PartyResponse(
    val partyId: String,
    val partyType: PartyType,
    val currentSnapshot: PartySnapshotResponse,
) {
    companion object {
        fun from(view: PartyView) = PartyResponse(
            partyId = view.partyId,
            partyType = view.partyType,
            currentSnapshot = PartySnapshotResponse.from(view.currentSnapshot),
        )
    }
}

data class PartySnapshotResponse(
    val snapshotId: String,
    val snapshotVersion: Int,
    val officialName: String,
    val sourceSystem: String,
    val effectiveAt: Instant,
) {
    companion object {
        fun from(view: PartySnapshotView) = PartySnapshotResponse(
            snapshotId = view.snapshotId,
            snapshotVersion = view.snapshotVersion,
            officialName = view.officialName,
            sourceSystem = view.sourceSystem,
            effectiveAt = view.effectiveAt,
        )
    }
}

data class OpenAnalysisCycleRequest(
    val cycleType: AnalysisCycleType,
    @field:NotBlank
    val policyVersion: String,
)

data class AnalysisCycleResponse(
    val analysisCycleId: String,
    val partyId: String,
    val cycleType: AnalysisCycleType,
    val status: String,
    val policyVersion: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(view: AnalysisCycleView) = AnalysisCycleResponse(
            analysisCycleId = view.analysisCycleId,
            partyId = view.partyId,
            cycleType = view.cycleType,
            status = view.status.name,
            policyVersion = view.policyVersion,
            createdAt = view.createdAt,
        )
    }
}


data class RelationshipView(
    val relationshipId: String,
    val fromPartyId: String,
    val toPartyId: String,
    val type: String,
    val participationPercentage: BigDecimal?,
    val startDate: String?,
    val endDate: String?,
    val sourceSystem: String,
)
