import type { CaseDetailView, DevActor } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { PriorityBadge } from "@/design/atoms/priority-badge";
import { StatusBadge } from "@/design/atoms/status-badge";
import { VersionTag } from "@/design/atoms/version-tag";

type CaseHeaderProps = {
  detail: CaseDetailView;
  onAssign: () => void;
  onStartAnalysis: () => void;
  onReturnToQueue: () => void;
  onApprove: () => void;
  onComplete: () => void;
  actorRole: DevActor["role"];
  busy: boolean;
};

export function CaseHeader({ detail, onAssign, onStartAnalysis, onReturnToQueue, onApprove, onComplete, actorRole, busy }: CaseHeaderProps) {
  const actions = new Set(detail.availableActions);
  return (
    <Card>
      <CardContent className="flex flex-wrap items-center justify-between gap-4 p-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="break-all font-mono text-sm font-semibold">{detail.case.caseId}</span>
            <StatusBadge status={detail.case.status} />
            <PriorityBadge priority={detail.case.priority} />
            <VersionTag version={detail.case.version} />
          </div>
          <div className="mt-1 text-sm text-muted-foreground">
            {detail.party.currentSnapshot.officialName} · {detail.case.origin} · {detail.case.reasonCode}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {actions.has("ASSIGN") && <Button disabled={busy} size="sm" onClick={onAssign}>Assumir</Button>}
          {actions.has("START_ANALYSIS") && <Button disabled={busy} size="sm" onClick={onStartAnalysis}>Iniciar análise</Button>}
          {actions.has("RETURN_TO_QUEUE") && <Button disabled={busy} size="sm" variant="outline" onClick={onReturnToQueue}>Devolver</Button>}
          {actions.has("APPROVE_DECISION") && actorRole === "APPROVER" && <Button disabled={busy} size="sm" onClick={onApprove}>Aprovar decisão</Button>}
          {actions.has("COMPLETE_CASE") && <Button disabled={busy || !detail.completionReadiness.allowed} size="sm" onClick={onComplete}>Concluir caso</Button>}
        </div>
      </CardContent>
    </Card>
  );
}
