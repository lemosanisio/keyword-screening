package br.com.pld.customeranalysis.casemanagement

import br.com.pld.customeranalysis.identityaccess.Actor
import br.com.pld.customeranalysis.identityaccess.ActorRole
import br.com.pld.customeranalysis.party.CreatePartyCommand
import br.com.pld.customeranalysis.party.PartyService
import br.com.pld.customeranalysis.party.PartyType
import br.com.pld.customeranalysis.transactionprojection.TransactionSignalConsumer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class TransactionSignalCaseIntegrationTest {

    @Autowired
    private lateinit var partyService: PartyService


    @Autowired
    private lateinit var transactionSignalConsumer: TransactionSignalConsumer

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.execute(
            "truncate table manual_review_request, transaction_signal_projection, transaction_evaluation_projection, fact_version, evidence_record, source_execution, analysis_requirement, evidence_collection, account_decision, suspicion_decision, case_comment, case_source, pld_case, inbox_event, outbox_event, timeline_entry, analysis_cycle, party_snapshot, party restart identity cascade",
        )
    }

    @Test
    fun `opens case from human routed transaction signal and exposes it in queue`() {
        val partyId = createParty()

        transactionSignalConsumer.consume(transactionSignalDetectedEvent(partyId))

        val cases = cases()
        assertThat(cases).hasSize(1)
        assertThat(cases.single().partyId).isEqualTo(partyId)
        assertThat(cases.single().origin).isEqualTo("TRANSACTION_ALERT")
        assertThat(cases.single().status).isEqualTo("OPEN")
        assertThat(cases.single().sourceCount).isEqualTo(1)
        assertThat(caseSourceIds()).containsExactly("sig_01J6ZK7Q3W8K0M2N4P6R8T0V2F")
        assertThat(timelineEntryTypes(partyId)).containsExactly(
            "PARTY_CREATED",
            "TRANSACTION_SIGNAL_DETECTED",
            "CASE_CREATED",
        )
        assertThat(outboxEventTypes()).containsExactly("PartyCreated", "CustomerRiskProfileUpdated", "CaseStatusChanged")

        mockMvc.get("/v1/cases")
            .andExpect {
                status { isOk() }
                jsonPath("$.cases.length()") { value(1) }
                jsonPath("$.cases[0].caseId") { value(cases.single().caseId) }
                jsonPath("$.cases[0].partyId") { value(partyId) }
                jsonPath("$.cases[0].origin") { value("TRANSACTION_ALERT") }
                jsonPath("$.cases[0].status") { value("OPEN") }
            }

        mockMvc.get("/v1/cases/{caseId}", cases.single().caseId)
            .andExpect {
                status { isOk() }
                jsonPath("$.case.caseId") { value(cases.single().caseId) }
                jsonPath("$.case.partyId") { value(partyId) }
                jsonPath("$.case.version") { value(1) }
                jsonPath("$.availableActions") { value(org.hamcrest.Matchers.contains("ASSIGN")) }
                jsonPath("$.party.currentSnapshot.officialName") { value("Maria Exemplo da Silva") }
                jsonPath("$.sources.length()") { value(1) }
                jsonPath("$.sources[0].sourceId") { value("sig_01J6ZK7Q3W8K0M2N4P6R8T0V2F") }
                jsonPath("$.sources[0].sourceType") { value("TransactionSignal") }
                jsonPath("$.sources[0].severity") { value("HIGH") }
                jsonPath("$.sources[0].evaluationId") { value("evl_01J6ZK7Q3W8K0M2N4P6R8T0V2G") }
                jsonPath("$.sources[0].transactionId") { value("txn_01J6ZK7Q3W8K0M2N4P6R8T0V2H") }
                jsonPath("$.sources[0].signalType") { value("RULE_MATCH") }
                jsonPath("$.sources[0].recommendedRoute") { value("DERIVED_TO_ANALYST") }
                jsonPath("$.sources[0].riskProfileVersion") { value(7) }
                jsonPath("$.sources[0].ruleMatches.length()") { value(1) }
                jsonPath("$.sources[0].ruleMatches[0].ruleCode") { value("PIX-009") }
                jsonPath("$.sources[0].ruleMatches[0].ruleVersion") { value(4) }
                jsonPath("$.sources[0].ruleMatches[0].explanationCode") { value("AMOUNT_OUTSIDE_PROFILE") }
                jsonPath("$.timeline.entries.length()") { value(3) }
                jsonPath("$.timeline.entries[2].entryType") { value("CASE_CREATED") }
            }
    }

    @Test
    fun `returns not found for unknown case`() {
        mockMvc.get("/v1/cases/{caseId}", "cse_01J6ZK7Q3W8K0M2N4P6R8T0BAD")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `groups another transaction signal into existing open party case`() {
        val partyId = createParty()

        transactionSignalConsumer.consume(transactionSignalDetectedEvent(partyId))
        transactionSignalConsumer.consume(
            transactionSignalDetectedEvent(
                partyId = partyId,
                eventId = "01J6ZK7Q3W8K0M2N4P6R8T0W3A",
                signalId = "sig_01J6ZK7Q3W8K0M2N4P6R8T0W3B",
            ),
        )

        assertThat(cases()).hasSize(1)
        assertThat(cases().single().sourceCount).isEqualTo(2)
        assertThat(caseSourceIds()).containsExactly(
            "sig_01J6ZK7Q3W8K0M2N4P6R8T0V2F",
            "sig_01J6ZK7Q3W8K0M2N4P6R8T0W3B",
        )
    }

    @Test
    fun `assigns starts analysis and returns case to queue with status events`() {
        val partyId = createParty()
        transactionSignalConsumer.consume(transactionSignalDetectedEvent(partyId))
        val caseId = cases().single().caseId

        mockMvc.post("/v1/cases/{caseId}/assign", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            header("X-Correlation-Id", "corr-case-assign")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"expectedVersion":1}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ASSIGNED") }
            jsonPath("$.assignedActorId") { value("analyst-1") }
            jsonPath("$.version") { value(2) }
            jsonPath("$.availableActions") {
                value(org.hamcrest.Matchers.containsInAnyOrder("START_ANALYSIS", "RETURN_TO_QUEUE"))
            }
        }

        mockMvc.post("/v1/cases/{caseId}/start-analysis", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            header("X-Correlation-Id", "corr-case-start-analysis")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"expectedVersion":2}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("IN_ANALYSIS") }
            jsonPath("$.assignedActorId") { value("analyst-1") }
            jsonPath("$.version") { value(3) }
            jsonPath("$.availableActions") { value(org.hamcrest.Matchers.containsInAnyOrder("RETURN_TO_QUEUE", "COMPLETE_CASE")) }
        }

        mockMvc.post("/v1/cases/{caseId}/return-to-queue", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            header("X-Correlation-Id", "corr-case-return")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"expectedVersion":3}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("OPEN") }
            jsonPath("$.assignedActorId") { doesNotExist() }
            jsonPath("$.version") { value(4) }
            jsonPath("$.availableActions") { value(org.hamcrest.Matchers.contains("ASSIGN")) }
        }

        assertThat(statusTimelineEntries(partyId)).containsExactly(
            "CASE_CREATED_FROM_TRANSACTION_SIGNAL",
            "CASE_ASSIGNED",
            "CASE_IN_ANALYSIS",
            "CASE_RETURNED_TO_QUEUE",
        )
        assertThat(outboxEventTypes()).containsExactly(
            "PartyCreated",
            "CustomerRiskProfileUpdated",
            "CaseStatusChanged",
            "CaseStatusChanged",
            "CaseStatusChanged",
            "CaseStatusChanged",
        )
    }

    @Test
    fun `rejects case transition with stale version`() {
        createParty().also { partyId -> transactionSignalConsumer.consume(transactionSignalDetectedEvent(partyId)) }
        val caseId = cases().single().caseId

        mockMvc.post("/v1/cases/{caseId}/assign", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"expectedVersion":99}"""
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `adds comment to case workspace and timeline`() {
        val partyId = createParty()
        transactionSignalConsumer.consume(transactionSignalDetectedEvent(partyId))
        val caseId = cases().single().caseId

        mockMvc.post("/v1/cases/{caseId}/comments", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            header("X-Correlation-Id", "corr-case-comment")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"body":"Cliente movimentou valor incompatível com perfil esperado."}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.commentId") { value(org.hamcrest.Matchers.startsWith("cmt_")) }
            jsonPath("$.body") { value("Cliente movimentou valor incompatível com perfil esperado.") }
            jsonPath("$.createdByActorId") { value("analyst-1") }
            jsonPath("$.createdByActorRole") { value("ANALYST") }
        }

        mockMvc.get("/v1/cases/{caseId}", caseId)
            .andExpect {
                status { isOk() }
                jsonPath("$.comments.length()") { value(1) }
                jsonPath("$.comments[0].body") { value("Cliente movimentou valor incompatível com perfil esperado.") }
                jsonPath("$.comments[0].createdByActorId") { value("analyst-1") }
                jsonPath("$.timeline.entries[3].entryType") { value("CASE_COMMENT_ADDED") }
                jsonPath("$.timeline.entries[3].summaryCode") { value("CASE_COMMENT_ADDED") }
            }
    }

    @Test
    fun `issues suspicion decision and completes case explicitly`() {
        val partyId = createParty()
        transactionSignalConsumer.consume(transactionSignalDetectedEvent(partyId))
        val caseId = cases().single().caseId
        assignAndStartAnalysis(caseId)

        mockMvc.post("/v1/cases/{caseId}/suspicion-decisions", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            header("X-Correlation-Id", "corr-suspicion-decision")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """
                {
                  "expectedVersion": 3,
                  "decision": "NO_SUSPICION",
                  "reasonCodes": ["TRANSACTION_SIGNAL_REVIEWED"],
                  "narrative": "Sinal analisado frente ao perfil e não há elementos suficientes de suspeição.",
                  "policyVersion": "suspicion-policy-1"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.decisionId") { value(org.hamcrest.Matchers.startsWith("dec_")) }
            jsonPath("$.caseId") { value(caseId) }
            jsonPath("$.decision") { value("NO_SUSPICION") }
            jsonPath("$.decisionVersion") { value(1) }
            jsonPath("$.decidedByActorId") { value("analyst-1") }
            jsonPath("$.approvalStatus") { value("APPROVED") }
        }

        mockMvc.get("/v1/cases/{caseId}", caseId)
            .andExpect {
                status { isOk() }
                jsonPath("$.case.status") { value("IN_ANALYSIS") }
                jsonPath("$.case.version") { value(4) }
                jsonPath("$.availableActions") { value(org.hamcrest.Matchers.containsInAnyOrder("RETURN_TO_QUEUE", "COMPLETE_CASE")) }
                jsonPath("$.suspicionDecisions.length()") { value(1) }
                jsonPath("$.suspicionDecisions[0].decision") { value("NO_SUSPICION") }
            }

        mockMvc.post("/v1/cases/{caseId}/complete", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            header("X-Correlation-Id", "corr-case-complete")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"expectedVersion":4}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("DECIDED") }
            jsonPath("$.version") { value(5) }
        }

        assertThat(timelineEntryTypes(partyId)).contains("SUSPICION_DECISION_ISSUED")

        assertThat(outboxEventTypes()).containsExactly(
            "PartyCreated",
            "CustomerRiskProfileUpdated",
            "CaseStatusChanged",
            "CaseStatusChanged",
            "CaseStatusChanged",
            "SuspicionDecisionIssued",
            "CaseStatusChanged",
            "CaseStatusChanged",
        )
        outboxPayload("SuspicionDecisionIssued").also { payload ->
            assertThat(payload["decisionId"].asText()).startsWith("dec_")
            assertThat(payload["caseId"].asText()).isEqualTo(caseId)
            assertThat(payload["decision"].asText()).isEqualTo("NO_SUSPICION")
            assertThat(payload["route"].asText()).isEqualTo("DERIVED_TO_ANALYST")
            assertThat(payload["policyVersion"].asText()).isEqualTo("suspicion-policy-1")
            assertThat(payload["analysisCycleId"].asText()).isEqualTo(DEFAULT_ANALYSIS_CYCLE_ID)
            assertThat(payload.has("supersedesDecisionId")).isFalse()
        }
    }

    @Test
    fun `issues account decision and keeps case in analysis`() {
        val partyId = createParty()
        transactionSignalConsumer.consume(transactionSignalDetectedEvent(partyId))
        val caseId = cases().single().caseId
        assignAndStartAnalysis(caseId)

        mockMvc.post("/v1/cases/{caseId}/account-decisions", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            header("X-Correlation-Id", "corr-account-decision")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """
                {
                  "expectedVersion": 3,
                  "decision": "MAINTAIN",
                  "reasonCodes": ["TRANSACTION_SIGNAL_REVIEWED"],
                  "narrative": "Sinal analisado e relacionamento pode ser mantido.",
                  "policyVersion": "account-decision-policy-1"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.decisionId") { value(org.hamcrest.Matchers.startsWith("dec_")) }
            jsonPath("$.caseId") { value(caseId) }
            jsonPath("$.decision") { value("MAINTAIN") }
            jsonPath("$.decisionVersion") { value(1) }
            jsonPath("$.decidedByActorId") { value("analyst-1") }
            jsonPath("$.approvalStatus") { value("APPROVED") }
        }

        mockMvc.get("/v1/cases/{caseId}", caseId)
            .andExpect {
                status { isOk() }
                jsonPath("$.case.status") { value("IN_ANALYSIS") }
                jsonPath("$.case.version") { value(4) }
                jsonPath("$.accountDecisions.length()") { value(1) }
                jsonPath("$.accountDecisions[0].decision") { value("MAINTAIN") }
            }

        assertThat(timelineEntryTypes(partyId)).contains("ACCOUNT_DECISION_ISSUED")
        assertThat(outboxEventTypes()).containsExactly(
            "PartyCreated",
            "CustomerRiskProfileUpdated",
            "CaseStatusChanged",
            "CaseStatusChanged",
            "CaseStatusChanged",
            "AccountDecisionIssued",
            "CaseStatusChanged",
        )
        outboxPayload("AccountDecisionIssued").also { payload ->
            assertThat(payload["decisionId"].asText()).startsWith("dec_")
            assertThat(payload["caseId"].asText()).isEqualTo(caseId)
            assertThat(payload["decision"].asText()).isEqualTo("MAINTAIN")
            assertThat(payload["context"].asText()).isEqualTo("ONGOING")
            assertThat(payload["route"].asText()).isEqualTo("DERIVED_TO_ANALYST")
            assertThat(payload["policyVersion"].asText()).isEqualTo("account-decision-policy-1")
            assertThat(payload["analysisCycleId"].asText()).isEqualTo(DEFAULT_ANALYSIS_CYCLE_ID)
            assertThat(payload.has("supersedesDecisionId")).isFalse()
        }
    }

    @Test
    fun `sensitive account decision requires approval from a second actor before publishing decision event`() {
        val partyId = createParty()
        transactionSignalConsumer.consume(transactionSignalDetectedEvent(partyId))
        val caseId = cases().single().caseId
        assignAndStartAnalysis(caseId)

        mockMvc.post("/v1/cases/{caseId}/account-decisions", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            header("X-Correlation-Id", "corr-account-decision-sensitive")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """
                {
                  "expectedVersion": 3,
                  "decision": "TERMINATE_RELATIONSHIP",
                  "reasonCodes": ["HIGH_IMPACT_ACTION"],
                  "narrative": "Risco operacional justifica encerramento do relacionamento.",
                  "policyVersion": "account-decision-policy-1"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.decisionId") { value(org.hamcrest.Matchers.startsWith("dec_")) }
            jsonPath("$.decision") { value("TERMINATE_RELATIONSHIP") }
            jsonPath("$.approvalStatus") { value("PENDING_APPROVAL") }
        }

        mockMvc.get("/v1/cases/{caseId}", caseId)
            .andExpect {
                status { isOk() }
                jsonPath("$.case.status") { value("PENDING_APPROVAL") }
                jsonPath("$.case.version") { value(4) }
                jsonPath("$.availableActions") { value(org.hamcrest.Matchers.contains("APPROVE_DECISION")) }
                jsonPath("$.accountDecisions[0].approvalStatus") { value("PENDING_APPROVAL") }
            }

        assertThat(outboxEventTypes()).containsExactly(
            "PartyCreated",
            "CustomerRiskProfileUpdated",
            "CaseStatusChanged",
            "CaseStatusChanged",
            "CaseStatusChanged",
            "CaseStatusChanged",
        )

        mockMvc.post("/v1/cases/{caseId}/approve-decision", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            header("X-Correlation-Id", "corr-account-decision-approval-same-actor")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"expectedVersion":4}"""
        }.andExpect {
            status { isConflict() }
        }

        mockMvc.post("/v1/cases/{caseId}/approve-decision", caseId) {
            header("X-Actor-Id", "lead-1")
            header("X-Actor-Role", "APPROVER")
            header("X-Correlation-Id", "corr-account-decision-approval")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"expectedVersion":4}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("IN_ANALYSIS") }
            jsonPath("$.version") { value(5) }
        }

        mockMvc.get("/v1/cases/{caseId}", caseId)
            .andExpect {
                status { isOk() }
                jsonPath("$.accountDecisions[0].approvalStatus") { value("APPROVED") }
                jsonPath("$.accountDecisions[0].approvedByActorId") { value("lead-1") }
            }

        assertThat(timelineEntryTypes(partyId)).contains("ACCOUNT_DECISION_PENDING_APPROVAL", "ACCOUNT_DECISION_APPROVED")
        assertThat(outboxEventTypes()).containsExactly(
            "PartyCreated",
            "CustomerRiskProfileUpdated",
            "CaseStatusChanged",
            "CaseStatusChanged",
            "CaseStatusChanged",
            "CaseStatusChanged",
            "AccountDecisionIssued",
            "CaseStatusChanged",
        )
        outboxPayload("AccountDecisionIssued").also { payload ->
            assertThat(payload["decision"].asText()).isEqualTo("TERMINATE_RELATIONSHIP")
            assertThat(payload["route"].asText()).isEqualTo("MANDATORY_SECOND_APPROVAL")
            assertThat(payload["context"].asText()).isEqualTo("ONGOING")
            assertThat(payload["analysisCycleId"].asText()).isEqualTo(DEFAULT_ANALYSIS_CYCLE_ID)
        }
    }

    @Test
    fun `source unavailable scenario blocks decision until retry satisfies mandatory requirement`() {
        val scenario = mockMvc.post("/v1/dev/scenarios/transaction-case") {
            header("X-Actor-Id", "scenario-cli")
            header("X-Actor-Role", "SYSTEM")
            header("X-Correlation-Id", "corr-source-unavailable-scenario")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"scenario":"SOURCE_UNAVAILABLE"}"""
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString.let(objectMapper::readTree)
        val caseId = scenario["caseId"].asText()

        assignAndStartAnalysis(caseId)

        val blockedWorkspace = mockMvc.get("/v1/cases/{caseId}", caseId)
            .andExpect {
                status { isOk() }
                jsonPath("$.decisionReadiness.allowed") { value(false) }
                jsonPath("$.evidenceMatrix.requirements.length()") { value(5) }
            }
            .andReturn().response.contentAsString.let(objectMapper::readTree)
        val pepRequirement = blockedWorkspace["evidenceMatrix"]["requirements"].first {
            it["code"].asText() == "PEP_SANCTIONS_CHECK"
        }
        val requirementId = pepRequirement["requirementId"].asText()
        val revision = blockedWorkspace["evidenceMatrix"]["revision"].asInt()
        assertThat(pepRequirement["outcome"].asText()).isEqualTo("TECHNICAL_PENDING")
        assertThat(pepRequirement["executions"][0]["status"].asText()).isEqualTo("UNAVAILABLE")

        mockMvc.post("/v1/cases/{caseId}/suspicion-decisions", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            header("X-Correlation-Id", "corr-blocked-decision")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """
                {
                  "expectedVersion": 3,
                  "decision": "NO_SUSPICION",
                  "reasonCodes": ["TRANSACTION_SIGNAL_REVIEWED"],
                  "narrative": "Tentativa de decisão deve ser bloqueada pela fonte indisponível.",
                  "policyVersion": "suspicion-policy-1"
                }
            """.trimIndent()
        }.andExpect {
            status { isConflict() }
        }

        mockMvc.post("/v1/cases/{caseId}/requirements/{requirementId}/retry", caseId, requirementId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            header("X-Correlation-Id", "corr-retry-source")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"expectedEvidenceRevision":$revision}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.revision") { value(revision + 1) }
        }

        mockMvc.get("/v1/cases/{caseId}", caseId)
            .andExpect {
                status { isOk() }
                jsonPath("$.decisionReadiness.allowed") { value(true) }
                jsonPath("$.evidenceMatrix.requirements[1].outcome") { value("SATISFIED") }
                jsonPath("$.evidenceMatrix.requirements[1].executions.length()") { value(2) }
            }

        mockMvc.post("/v1/cases/{caseId}/suspicion-decisions", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            header("X-Correlation-Id", "corr-unblocked-decision")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """
                {
                  "expectedVersion": 3,
                  "decision": "NO_SUSPICION",
                  "reasonCodes": ["TRANSACTION_SIGNAL_REVIEWED"],
                  "narrative": "Fonte retentada e requisitos obrigatórios satisfeitos.",
                  "policyVersion": "suspicion-policy-1"
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
        }

        mockMvc.post("/v1/cases/{caseId}/complete", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            header("X-Correlation-Id", "corr-complete-after-evidence")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"expectedVersion":4}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("DECIDED") }
        }

        assertThat(timelineEntryTypes(scenario["partyId"].asText())).contains(
            "EVIDENCE_COLLECTION_CREATED",
            "SOURCE_EXECUTION_RETRIED",
            "REQUIREMENT_OUTCOME_CHANGED",
            "CASE_COMPLETED",
        )
    }

    private fun createParty(): String = partyService.create(
        CreatePartyCommand(
            partyType = PartyType.PERSON,
            officialName = "Maria Exemplo da Silva",
            sourceSystem = "manual",
            actor = Actor(id = "analyst-1", role = ActorRole.ANALYST),
            correlationId = "corr-party-create",
        ),
    ).partyId

    private fun assignAndStartAnalysis(caseId: String) {
        mockMvc.post("/v1/cases/{caseId}/assign", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"expectedVersion":1}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("/v1/cases/{caseId}/start-analysis", caseId) {
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"expectedVersion":2}"""
        }.andExpect { status { isOk() } }
    }

    private fun cases(): List<CaseRow> = jdbcTemplate.query(
        "select id, party_id, origin, status, source_count from pld_case order by created_at, id",
        { rs, _ ->
            CaseRow(
                caseId = rs.getString("id"),
                partyId = rs.getString("party_id"),
                origin = rs.getString("origin"),
                status = rs.getString("status"),
                sourceCount = rs.getInt("source_count"),
            )
        },
    )

    private fun caseSourceIds(): List<String> = jdbcTemplate.queryForList(
        "select source_id from case_source order by attached_at, id",
        String::class.java,
    )

    private fun timelineEntryTypes(partyId: String): List<String> = jdbcTemplate.queryForList(
        "select entry_type from timeline_entry where party_id = ? order by recorded_at, id",
        String::class.java,
        partyId,
    )

    private fun statusTimelineEntries(partyId: String): List<String> = jdbcTemplate.queryForList(
        "select summary_code from timeline_entry where party_id = ? and object_type = 'Case' order by recorded_at, id",
        String::class.java,
        partyId,
    )

    private fun outboxEventTypes(): List<String> = jdbcTemplate.queryForList(
        "select event_type from outbox_event order by occurred_at, id",
        String::class.java,
    )

    private fun outboxPayload(eventType: String): JsonNode = objectMapper.readTree(
        jdbcTemplate.queryForObject(
            "select payload from outbox_event where event_type = ? order by occurred_at desc, id desc limit 1",
            String::class.java,
            eventType,
        ),
    )

    private fun transactionSignalDetectedEvent(
        partyId: String,
        eventId: String = "01J6ZK7Q3W8K0M2N4P6R8T0V2A",
        signalId: String = "sig_01J6ZK7Q3W8K0M2N4P6R8T0V2F",
    ): String = """
        {
          "eventId": "$eventId",
          "eventType": "TransactionSignalDetected",
          "eventVersion": 1,
          "occurredAt": "2026-07-20T15:30:00Z",
          "publishedAt": "2026-07-20T15:30:01Z",
          "producer": "pld-transaction-screening",
          "correlationId": "01J6ZK7Q3W8K0M2N4P6R8T0V2B",
          "causationId": "01J6ZK7Q3W8K0M2N4P6R8T0V2C",
          "actor": {"type": "SYSTEM", "id": "rule-engine"},
          "subject": {"partyId": "$partyId", "accountId": "acc_01J6ZK7Q3W8K0M2N4P6R8T0V2E", "analysisCycleId": "$DEFAULT_ANALYSIS_CYCLE_ID", "caseId": null},
          "dataClassification": "CONFIDENTIAL",
          "payload": {
            "signalId": "$signalId",
            "evaluationId": "evl_01J6ZK7Q3W8K0M2N4P6R8T0V2G",
            "transactionId": "txn_01J6ZK7Q3W8K0M2N4P6R8T0V2H",
            "signalType": "RULE_MATCH",
            "severity": "HIGH",
            "ruleMatches": [{"ruleCode": "PIX-009", "ruleVersion": 4, "explanationCode": "AMOUNT_OUTSIDE_PROFILE"}],
            "riskProfileVersion": 7,
            "recommendedRoute": "DERIVED_TO_ANALYST"
          }
        }
    """.trimIndent()

    companion object {
        private const val DEFAULT_ANALYSIS_CYCLE_ID = "acy_01J6ZK7Q3W8K0M2N4P6R8T0V7B"

        @Container
        private val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}

data class CaseRow(
    val caseId: String,
    val partyId: String,
    val origin: String,
    val status: String,
    val sourceCount: Int,
)
