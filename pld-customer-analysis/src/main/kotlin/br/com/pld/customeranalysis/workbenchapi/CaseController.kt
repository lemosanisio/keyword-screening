package br.com.pld.customeranalysis.workbenchapi

import br.com.pld.customeranalysis.casemanagement.AddCaseCommentCommand
import br.com.pld.customeranalysis.casemanagement.AccountDecisionValue
import br.com.pld.customeranalysis.casemanagement.AccountDecisionView
import br.com.pld.customeranalysis.casemanagement.CaseCommentView
import br.com.pld.customeranalysis.casemanagement.CaseDetailView
import br.com.pld.customeranalysis.casemanagement.CaseCommandResultView
import br.com.pld.customeranalysis.casemanagement.CaseNotFoundException
import br.com.pld.customeranalysis.casemanagement.CaseQueueView
import br.com.pld.customeranalysis.casemanagement.CaseService
import br.com.pld.customeranalysis.casemanagement.CaseVersionConflictException
import br.com.pld.customeranalysis.casemanagement.CaseCompletionBlockedException
import br.com.pld.customeranalysis.casemanagement.ChangeCaseStatusCommand
import br.com.pld.customeranalysis.casemanagement.DecisionApprovalConflictException
import br.com.pld.customeranalysis.casemanagement.InvalidCaseTransitionException
import br.com.pld.customeranalysis.casemanagement.IssueAccountDecisionCommand
import br.com.pld.customeranalysis.casemanagement.IssueSuspicionDecisionCommand
import br.com.pld.customeranalysis.casemanagement.RetryRequirementCommand
import br.com.pld.customeranalysis.casemanagement.SuspicionDecisionValue
import br.com.pld.customeranalysis.casemanagement.SuspicionDecisionView
import br.com.pld.customeranalysis.evidence.EvidenceMatrixView
import br.com.pld.customeranalysis.evidence.EvidenceRequirementsBlockedException
import br.com.pld.customeranalysis.evidence.EvidenceRevisionConflictException
import br.com.pld.customeranalysis.evidence.RequirementNotFoundException
import br.com.pld.customeranalysis.evidence.RequirementRetryNotAllowedException
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
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

