import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as React from "react";
import { useParams } from "react-router-dom";
import {
  addComment,
  approveDecision,
  assignCase,
  getCase,
  issueAccountDecision,
  issueSuspicionDecision,
  returnToQueue,
  startAnalysis
} from "@/api/cases";
import { ApiConflictError } from "@/api/http";
import type { DecisionCommand } from "@/api/types";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { CaseWorkspaceTemplate } from "@/design/templates/case-workspace-template";
import { useDevActor } from "@/features/auth-dev/dev-actor";

export function CaseWorkspacePage() {
  const { caseId } = useParams();
  const { actor } = useDevActor();
  const queryClient = useQueryClient();
  const [conflict, setConflict] = React.useState<string | null>(null);

  const query = useQuery({
    queryKey: ["case", caseId],
    queryFn: () => getCase(caseId ?? ""),
    enabled: Boolean(caseId)
  });

  const refresh = async () => {
    setConflict(null);
    await queryClient.invalidateQueries({ queryKey: ["case", caseId] });
    await queryClient.invalidateQueries({ queryKey: ["cases"] });
  };

  const mutate = useMutation({
    mutationFn: async (operation: () => Promise<unknown>) => operation(),
    onSuccess: refresh,
    onError: (error) => {
      if (error instanceof ApiConflictError) {
        setConflict(error.message);
        return;
      }
      setConflict(error instanceof Error ? error.message : "Falha inesperada no comando.");
    }
  });

  if (!caseId) {
    return <Alert variant="destructive">Caso inválido.</Alert>;
  }
  if (query.isLoading) {
    return <div className="rounded-lg border bg-card p-6 text-sm text-muted-foreground">Carregando caso...</div>;
  }
  if (query.isError) {
    return <Alert variant="destructive">Não foi possível carregar o caso.</Alert>;
  }

  const detail = query.data;
  const version = detail.case.version;

  return (
    <div className="space-y-3">
      {conflict && <Button size="sm" variant="outline" onClick={refresh}>Recarregar dados do caso</Button>}
      <CaseWorkspaceTemplate
        detail={detail}
        conflict={conflict}
        busy={mutate.isPending}
        onAssign={() => mutate.mutate(() => assignCase(caseId, version, actor))}
        onStartAnalysis={() => mutate.mutate(() => startAnalysis(caseId, version, actor))}
        onReturnToQueue={() => mutate.mutate(() => returnToQueue(caseId, version, actor))}
        onApprove={() => mutate.mutate(() => approveDecision(caseId, version, actor))}
        onComment={(body) => mutate.mutate(() => addComment(caseId, body, actor))}
        onAccountDecision={(command: DecisionCommand) => mutate.mutate(() => issueAccountDecision(caseId, command, actor))}
        onSuspicionDecision={(command: DecisionCommand) => mutate.mutate(() => issueSuspicionDecision(caseId, command, actor))}
      />
    </div>
  );
}
