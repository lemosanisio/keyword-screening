import type { CaseDetailView, DevActor } from "@/api/types";
import { Alert } from "@/components/ui/alert";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CaseCommentsPanel } from "@/design/organisms/case-comments-panel";
import { CaseHeader } from "@/design/organisms/case-header";
import { CaseTimeline } from "@/design/organisms/case-timeline";
import { CoafPanel } from "@/design/organisms/coaf-panel";
import { DecisionPanel } from "@/design/organisms/decision-panel";
import { DossierPanel } from "@/design/organisms/dossier-panel";
import { EvidenceDetailsPanel } from "@/design/organisms/evidence-details-panel";
import { EvidenceRequirementMatrix } from "@/design/organisms/evidence-requirement-matrix";
import { RelationshipsPanel } from "@/design/organisms/relationships-panel";
import { PartySummary } from "@/design/molecules/party-summary";
import type { DecisionCommand } from "@/api/types";

type CaseWorkspaceTemplateProps = {
  detail: CaseDetailView;
  conflict?: string | null;
  busy: boolean;
  actorRole: DevActor["role"];
  onAssign: () => void;
  onStartAnalysis: () => void;
  onReturnToQueue: () => void;
  onApprove: () => void;
  onComplete: () => void;
  onComment: (body: string) => void;
  onRetryRequirement: (requirementId: string) => void;
  onAccountDecision: (command: DecisionCommand) => void;
  onSuspicionDecision: (command: DecisionCommand) => void;
};

export function CaseWorkspaceTemplate(props: CaseWorkspaceTemplateProps) {
  return (
    <div className="space-y-4">
      <CaseHeader
        detail={props.detail}
        busy={props.busy}
        actorRole={props.actorRole}
        onAssign={props.onAssign}
        onStartAnalysis={props.onStartAnalysis}
        onReturnToQueue={props.onReturnToQueue}
        onApprove={props.onApprove}
        onComplete={props.onComplete}
      />
      {props.conflict && <Alert variant="warning">{props.conflict}</Alert>}
      <div className="grid gap-4 lg:grid-cols-[320px_1fr_380px]">
        <aside className="space-y-4">
          <PartySummary party={props.detail.party} />
          <RelationshipsPanel partyId={props.detail.party.partyId} />
          <SignalsCard detail={props.detail} />
        </aside>
        <section className="space-y-4">
          <EvidenceRequirementMatrix matrix={props.detail.evidenceMatrix} busy={props.busy} onRetry={props.onRetryRequirement} />
          <EvidenceDetailsPanel matrix={props.detail.evidenceMatrix} />
          <CaseTimeline entries={props.detail.timeline.entries} />
          <CaseCommentsPanel comments={props.detail.comments} busy={props.busy} onSubmit={props.onComment} />
        </section>
        <aside>
          <div className="space-y-4">
            <DecisionPanel
              caseVersion={props.detail.case.version}
              accountDecisions={props.detail.accountDecisions}
              suspicionDecisions={props.detail.suspicionDecisions}
              busy={props.busy}
              decisionAllowed={props.detail.decisionReadiness.allowed}
              blockingReasons={props.detail.decisionReadiness.blockingReasons}
              onAccountDecision={props.onAccountDecision}
              onSuspicionDecision={props.onSuspicionDecision}
            />
            <DossierPanel caseId={props.detail.case.caseId} partyId={props.detail.party.partyId} />
            <CoafPanel caseId={props.detail.case.caseId} partyId={props.detail.party.partyId} />
          </div>
        </aside>
      </div>
    </div>
  );
}

function SignalsCard({ detail }: { detail: CaseDetailView }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Sinais e fontes</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {detail.sources.map((source) => (
          <div key={source.sourceId} className="rounded-lg border p-3 text-sm">
            <div className="flex items-center justify-between gap-2">
              <span className="font-medium">{source.signalType ?? source.sourceType}</span>
              <span className="rounded bg-muted px-2 py-0.5 text-xs">{source.severity}</span>
            </div>
            <div className="mt-2 space-y-1 text-xs text-muted-foreground">
              <div>Evaluation: <span className="break-all font-mono text-foreground">{source.evaluationId}</span></div>
              <div>Transaction: <span className="break-all font-mono text-foreground">{source.transactionId}</span></div>
              <div>Route: <span className="break-all font-mono text-foreground">{source.recommendedRoute}</span></div>
            </div>
            {source.purpose && (
              <div className="mt-2 space-y-1 border-t pt-2 text-xs text-muted-foreground">
                <div className="flex items-center gap-2">
                  <span>Finalidade:</span>
                  <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-foreground">{source.purpose}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span>Estado:</span>
                  <StatusBadge status={source.executionStatus} />
                </div>
                {source.evaluationOutcome && (
                  <div className="flex items-center gap-2">
                    <span>Outcome:</span>
                    <span className="font-mono text-foreground">{source.evaluationOutcome}</span>
                    {source.reviewRequired && <span className="rounded bg-amber-100 px-1.5 py-0.5 text-amber-800 dark:bg-amber-900 dark:text-amber-200">revisão</span>}
                  </div>
                )}
                {source.rulesetVersion && (
                  <div>Ruleset: <span className="font-mono text-foreground">{source.rulesetVersion}</span></div>
                )}
                {source.snapshotHash && (
                  <div>Snapshot: <span className="break-all font-mono text-foreground">{source.snapshotHash.slice(0, 16)}…</span></div>
                )}
                {source.indeterminateFacts && source.indeterminateFacts.length > 0 && (
                  <div className="mt-1">
                    <span className="text-amber-600 dark:text-amber-400">Fatos indeterminados:</span>
                    <div className="mt-0.5 flex flex-wrap gap-1">
                      {source.indeterminateFacts.map((fact) => (
                        <span key={fact} className="rounded border border-amber-300 bg-amber-50 px-1.5 py-0.5 text-[11px] dark:border-amber-700 dark:bg-amber-950">{fact}</span>
                      ))}
                    </div>
                  </div>
                )}
                {source.explanation && source.explanation.length > 0 && (
                  <div className="mt-1">
                    <span>Explicação:</span>
                    <div className="mt-0.5 space-y-0.5">
                      {source.explanation.map((step, i) => (
                        <div key={i} className="rounded bg-muted px-1.5 py-0.5 font-mono text-foreground">{step.code}{step.detail ? `: ${step.detail}` : ""}</div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
            <div className="mt-2 flex flex-wrap gap-1">
              {source.ruleMatches.map((rule) => (
                <span key={`${rule.ruleCode}-${rule.ruleVersion}`} className="rounded border px-1.5 py-0.5 text-[11px]">
                  {rule.ruleCode} v{rule.ruleVersion}
                </span>
              ))}
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function StatusBadge({ status }: { status?: string | null }) {
  if (!status) return null;
  const colors: Record<string, string> = {
    COMPLETED: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
    INDETERMINATE: "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200",
    FAILED: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  };
  return <span className={`rounded px-1.5 py-0.5 font-mono ${colors[status] ?? "bg-muted text-foreground"}`}>{status}</span>;
}
