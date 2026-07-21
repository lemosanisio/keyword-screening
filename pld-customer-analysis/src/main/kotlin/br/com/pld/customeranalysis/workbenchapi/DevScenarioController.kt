package br.com.pld.customeranalysis.workbenchapi

import br.com.pld.customeranalysis.casemanagement.CaseJpaRepository
import br.com.pld.customeranalysis.casemanagement.CaseStatus
import br.com.pld.customeranalysis.analysis.AnalysisCycleService
import br.com.pld.customeranalysis.analysis.AnalysisCycleType
import br.com.pld.customeranalysis.analysis.OpenAnalysisCycleCommand
import br.com.pld.customeranalysis.common.PrefixedUlid
import br.com.pld.customeranalysis.evidence.EvidenceScenario
import br.com.pld.customeranalysis.evidence.EvidenceService
import br.com.pld.customeranalysis.identityaccess.Actor
import br.com.pld.customeranalysis.identityaccess.ActorRole
import br.com.pld.customeranalysis.party.CreatePartyCommand
import br.com.pld.customeranalysis.party.PartyService
import br.com.pld.customeranalysis.party.PartyType
import br.com.pld.customeranalysis.transactionprojection.TransactionSignalConsumer
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/v1/dev/scenarios")
class DevScenarioController(
    private val partyService: PartyService,
    private val analysisCycleService: AnalysisCycleService,
    private val transactionSignalConsumer: TransactionSignalConsumer,
    private val caseRepository: CaseJpaRepository,
    private val evidenceService: EvidenceService,
) {
    @PostMapping("/transaction-case")
    fun transactionCase(@RequestBody(required = false) request: DevScenarioRequest?): DevScenarioResponse {
        val correlationId = PrefixedUlid.ulid()
        val actor = Actor(id = "scenario-seed", role = ActorRole.SYSTEM)
        val party = partyService.create(
            CreatePartyCommand(
                partyType = PartyType.PERSON,
                officialName = "Maria Exemplo da Silva",
                sourceSystem = "dev-scenario",
                actor = actor,
                correlationId = correlationId,
            ),
        )
        val cycle = analysisCycleService.open(
            OpenAnalysisCycleCommand(
                partyId = party.partyId,
                cycleType = AnalysisCycleType.TRANSACTION_ALERT,
                policyVersion = "customer-risk-demo-1",
                actor = actor,
                correlationId = correlationId,
            ),
        )
        val signalId = PrefixedUlid.next("sig_")
        transactionSignalConsumer.consume(transactionSignalDetectedEvent(party.partyId, cycle.analysisCycleId, signalId, correlationId))
        val case = caseRepository.findByStatusInOrderByCreatedAtAsc(
            listOf(CaseStatus.OPEN, CaseStatus.ASSIGNED, CaseStatus.IN_ANALYSIS, CaseStatus.PENDING_APPROVAL),
        ).last { it.partyId == party.partyId }
        evidenceService.createDemoCollection(
            caseId = case.id,
            partyId = case.partyId,
            analysisCycleId = requireNotNull(case.analysisCycleId),
            scenario = request?.scenario ?: EvidenceScenario.CLEAR,
            correlationId = correlationId,
        )

        return DevScenarioResponse(
            partyId = party.partyId,
            analysisCycleId = cycle.analysisCycleId,
            caseId = case.id,
            signalId = signalId,
        )
    }

    private fun transactionSignalDetectedEvent(
        partyId: String,
        analysisCycleId: String,
        signalId: String,
        correlationId: String,
    ): String = """
        {
          "eventId": "${PrefixedUlid.ulid()}",
          "eventType": "TransactionSignalDetected",
          "eventVersion": 1,
          "occurredAt": "${Instant.now()}",
          "publishedAt": "${Instant.now()}",
          "producer": "pld-transaction-screening",
          "correlationId": "$correlationId",
          "causationId": "${PrefixedUlid.ulid()}",
          "actor": {"type": "SYSTEM", "id": "rule-engine"},
          "subject": {"partyId": "$partyId", "accountId": "acc_${PrefixedUlid.ulid()}", "analysisCycleId": "$analysisCycleId", "caseId": null},
          "dataClassification": "CONFIDENTIAL",
          "payload": {
            "signalId": "$signalId",
            "evaluationId": "evl_${PrefixedUlid.ulid()}",
            "transactionId": "txn_${PrefixedUlid.ulid()}",
            "signalType": "RULE_MATCH",
            "severity": "HIGH",
            "ruleMatches": [{"ruleCode": "PIX-009", "ruleVersion": 4, "explanationCode": "AMOUNT_OUTSIDE_PROFILE"}],
            "riskProfileVersion": 7,
            "recommendedRoute": "DERIVED_TO_ANALYST"
          }
        }
    """.trimIndent()
}

data class DevScenarioResponse(
    val partyId: String,
    val analysisCycleId: String,
    val caseId: String,
    val signalId: String,
)

data class DevScenarioRequest(
    val scenario: EvidenceScenario = EvidenceScenario.CLEAR,
)
