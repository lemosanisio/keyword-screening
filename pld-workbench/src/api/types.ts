export type ActorRole = "ANALYST" | "APPROVER" | "RULE_ADMIN" | "AUDITOR" | "SYSTEM";

export type DevActor = {
  id: string;
  role: ActorRole;
  label: string;
};

export type CaseStatus = "OPEN" | "ASSIGNED" | "IN_ANALYSIS" | "PENDING_APPROVAL" | "DECIDED" | string;
export type CaseAction = "ASSIGN" | "START_ANALYSIS" | "RETURN_TO_QUEUE" | "APPROVE_DECISION" | string;

export type CaseQueueItem = {
  caseId: string;
  partyId: string;
  origin: string;
  status: CaseStatus;
  priority: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL" | string;
  reasonCode: string;
  sourceCount: number;
  version: number;
  assignedActorId?: string | null;
  createdAt: string;
};

export type CaseQueueView = {
  cases: CaseQueueItem[];
};

export type PartyResponse = {
  partyId: string;
  partyType: "PERSON" | "ORGANIZATION" | string;
  currentSnapshot: {
    snapshotId: string;
    snapshotVersion: number;
    officialName: string;
    sourceSystem: string;
    effectiveAt: string;
  };
};

export type CaseSource = {
  sourceId: string;
  sourceSystem: string;
  sourceType: string;
  severity: string;
  evaluationId?: string | null;
  transactionId?: string | null;
  signalType?: string | null;
  recommendedRoute?: string | null;
  riskProfileVersion?: number | null;
  ruleMatches: Array<{ ruleCode: string; ruleVersion: number; explanationCode?: string | null }>;
  reasonCode: string;
  correlationId: string;
  causationId: string;
  attachedAt: string;
};

export type CaseComment = {
  commentId: string;
  caseId: string;
  body: string;
  createdByActorId: string;
  createdByActorRole: string;
  correlationId: string;
  createdAt: string;
};

export type DecisionApprovalStatus = "PENDING_APPROVAL" | "APPROVED";

export type DecisionView = {
  decisionId: string;
  caseId: string;
  partyId: string;
  decision: string;
  decisionVersion: number;
  reasonCodes: string[];
  narrative: string;
  policyVersion: string;
  decidedByActorId: string;
  decidedByActorRole: string;
  decidedAt: string;
  correlationId: string;
  previousDecisionId?: string | null;
  approvalStatus: DecisionApprovalStatus;
  approvedByActorId?: string | null;
  approvedAt?: string | null;
};

export type TimelineEntry = {
  timelineEntryId: string;
  partyId: string;
  analysisCycleId?: string | null;
  entryType: string;
  summaryCode: string;
  objectType?: string | null;
  objectId?: string | null;
  objectVersion?: string | null;
  actorType: string;
  actorId: string;
  businessOccurredAt: string;
  recordedAt: string;
  correlationId: string;
};

export type TimelineView = {
  entries: TimelineEntry[];
};

export type CaseDetailView = {
  case: CaseQueueItem;
  party: PartyResponse;
  sources: CaseSource[];
  comments: CaseComment[];
  suspicionDecisions: DecisionView[];
  accountDecisions: DecisionView[];
  timeline: TimelineView;
  availableActions: CaseAction[];
};

export type CommandResult = {
  caseId: string;
  status: CaseStatus;
  assignedActorId?: string | null;
  version: number;
  availableActions: CaseAction[];
};

export type DecisionCommand = {
  expectedVersion: number;
  decision: string;
  reasonCodes: string[];
  narrative: string;
  policyVersion: string;
};
