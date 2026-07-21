import { requestJson } from "./http";
import type { CaseDetailView, CaseQueueView, CommandResult, DecisionCommand, DecisionView, DevActor, EvidenceMatrix, EvidenceScenario } from "./types";

export function getCases() {
  return requestJson<CaseQueueView>("/v1/cases");
}

export function getCase(caseId: string) {
  return requestJson<CaseDetailView>(`/v1/cases/${caseId}`);
}

export function assignCase(caseId: string, expectedVersion: number, actor: DevActor) {
  return requestJson<CommandResult>(`/v1/cases/${caseId}/assign`, {
    method: "POST",
    actor,
    body: JSON.stringify({ expectedVersion })
  });
}

export function startAnalysis(caseId: string, expectedVersion: number, actor: DevActor) {
  return requestJson<CommandResult>(`/v1/cases/${caseId}/start-analysis`, {
    method: "POST",
    actor,
    body: JSON.stringify({ expectedVersion })
  });
}

export function returnToQueue(caseId: string, expectedVersion: number, actor: DevActor) {
  return requestJson<CommandResult>(`/v1/cases/${caseId}/return-to-queue`, {
    method: "POST",
    actor,
    body: JSON.stringify({ expectedVersion })
  });
}

export function approveDecision(caseId: string, expectedVersion: number, actor: DevActor) {
  return requestJson<CommandResult>(`/v1/cases/${caseId}/approve-decision`, {
    method: "POST",
    actor,
    body: JSON.stringify({ expectedVersion })
  });
}

export function completeCase(caseId: string, expectedVersion: number, actor: DevActor) {
  return requestJson<CommandResult>(`/v1/cases/${caseId}/complete`, {
    method: "POST",
    actor,
    body: JSON.stringify({ expectedVersion })
  });
}

export function retryRequirement(caseId: string, requirementId: string, expectedEvidenceRevision: number, actor: DevActor) {
  return requestJson<EvidenceMatrix>(`/v1/cases/${caseId}/requirements/${requirementId}/retry`, {
    method: "POST",
    actor,
    body: JSON.stringify({ expectedEvidenceRevision })
  });
}

export function addComment(caseId: string, body: string, actor: DevActor) {
  return requestJson(`/v1/cases/${caseId}/comments`, {
    method: "POST",
    actor,
    body: JSON.stringify({ body })
  });
}

export function issueAccountDecision(caseId: string, command: DecisionCommand, actor: DevActor) {
  return requestJson<DecisionView>(`/v1/cases/${caseId}/account-decisions`, {
    method: "POST",
    actor,
    body: JSON.stringify(command)
  });
}

export function issueSuspicionDecision(caseId: string, command: DecisionCommand, actor: DevActor) {
  return requestJson<DecisionView>(`/v1/cases/${caseId}/suspicion-decisions`, {
    method: "POST",
    actor,
    body: JSON.stringify(command)
  });
}

export function createTransactionCaseScenario(actor: DevActor, scenario: EvidenceScenario = "CLEAR") {
  return requestJson<{ partyId: string; analysisCycleId: string; caseId: string; signalId: string }>("/v1/dev/scenarios/transaction-case", {
    method: "POST",
    actor,
    body: JSON.stringify({ scenario })
  });
}