@RestController
@RequestMapping("/v1/cases")
class CaseController(
    private val caseService: CaseService,
    private val actorResolver: ActorResolver,
) {
    @GetMapping
    fun queue(): CaseQueueView = caseService.queue()

    @GetMapping("/{caseId}")
    fun get(@PathVariable caseId: String): CaseDetailView = caseService.get(caseId)

    @PostMapping("/{caseId}/assign")
    fun assign(
        @PathVariable caseId: String,
        @Valid @RequestBody request: CaseTransitionRequest,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
    ): CaseCommandResultView = caseService.assign(caseId, command(request, actorId, actorRole, correlationId))

    @PostMapping("/{caseId}/start-analysis")
    fun startAnalysis(
        @PathVariable caseId: String,
        @Valid @RequestBody request: CaseTransitionRequest,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
    ): CaseCommandResultView = caseService.startAnalysis(caseId, command(request, actorId, actorRole, correlationId))

    @PostMapping("/{caseId}/return-to-queue")
    fun returnToQueue(
        @PathVariable caseId: String,
        @Valid @RequestBody request: CaseTransitionRequest,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
    ): CaseCommandResultView = caseService.returnToQueue(caseId, command(request, actorId, actorRole, correlationId))

    @PostMapping("/{caseId}/comments")
    fun addComment(
        @PathVariable caseId: String,
        @Valid @RequestBody request: AddCaseCommentRequest,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
    ): ResponseEntity<CaseCommentView> {
        val comment = caseService.addComment(
            caseId,
            AddCaseCommentCommand(
                actor = actorResolver.commandActor(actorId, actorRole),
                correlationId = actorResolver.correlationId(correlationId),
                body = request.body,
            ),
        )

        return ResponseEntity
            .created(URI.create("/v1/cases/$caseId/comments/${comment.commentId}"))
            .body(comment)
    }

    @PostMapping("/{caseId}/suspicion-decisions")
    fun issueSuspicionDecision(
        @PathVariable caseId: String,
        @Valid @RequestBody request: IssueSuspicionDecisionRequest,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
    ): ResponseEntity<SuspicionDecisionView> {
        val decision = caseService.issueSuspicionDecision(
            caseId,
            IssueSuspicionDecisionCommand(
                actor = actorResolver.commandActor(actorId, actorRole),
                correlationId = actorResolver.correlationId(correlationId),
                expectedVersion = request.expectedVersion,
                decision = request.decision,
                reasonCodes = request.reasonCodes,
                narrative = request.narrative,
                policyVersion = request.policyVersion,
            ),
        )

        return ResponseEntity
            .created(URI.create("/v1/cases/$caseId/suspicion-decisions/${decision.decisionId}"))
            .body(decision)
    }

    @PostMapping("/{caseId}/account-decisions")
    fun issueAccountDecision(
        @PathVariable caseId: String,
        @Valid @RequestBody request: IssueAccountDecisionRequest,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
    ): ResponseEntity<AccountDecisionView> {
        val decision = caseService.issueAccountDecision(
            caseId,
            IssueAccountDecisionCommand(
                actor = actorResolver.commandActor(actorId, actorRole),
                correlationId = actorResolver.correlationId(correlationId),
                expectedVersion = request.expectedVersion,
                decision = request.decision,
                reasonCodes = request.reasonCodes,
                narrative = request.narrative,
                policyVersion = request.policyVersion,
            ),
        )

        return ResponseEntity
            .created(URI.create("/v1/cases/$caseId/account-decisions/${decision.decisionId}"))
            .body(decision)
    }

    @PostMapping("/{caseId}/approve-decision")
    fun approveDecision(
        @PathVariable caseId: String,
        @Valid @RequestBody request: CaseTransitionRequest,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
    ): CaseCommandResultView = caseService.approvePendingDecision(
        caseId,
        command(request, actorId, actorRole, correlationId),
    )

    @PostMapping("/{caseId}/complete")
    fun complete(
        @PathVariable caseId: String,
        @Valid @RequestBody request: CaseTransitionRequest,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
    ): CaseCommandResultView = caseService.complete(
        caseId,
        command(request, actorId, actorRole, correlationId),
    )

    @PostMapping("/{caseId}/requirements/{requirementId}/retry")
    fun retryRequirement(
        @PathVariable caseId: String,
        @PathVariable requirementId: String,
        @Valid @RequestBody request: RetryRequirementRequest,
        @RequestHeader("X-Actor-Id", required = false) actorId: String?,
        @RequestHeader("X-Actor-Role", required = false) actorRole: String?,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
    ): EvidenceMatrixView = caseService.retryRequirement(
        caseId,
        RetryRequirementCommand(
            actor = actorResolver.commandActor(actorId, actorRole),
            correlationId = actorResolver.correlationId(correlationId),
            requirementId = requirementId,
            expectedEvidenceRevision = request.expectedEvidenceRevision,
        ),
    )

    private fun command(
        request: CaseTransitionRequest,
        actorId: String?,
        actorRole: String?,
        correlationId: String?,
    ): ChangeCaseStatusCommand = ChangeCaseStatusCommand(
        actor = actorResolver.commandActor(actorId, actorRole),
        correlationId = actorResolver.correlationId(correlationId),
        expectedVersion = request.expectedVersion,
    )

    @ExceptionHandler(CaseNotFoundException::class)
    fun notFound(): ResponseEntity<Void> = ResponseEntity.notFound().build()

    @ExceptionHandler(
        CaseVersionConflictException::class,
        CaseCompletionBlockedException::class,
        EvidenceRequirementsBlockedException::class,
        EvidenceRevisionConflictException::class,
        InvalidCaseTransitionException::class,
        DecisionApprovalConflictException::class,
        RequirementRetryNotAllowedException::class,
    )
    fun conflict(): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.CONFLICT).build()

    @ExceptionHandler(RequirementNotFoundException::class)
    fun requirementNotFound(): ResponseEntity<Void> = ResponseEntity.notFound().build()
}

data class CaseTransitionRequest(
    @field:Min(1)
    val expectedVersion: Int,
)

data class AddCaseCommentRequest(
    @field:NotBlank
    val body: String,
)

data class RetryRequirementRequest(
    @field:Min(1)
    val expectedEvidenceRevision: Int,
)

data class IssueSuspicionDecisionRequest(
    @field:Min(1)
    val expectedVersion: Int,
    val decision: SuspicionDecisionValue,
    @field:NotEmpty
    val reasonCodes: List<@NotBlank String>,
    @field:NotBlank
    val narrative: String,
    @field:NotBlank
    val policyVersion: String,
)

data class IssueAccountDecisionRequest(
    @field:Min(1)
    val expectedVersion: Int,
    val decision: AccountDecisionValue,
    @field:NotEmpty
    val reasonCodes: List<@NotBlank String>,
    @field:NotBlank
    val narrative: String,
    @field:NotBlank
    val policyVersion: String,
)
