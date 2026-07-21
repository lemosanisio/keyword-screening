package br.com.pld.customeranalysis.workbenchapi

import br.com.pld.customeranalysis.party.CreatePartyCommand
import br.com.pld.customeranalysis.party.PartyNotFoundException
import br.com.pld.customeranalysis.party.PartyService
import br.com.pld.customeranalysis.party.PartySnapshotView
import br.com.pld.customeranalysis.party.PartyType
import br.com.pld.customeranalysis.party.PartyView
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
import java.time.Instant

@RestController
@RequestMapping("/v1/parties")
class PartyController(
    private val partyService: PartyService,
    private val timelineService: TimelineService,
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
                actor = actorResolver.actor(actorId, actorRole),
                correlationId = actorResolver.correlationId(correlationId),
            ),
        )

        return ResponseEntity
            .created(URI.create("/v1/parties/${party.partyId}"))
            .body(PartyResponse.from(party))
    }

    @GetMapping("/{partyId}")
    fun get(@PathVariable partyId: String): PartyResponse = PartyResponse.from(partyService.get(partyId))

    @GetMapping("/{partyId}/timeline")
    fun timeline(@PathVariable partyId: String): TimelineView = timelineService.getByPartyId(partyId)

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
