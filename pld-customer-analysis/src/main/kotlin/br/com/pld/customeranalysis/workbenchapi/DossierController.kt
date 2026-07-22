package br.com.pld.customeranalysis.workbenchapi

import br.com.pld.customeranalysis.dossier.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/v1/cases/{caseId}")
class DossierController(
    private val dossierService: DossierService,
    private val coafService: CoafService,
    private val actorResolver: ActorResolver,
) {

    // --- Dossiê ---

    @PostMapping("/dossier")
    fun generateDossier(
        @PathVariable caseId: String,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
        @RequestBody body: GenerateDossierRequest,
    ): ResponseEntity<DossierView> {
        val result = dossierService.generate(caseId, body.partyId, actorResolver.correlationId(correlationId))
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result)
    }

    @GetMapping("/dossier")
    fun listDossiers(@PathVariable caseId: String): List<DossierSummaryView> =
        dossierService.listForCase(caseId)

    @GetMapping("/dossier/{dossierId}")
    fun getDossier(@PathVariable caseId: String, @PathVariable dossierId: String): ResponseEntity<DossierView> =
        dossierService.get(dossierId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    // --- COAF ---

    @PostMapping("/coaf")
    fun createCoafDraft(
        @PathVariable caseId: String,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestBody body: CreateCoafRequest,
    ): ResponseEntity<CoafCommunicationView> {
        val actor = actorResolver.commandActor(actorId, actorRole)
        val result = coafService.createDraft(
            CreateCoafDraftCommand(
                caseId = caseId,
                partyId = body.partyId,
                dossierId = body.dossierId,
                operationType = body.operationType,
                operationValue = body.operationValue,
                operationDate = body.operationDate,
                narrative = body.narrative,
                legalFramework = body.legalFramework,
                actorId = actor.id,
                actorRole = actor.role.name,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @GetMapping("/coaf")
    fun listCoafCommunications(@PathVariable caseId: String): List<CoafCommunicationView> =
        coafService.listForCase(caseId)

    @GetMapping("/coaf/{communicationId}")
    fun getCoafCommunication(
        @PathVariable caseId: String,
        @PathVariable communicationId: String,
    ): ResponseEntity<CoafCommunicationView> =
        coafService.get(communicationId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @GetMapping("/coaf/{communicationId}/events")
    fun getCoafEvents(
        @PathVariable caseId: String,
        @PathVariable communicationId: String,
    ): List<CoafEventView> = coafService.getEvents(communicationId)

    @PatchMapping("/coaf/{communicationId}/submit-for-review")
    fun submitForReview(
        @PathVariable caseId: String,
        @PathVariable communicationId: String,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
    ): CoafCommunicationView {
        val actor = actorResolver.commandActor(actorId, actorRole)
        return coafService.submitForReview(communicationId, actor.id, actor.role.name)
    }

    @PatchMapping("/coaf/{communicationId}/approve")
    fun approve(
        @PathVariable caseId: String,
        @PathVariable communicationId: String,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
    ): CoafCommunicationView {
        val actor = actorResolver.commandActor(actorId, actorRole)
        return coafService.approve(communicationId, actor.id, actor.role.name)
    }

    @PatchMapping("/coaf/{communicationId}/submit")
    fun submitToCoaf(
        @PathVariable caseId: String,
        @PathVariable communicationId: String,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
    ): CoafCommunicationView {
        val actor = actorResolver.commandActor(actorId, actorRole)
        return coafService.submit(communicationId, actor.id, actor.role.name)
    }

    @PostMapping("/coaf/{communicationId}/rectify")
    fun rectify(
        @PathVariable caseId: String,
        @PathVariable communicationId: String,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestBody body: CreateCoafRequest,
    ): ResponseEntity<CoafCommunicationView> {
        val actor = actorResolver.commandActor(actorId, actorRole)
        val result = coafService.rectify(
            communicationId,
            CreateCoafDraftCommand(
                caseId = caseId,
                partyId = body.partyId,
                dossierId = body.dossierId,
                operationType = body.operationType,
                operationValue = body.operationValue,
                operationDate = body.operationDate,
                narrative = body.narrative,
                legalFramework = body.legalFramework,
                actorId = actor.id,
                actorRole = actor.role.name,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }
}

data class GenerateDossierRequest(val partyId: String)

data class CreateCoafRequest(
    val partyId: String,
    val dossierId: String? = null,
    val operationType: String? = null,
    val operationValue: String? = null,
    val operationDate: LocalDate? = null,
    val narrative: String? = null,
    val legalFramework: String? = null,
)
