import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as React from "react";
import { createTransactionCaseScenario, getCases } from "@/api/cases";
import type { EvidenceScenario } from "@/api/types";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/select";
import { QueueTemplate } from "@/design/templates/queue-template";
import { useDevActor } from "@/features/auth-dev/dev-actor";

export function QueuePage() {
  const { actor } = useDevActor();
  const [scenario, setScenario] = React.useState<EvidenceScenario>("CLEAR");
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ["cases"], queryFn: getCases });
  const scenarioMutation = useMutation({
    mutationFn: () => createTransactionCaseScenario(actor, scenario),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["cases"] })
  });

  if (query.isLoading) {
    return <div className="rounded-lg border bg-card p-6 text-sm text-muted-foreground">Carregando fila...</div>;
  }
  if (query.isError) {
    return <Alert variant="destructive">Não foi possível carregar a fila. Verifique se o backend está rodando.</Alert>;
  }
  if (!query.data) {
    return <Alert variant="destructive">A fila não retornou dados.</Alert>;
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-col gap-3 rounded-lg border bg-card p-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="text-sm font-medium">Cenário demonstrável</div>
          <div className="text-xs text-muted-foreground">Cria Party, ciclo de análise, sinal transacional e caso aberto.</div>
        </div>
        <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row sm:items-center">
          <Select aria-label="Cenário demonstrável" className="w-full sm:w-56" value={scenario} onChange={(event) => setScenario(event.target.value as EvidenceScenario)}>
            <option value="CLEAR">CLEAR</option>
            <option value="SOURCE_UNAVAILABLE">SOURCE_UNAVAILABLE</option>
            <option value="RISK_CONTEXT">RISK_CONTEXT</option>
          </Select>
          <Button className="w-full sm:w-auto" size="sm" disabled={scenarioMutation.isPending} onClick={() => scenarioMutation.mutate()}>
            Criar caso demo
          </Button>
        </div>
      </div>
      {scenarioMutation.isError && <Alert variant="destructive">Não foi possível criar o cenário demo.</Alert>}
      <QueueTemplate cases={query.data.cases} />
    </div>
  );
}
