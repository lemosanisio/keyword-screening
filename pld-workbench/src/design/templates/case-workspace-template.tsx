import type { CaseDetailView } from "@/api/types";
import { Alert } from "@/components/ui/alert";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CaseCommentsPanel } from "@/design/organisms/case-comments-panel";
import { CaseHeader } from "@/design/organisms/case-header";
import { CaseTimeline } from "@/design/organisms/case-timeline";
import { DecisionPanel } from "@/design/organisms/decision-panel";
import { PartySummary } from "@/design/molecules/party-summary";
import type { DecisionCommand } from "@/api/types";

type CaseWorkspaceTemplateProps = {
  detail: CaseDetailView;
  conflict?: string | null;
  busy: boolean;
  onAssign: () => void;
  onStartAnalysis: () => void;
  onReturnToQueue: () => void;
  onApprove: () => void;
  onComment: (body: string) => void;
  onAccountDecision: (command: DecisionCommand) => void;
  onSuspicionDecision: (command: DecisionCommand) => void;
};

export function CaseWorkspaceTemplate(props: CaseWorkspaceTemplateProps) {
  return (
    <div className="space-y-4">
      <CaseHeader
        detail={props.detail}
        busy={props.busy}
        onAssign={props.onAssign}
        onStartAnalysis={props.onStartAnalysis}
        onReturnToQueue={props.onReturnToQueue}
        onApprove={props.onApprove}
      />
      {props.conflict && <Alert variant="warning">{props.conflict}</Alert>}
      <div className="grid gap-4 lg:grid-cols-[320px_1fr_380px]">
        <aside className="space-y-4">
          <PartySummary party={props.detail.party} />
          <SignalsCard detail={props.detail} />
        </aside>
        <section className="space-y-4">
          <CaseTimeline entries={props.detail.timeline.entries} />
          <CaseCommentsPanel comments={props.detail.comments} busy={props.busy} onSubmit={props.onComment} />
        </section>
        <aside>
          <DecisionPanel
            caseVersion={props.detail.case.version}
            accountDecisions={props.detail.accountDecisions}
            suspicionDecisions={props.detail.suspicionDecisions}
            busy={props.busy}
            onAccountDecision={props.onAccountDecision}
            onSuspicionDecision={props.onSuspicionDecision}
          />
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
              <div>Evaluation: <span className="font-mono text-foreground">{source.evaluationId}</span></div>
              <div>Transaction: <span className="font-mono text-foreground">{source.transactionId}</span></div>
              <div>Route: <span className="font-mono text-foreground">{source.recommendedRoute}</span></div>
            </div>
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
