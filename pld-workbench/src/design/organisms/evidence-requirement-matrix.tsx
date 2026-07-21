import type { EvidenceMatrix } from "@/api/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { RequirementOutcomeBadge } from "@/design/atoms/requirement-outcome-badge";
import { SourceStatusBadge } from "@/design/atoms/source-status-badge";

type EvidenceRequirementMatrixProps = {
  matrix: EvidenceMatrix;
  busy: boolean;
  onRetry: (requirementId: string) => void;
};

export function EvidenceRequirementMatrix({ matrix, busy, onRetry }: EvidenceRequirementMatrixProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between gap-3 text-sm">
          <span>Matriz de evidências</span>
          <span className="font-mono text-xs text-muted-foreground">rev {matrix.revision}</span>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {matrix.requirements.length === 0 && <p className="text-sm text-muted-foreground">Sem matriz de evidências para este caso.</p>}
        {matrix.requirements.map((requirement) => {
          const latest = requirement.executions[requirement.executions.length - 1];
          const retryable = latest?.status === "UNAVAILABLE" || latest?.status === "ERROR";
          return (
            <div key={requirement.requirementId} className="rounded-lg border p-3 text-sm">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <div className="font-medium">{requirement.title}</div>
                  <div className="mt-1 break-all text-xs text-muted-foreground">
                    {requirement.code} · {requirement.category} · {requirement.mandatory ? "obrigatório" : "opcional"}
                  </div>
                </div>
                <RequirementOutcomeBadge outcome={requirement.outcome} />
              </div>
              {latest && (
                <div className="mt-3 rounded-md bg-muted/60 p-2 text-xs">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-medium">{latest.sourceName}</span>
                    <div className="flex items-center gap-2">
                      <span className="font-mono">tentativa {latest.attempt}</span>
                      <SourceStatusBadge status={latest.status} />
                    </div>
                  </div>
                  <p className="mt-2 text-muted-foreground">{latest.summary}</p>
                  {latest.errorCode && <p className="mt-1 font-mono text-destructive">{latest.errorCode}</p>}
                  {latest.evidence.map((evidence) => (
                    <div key={evidence.evidenceId} className="mt-2 rounded border bg-background p-2">
                      <div className="font-medium">{evidence.title}</div>
                      <div className="mt-1 text-muted-foreground">{evidence.summary}</div>
                      <div className="mt-2 flex flex-wrap gap-1">
                        {evidence.facts.map((fact) => (
                          <span key={fact.factId} className="max-w-full break-all rounded border px-1.5 py-0.5 font-mono text-[11px]">
                            {fact.factCode}: {String(fact.value)} / {fact.quality}
                          </span>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}
              {retryable && (
                <Button className="mt-3" size="sm" variant="outline" disabled={busy} onClick={() => onRetry(requirement.requirementId)}>
                  Retentar fonte
                </Button>
              )}
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}
