import { requestJson } from "./http";
import type { DevActor } from "./types";

export type DossierSummary = {
  dossierId: string;
  version: number;
  status: string;
  asOf: string;
  generatedAt?: string | null;
  manifestHash?: string | null;
  policyVersion: string;
};

export type DossierSection = {
  sectionId: string;
  code: string;
  title: string;
  included: boolean;
  gapReason?: string | null;
  objectType?: string | null;
  objectId?: string | null;
  objectVersion?: string | null;
};

export type DossierView = DossierSummary & {
  caseId: string;
  partyId: string;
  sections: DossierSection[];
  gaps: string[];
};

export type CoafCommunication = {
  communicationId: string;
  caseId: string;
  partyId: string;
  dossierId?: string | null;
  status: string;
  version: number;
  operationType?: string | null;
  narrative?: string | null;
  legalFramework?: string | null;
  protocolNumber?: string | null;
  rejectionReason?: string | null;
  deadlineDays?: number | null;
  deadlineStart?: string | null;
  previousId?: string | null;
  createdBy: string;
  createdAt: string;
  submittedAt?: string | null;
  acknowledgedAt?: string | null;
};

export type CoafEvent = {
  eventType: string;
  actorId: string;
  actorRole: string;
  detail?: string | null;
  occurredAt: string;
};

export function generateDossier(caseId: string, partyId: string, actor: DevActor) {
  return requestJson<DossierView>(`/v1/cases/${caseId}/dossier`, {
    method: "POST",
    actor,
    body: JSON.stringify({ partyId }),
  });
}

export function listDossiers(caseId: string) {
  return requestJson<DossierSummary[]>(`/v1/cases/${caseId}/dossier`);
}

export function getDossier(caseId: string, dossierId: string) {
  return requestJson<DossierView>(`/v1/cases/${caseId}/dossier/${dossierId}`);
}

export function listCoafCommunications(caseId: string) {
  return requestJson<CoafCommunication[]>(`/v1/cases/${caseId}/coaf`);
}

export function createCoafDraft(caseId: string, body: { partyId: string; dossierId?: string; operationType?: string; narrative?: string; legalFramework?: string }, actor: DevActor) {
  return requestJson<CoafCommunication>(`/v1/cases/${caseId}/coaf`, {
    method: "POST",
    actor,
    body: JSON.stringify(body),
  });
}

export function submitCoafForReview(caseId: string, communicationId: string, actor: DevActor) {
  return requestJson<CoafCommunication>(`/v1/cases/${caseId}/coaf/${communicationId}/submit-for-review`, { method: "PATCH", actor });
}

export function approveCoaf(caseId: string, communicationId: string, actor: DevActor) {
  return requestJson<CoafCommunication>(`/v1/cases/${caseId}/coaf/${communicationId}/approve`, { method: "PATCH", actor });
}

export function submitCoaf(caseId: string, communicationId: string, actor: DevActor) {
  return requestJson<CoafCommunication>(`/v1/cases/${caseId}/coaf/${communicationId}/submit`, { method: "PATCH", actor });
}

export function getCoafEvents(caseId: string, communicationId: string) {
  return requestJson<CoafEvent[]>(`/v1/cases/${caseId}/coaf/${communicationId}/events`);
}
