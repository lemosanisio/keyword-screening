import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { EvidenceMatrix, EvidenceRecord, FactRecord } from "@/api/types";

type EvidenceDetailsPanelProps = {
  matrix: EvidenceMatrix;
};

/**
 * Panel that displays detailed evidence results: media mentions, legal proceedings,
 * and name/alias matches. Renders from the evidence matrix requirements.
 */
export function EvidenceDetailsPanel({ matrix }: EvidenceDetailsPanelProps) {
  const allEvidence = matrix.requirements.flatMap((r) =>
    r.executions.flatMap((e) => e.evidence.map((ev) => ({ ...ev, sourceCode: e.sourceCode })))
  );

  const media = allEvidence.filter((e) => e.evidenceType === "MEDIA_CHECK");
  const legal = allEvidence.filter((e) => e.evidenceType === "LEGAL_CHECK");
  const sanctions = allEvidence.filter((e) => e.evidenceType === "SANCTIONS_CHECK");

  if (media.length === 0 && legal.length === 0 && sanctions.length === 0) {
    return null;
  }

  return (
    <div className="space-y-4">
      {sanctions.length > 0 && <SanctionsSection evidence={sanctions} />}
      {legal.length > 0 && <LegalSection evidence={legal} />}
      {media.length > 0 && <MediaSection evidence={media} />}
    </div>
  );
}

function SanctionsSection({ evidence }: { evidence: Array<EvidenceRecord & { sourceCode: string }> }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Listas e sanções</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {evidence.map((ev) => {
          const data = ev.structuredData as Record<string, unknown>;
          const matchScore = data?.matchScore as number | undefined;
          const hasFinding = ev.facts.some((f) => f.factCode === "sanctionsHit" && f.value === true);

          return (
            <div key={ev.evidenceId} className="rounded border p-3 text-sm">
              <div className="flex items-center justify-between">
                <span className="font-medium">{ev.title}</span>
                {hasFinding && <span className="rounded bg-red-100 px-2 py-0.5 text-xs text-red-800">MATCH</span>}
              </div>
              <p className="mt-1 text-xs text-muted-foreground">{ev.summary}</p>
              {matchScore != null && matchScore > 0 && (
                <div className="mt-1 text-xs">
                  Score de similaridade: <span className="font-mono">{matchScore}%</span>
                  {(data?.candidateName as string) && (
                    <span className="ml-2">· Candidato: <span className="font-mono">{data.candidateName as string}</span></span>
                  )}
                </div>
              )}
              <FactList facts={ev.facts} />
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}

function LegalSection({ evidence }: { evidence: Array<EvidenceRecord & { sourceCode: string }> }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Processos judiciais</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {evidence.map((ev) => {
          const data = ev.structuredData as Record<string, unknown>;
          const proceedings = (data?.proceedings as Array<Record<string, string>>) ?? [];

          return (
            <div key={ev.evidenceId} className="rounded border p-3 text-sm">
              <div className="font-medium">{ev.title}</div>
              <p className="mt-1 text-xs text-muted-foreground">{ev.summary}</p>
              {proceedings.length > 0 && (
                <div className="mt-2 space-y-1">
                  {proceedings.map((proc, i) => (
                    <div key={i} className="flex items-center gap-2 rounded bg-muted px-2 py-1 text-xs">
                      <span className="font-mono">{proc.number}</span>
                      <span>{proc.tribunal}</span>
                      <span className="text-muted-foreground">{proc.class}</span>
                      <span className={`ml-auto rounded px-1.5 py-0.5 ${proc.role === "DEFENDANT" ? "bg-amber-100 text-amber-800" : "bg-muted"}`}>
                        {proc.role}
                      </span>
                      <span className={`rounded px-1.5 py-0.5 text-[10px] ${proc.status === "ACTIVE" ? "bg-red-50 text-red-700" : "bg-green-50 text-green-700"}`}>
                        {proc.status}
                      </span>
                    </div>
                  ))}
                </div>
              )}
              <FactList facts={ev.facts} />
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}

function MediaSection({ evidence }: { evidence: Array<EvidenceRecord & { sourceCode: string }> }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Mídia negativa</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {evidence.map((ev) => {
          const data = ev.structuredData as Record<string, unknown>;
          const items = (data?.items as Array<Record<string, unknown>>) ?? [];
          const hasConflict = data?.conflict === true;

          return (
            <div key={ev.evidenceId} className="rounded border p-3 text-sm">
              <div className="flex items-center justify-between">
                <span className="font-medium">{ev.title}</span>
                {hasConflict && <span className="rounded bg-amber-100 px-2 py-0.5 text-xs text-amber-800">CONFLITO</span>}
              </div>
              <p className="mt-1 text-xs text-muted-foreground">{ev.summary}</p>
              {items.length > 0 && (
                <div className="mt-2 space-y-1">
                  {items.map((item, i) => (
                    <div key={i} className="rounded bg-muted px-2 py-1.5 text-xs">
                      <div className="flex items-center justify-between">
                        <span className="font-medium">{item.vehicle as string}</span>
                        <span className={`rounded px-1.5 py-0.5 ${item.relevance === "HIGH" ? "bg-red-100 text-red-700" : "bg-muted-foreground/10"}`}>
                          {item.relevance as string}
                        </span>
                      </div>
                      <p className="mt-0.5 text-muted-foreground">
                        {(item.summary as string)?.startsWith("[Extração") && (
                          <span className="mr-1 rounded bg-blue-50 px-1 text-[9px] text-blue-700 dark:bg-blue-950 dark:text-blue-300">IA</span>
                        )}
                        {item.summary as string}
                      </p>
                      <span className="text-[10px] text-muted-foreground">{item.date as string}</span>
                    </div>
                  ))}
                </div>
              )}
              <FactList facts={ev.facts} />
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}

function FactList({ facts }: { facts: FactRecord[] }) {
  if (facts.length === 0) return null;
  return (
    <div className="mt-2 flex flex-wrap gap-1">
      {facts.map((f) => (
        <span
          key={f.factId}
          className={`rounded px-1.5 py-0.5 text-[10px] ${f.quality === "PRESENT" ? "border bg-background" : "border-amber-300 bg-amber-50"}`}
        >
          {f.label}: {String(f.value)}
        </span>
      ))}
    </div>
  );
}
