import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { approveCoaf, createCoafDraft, getCoafEvents, listCoafCommunications, submitCoaf, submitCoafForReview } from "@/api/dossier";
import type { CoafCommunication, CoafEvent } from "@/api/dossier";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useDevActor } from "@/features/auth-dev/dev-actor";
import * as React from "react";

type CoafPanelProps = { caseId: string; partyId: string; dossierId?: string | null };

const statusColors: Record<string, string> = {
  DRAFT: "bg-muted text-foreground",
  PENDING_REVIEW: "bg-amber-100 text-amber-800",
  APPROVED: "bg-blue-100 text-blue-800",
  SUBMITTED: "bg-purple-100 text-purple-800",
  ACKNOWLEDGED: "bg-green-100 text-green-800",
  REJECTED: "bg-red-100 text-red-800",
};

export function CoafPanel({ caseId, partyId, dossierId }: CoafPanelProps) {
  const { actor } = useDevActor();
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = React.useState<string | null>(null);

  // Restrict access
  if (actor.role !== "ANALYST" && actor.role !== "APPROVER") {
    return null; // COAF section hidden for non-authorized roles
  }

  const listQuery = useQuery({ queryKey: ["coaf", caseId], queryFn: () => listCoafCommunications(caseId) });
  const eventsQuery = useQuery({
    queryKey: ["coaf-events", selectedId],
    queryFn: () => getCoafEvents(caseId, selectedId!),
    enabled: Boolean(selectedId),
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ["coaf", caseId] });

  const createMutation = useMutation({
    mutationFn: () => createCoafDraft(caseId, {
      partyId,
      dossierId: dossierId ?? undefined,
      operationType: "PIX_SUSPICIOUS",
      narrative: "Comunicação referente a operação suspeita identificada por regras transacionais.",
      legalFramework: "Art. 10, Circular BACEN 3.978/2020",
    }, actor),
    onSuccess: (data) => { refresh(); setSelectedId(data.communicationId); },
  });

  const submitForReviewMutation = useMutation({
    mutationFn: (id: string) => submitCoafForReview(caseId, id, actor),
    onSuccess: refresh,
  });

  const approveMutation = useMutation({
    mutationFn: (id: string) => approveCoaf(caseId, id, actor),
    onSuccess: refresh,
  });

  const submitMutation = useMutation({
    mutationFn: (id: string) => submitCoaf(caseId, id, actor),
    onSuccess: refresh,
  });

  const communications = listQuery.data ?? [];
  const selected = communications.find((c) => c.communicationId === selectedId);

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-sm">Comunicação COAF</CardTitle>
          <Button size="sm" onClick={() => createMutation.mutate()} disabled={createMutation.isPending}>
            Criar draft
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {communications.length === 0 && <p className="text-xs text-muted-foreground">Nenhuma comunicação.</p>}
        {communications.map((c) => (
          <button
            key={c.communicationId}
            onClick={() => setSelectedId(c.communicationId)}
            className={`w-full rounded border p-2 text-left text-xs ${selectedId === c.communicationId ? "ring-2 ring-primary" : ""}`}
          >
            <div className="flex items-center justify-between">
              <span className="font-mono">{c.communicationId.slice(0, 15)}</span>
              <span className={`rounded px-1.5 py-0.5 ${statusColors[c.status] ?? "bg-muted"}`}>{c.status}</span>
            </div>
            {c.protocolNumber && <div className="mt-1 font-mono text-green-700">{c.protocolNumber}</div>}
            {c.deadlineDays && c.deadlineStart && (
              <div className="mt-0.5 text-muted-foreground">Prazo: {c.deadlineDays} dias úteis</div>
            )}
          </button>
        ))}

        {selected && (
          <div className="rounded border p-3 text-xs space-y-2">
            <div className="font-medium">Detalhes</div>
            {selected.narrative && <p className="text-muted-foreground">{selected.narrative}</p>}
            {selected.legalFramework && <p className="italic">{selected.legalFramework}</p>}
            {selected.rejectionReason && (
              <div className="rounded bg-red-50 p-2 text-red-700">{selected.rejectionReason}</div>
            )}
            <div className="flex flex-wrap gap-1.5">
              {selected.status === "DRAFT" && (
                <Button size="sm" variant="outline" onClick={() => submitForReviewMutation.mutate(selected.communicationId)}>
                  Submeter para revisão
                </Button>
              )}
              {selected.status === "PENDING_REVIEW" && (
                <Button size="sm" variant="outline" onClick={() => approveMutation.mutate(selected.communicationId)}>
                  Aprovar
                </Button>
              )}
              {selected.status === "APPROVED" && (
                <Button size="sm" onClick={() => submitMutation.mutate(selected.communicationId)}>
                  Enviar ao COAF
                </Button>
              )}
            </div>

            {eventsQuery.data && eventsQuery.data.length > 0 && (
              <div className="mt-2">
                <div className="text-[10px] font-medium text-muted-foreground uppercase">Eventos</div>
                <div className="mt-1 space-y-0.5">
                  {eventsQuery.data.map((ev, i) => (
                    <div key={i} className="flex items-center gap-2 text-[10px]">
                      <span className="font-mono">{ev.eventType}</span>
                      <span className="text-muted-foreground">{ev.actorId}</span>
                      <span className="ml-auto text-muted-foreground">{new Date(ev.occurredAt).toLocaleString("pt-BR")}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
