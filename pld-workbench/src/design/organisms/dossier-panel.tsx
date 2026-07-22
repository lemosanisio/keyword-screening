import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { generateDossier, getDossier, listDossiers } from "@/api/dossier";
import type { DossierSummary, DossierView } from "@/api/dossier";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useDevActor } from "@/features/auth-dev/dev-actor";
import * as React from "react";

type DossierPanelProps = { caseId: string; partyId: string };

export function DossierPanel({ caseId, partyId }: DossierPanelProps) {
  const { actor } = useDevActor();
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = React.useState<string | null>(null);

  const listQuery = useQuery({ queryKey: ["dossiers", caseId], queryFn: () => listDossiers(caseId) });
  const detailQuery = useQuery({
    queryKey: ["dossier", selectedId],
    queryFn: () => getDossier(caseId, selectedId!),
    enabled: Boolean(selectedId),
  });

  const generateMutation = useMutation({
    mutationFn: () => generateDossier(caseId, partyId, actor),
    onSuccess: (data) => { queryClient.invalidateQueries({ queryKey: ["dossiers", caseId] }); setSelectedId(data.dossierId); },
  });

  const dossiers = listQuery.data ?? [];

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-sm">Dossiê</CardTitle>
          <Button size="sm" onClick={() => generateMutation.mutate()} disabled={generateMutation.isPending}>
            Gerar dossiê
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {dossiers.length === 0 && <p className="text-xs text-muted-foreground">Nenhum dossiê gerado.</p>}
        {dossiers.map((d) => (
          <button
            key={d.dossierId}
            onClick={() => setSelectedId(d.dossierId)}
            className={`w-full rounded border p-2 text-left text-xs ${selectedId === d.dossierId ? "ring-2 ring-primary" : ""}`}
          >
            <div className="flex items-center justify-between">
              <span className="font-mono">v{d.version}</span>
              <span className={`rounded px-1.5 py-0.5 ${d.status === "READY" ? "bg-green-100 text-green-800" : "bg-muted"}`}>{d.status}</span>
            </div>
            <div className="mt-1 text-muted-foreground">{d.policyVersion} · {d.manifestHash?.slice(0, 12) ?? "—"}</div>
          </button>
        ))}

        {detailQuery.data && (
          <div className="rounded border p-3 text-xs">
            <div className="font-medium">Manifesto — v{detailQuery.data.version}</div>
            <div className="mt-2 space-y-1">
              {detailQuery.data.sections.map((sec) => (
                <div key={sec.sectionId} className="flex items-center justify-between">
                  <span>{sec.title}</span>
                  {sec.included ? (
                    <span className="rounded bg-green-50 px-1.5 py-0.5 text-green-700">incluído</span>
                  ) : (
                    <span className="rounded bg-amber-50 px-1.5 py-0.5 text-amber-700">{sec.gapReason}</span>
                  )}
                </div>
              ))}
            </div>
            {detailQuery.data.gaps.length > 0 && (
              <div className="mt-2 rounded bg-amber-50 p-2 text-amber-800">
                <div className="font-medium">Gaps:</div>
                {detailQuery.data.gaps.map((g, i) => <div key={i}>{g}</div>)}
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
