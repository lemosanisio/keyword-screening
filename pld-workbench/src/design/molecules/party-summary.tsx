import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { PartyResponse } from "@/api/types";

const riskColors: Record<string, string> = {
  HIGH: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  MEDIUM: "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200",
  LOW: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
};

export function PartySummary({ party }: { party: PartyResponse }) {
  const risk = party.riskProfile;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Parte analisada</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        <div className="font-semibold">{party.currentSnapshot.officialName}</div>

        {risk && (
          <div className="space-y-1.5">
            <div className="flex items-center gap-2">
              <span className="text-xs text-muted-foreground">Risco:</span>
              <span className={`rounded px-2 py-0.5 text-xs font-medium ${riskColors[risk.riskLevel] ?? "bg-muted text-foreground"}`}>
                {risk.riskLevel}
              </span>
            </div>
            {risk.segments.length > 0 && (
              <div className="flex flex-wrap gap-1">
                {risk.segments.map((seg) => (
                  <span key={seg} className="rounded border px-1.5 py-0.5 text-[10px]">{seg}</span>
                ))}
              </div>
            )}
            <div className="text-[10px] text-muted-foreground">
              Válido: {new Date(risk.effectiveFrom).toLocaleDateString("pt-BR")} — {new Date(risk.validUntil).toLocaleDateString("pt-BR")}
              <span className="ml-1">· {risk.policyVersion}</span>
            </div>
          </div>
        )}

        <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-2 text-xs text-muted-foreground">
          <span>Tipo</span>
          <span className="text-right text-foreground">{party.partyType}</span>
          <span>Party ID</span>
          <span className="min-w-0 break-all text-right font-mono text-foreground">{party.partyId}</span>
          <span>Snapshot</span>
          <span className="text-right font-mono text-foreground">v{party.currentSnapshot.snapshotVersion}</span>
          <span>Fonte</span>
          <span className="text-right text-foreground">{party.currentSnapshot.sourceSystem}</span>
        </div>
      </CardContent>
    </Card>
  );
}
