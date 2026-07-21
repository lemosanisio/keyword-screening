import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createTransactionCaseScenario, getCases } from "@/api/cases";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { QueueTemplate } from "@/design/templates/queue-template";
import { useDevActor } from "@/features/auth-dev/dev-actor";

export function QueuePage() {
  const { actor } = useDevActor();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ["cases"], queryFn: getCases });
  const scenario = useMutation({
    mutationFn: () => createTransactionCaseScenario(actor),
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
      <div className="flex items-center justify-between gap-3 rounded-lg border bg-card p-3">
        <div>
          <div className="text-sm font-medium">Cenário demonstrável</div>
          <div className="text-xs text-muted-foreground">Cria Party, ciclo de análise, sinal transacional e caso aberto.</div>
        </div>
        <Button size="sm" disabled={scenario.isPending} onClick={() => scenario.mutate()}>
          Criar caso demo
        </Button>
      </div>
      {scenario.isError && <Alert variant="destructive">Não foi possível criar o cenário demo.</Alert>}
      <QueueTemplate cases={query.data.cases} />
    </div>
  );
}
