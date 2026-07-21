import { Badge } from "@/components/ui/badge";
import type { DecisionView } from "@/api/types";

export function DecisionStatus({ decision }: { decision: DecisionView }) {
  return (
    <div className="rounded-lg border p-3 text-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="font-medium">{decision.decision}</div>
          <div className="mt-1 text-xs text-muted-foreground">{decision.policyVersion}</div>
        </div>
        <Badge variant={decision.approvalStatus === "PENDING_APPROVAL" ? "warning" : "success"}>{decision.approvalStatus}</Badge>
      </div>
      <p className="mt-2 line-clamp-3 text-xs text-muted-foreground">{decision.narrative}</p>
      <div className="mt-2 flex flex-wrap gap-1">
        {decision.reasonCodes.map((code) => (
          <Badge key={code} variant="outline">{code}</Badge>
        ))}
      </div>
    </div>
  );
}
