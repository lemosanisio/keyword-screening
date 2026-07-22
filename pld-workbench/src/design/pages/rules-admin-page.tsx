import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as React from "react";
import { activateConfiguration, createConfiguration, createRule, deactivateConfiguration, executeDryRun, getConfigurationVersions, listConfigurations, listFacts, listRules } from "@/api/rules";
import type { DryRunResult, ExpressionView, FactDefinitionView, RuleConfigurationView, RuleDefinitionView } from "@/api/rules";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useDevActor } from "@/features/auth-dev/dev-actor";

export function RulesAdminPage() {
  const { actor } = useDevActor();
  const [selectedRule, setSelectedRule] = React.useState<string | null>(null);
  const [selectedConfig, setSelectedConfig] = React.useState<string | null>(null);
  const [showCreateForm, setShowCreateForm] = React.useState(false);

  const queryClient = useQueryClient();
  const rulesQuery = useQuery({ queryKey: ["rules"], queryFn: listRules });
  const factsQuery = useQuery({ queryKey: ["facts"], queryFn: listFacts });

  const createRuleMutation = useMutation({
    mutationFn: (body: { code: string; name: string; description: string; context: string; category: string; supportedFacts: string[]; supportedActions: string[] }) => createRule(body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["rules"] }); setShowCreateForm(false); },
  });

  if (actor.role !== "RULE_ADMIN") {
    return <Alert variant="destructive">Acesso restrito a RULE_ADMIN.</Alert>;
  }

  if (rulesQuery.isLoading) return <div className="text-sm text-muted-foreground">Carregando regras...</div>;
  if (rulesQuery.isError) return <Alert variant="destructive">Erro ao carregar regras.</Alert>;

  return (
    <div className="space-y-4">
      <h1 className="text-lg font-semibold">Administração de Regras</h1>
      <div className="grid gap-4 lg:grid-cols-[300px_1fr]">
        <aside className="space-y-2">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="text-sm">Catálogo de Regras</CardTitle>
                <Button size="sm" variant="outline" onClick={() => setShowCreateForm(!showCreateForm)}>
                  + Nova
                </Button>
              </div>
            </CardHeader>
            <CardContent className="space-y-1">
              {showCreateForm && (
                <CreateRuleForm
                  onSubmit={(data) => createRuleMutation.mutate(data)}
                  busy={createRuleMutation.isPending}
                  onCancel={() => setShowCreateForm(false)}
                />
              )}
              {rulesQuery.data?.map((rule) => (
                <button
                  key={rule.code}
                  onClick={() => { setSelectedRule(rule.code); setSelectedConfig(null); }}
                  className={`w-full rounded px-2 py-1.5 text-left text-sm hover:bg-muted ${selectedRule === rule.code ? "bg-muted font-medium" : ""}`}
                >
                  <div>{rule.code}</div>
                  <div className="text-xs text-muted-foreground">{rule.name}</div>
                </button>
              ))}
            </CardContent>
          </Card>
        </aside>
        <section>
          {selectedRule && (
            <RuleDetail
              ruleCode={selectedRule}
              facts={factsQuery.data ?? []}
              selectedConfig={selectedConfig}
              onSelectConfig={setSelectedConfig}
              actorEmail={actor.id}
            />
          )}
          {!selectedRule && (
            <div className="rounded-lg border bg-card p-6 text-sm text-muted-foreground">
              Selecione uma regra no catálogo para gerenciar configurações.
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

function RuleDetail({ ruleCode, facts, selectedConfig, onSelectConfig, actorEmail }: {
  ruleCode: string;
  facts: FactDefinitionView[];
  selectedConfig: string | null;
  onSelectConfig: (id: string | null) => void;
  actorEmail: string;
}) {
  const queryClient = useQueryClient();
  const configsQuery = useQuery({ queryKey: ["configs", ruleCode], queryFn: () => listConfigurations(ruleCode) });

  const createMutation = useMutation({
    mutationFn: () => createConfiguration(ruleCode, {
      expressions: [{ type: "CONDITION", factName: "keywordMatched", operator: "EQUALS", expectedValue: { type: "BOOLEAN", value: true } }],
      actions: ["GENERATE_ALERT"],
      createdBy: actorEmail,
    }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["configs", ruleCode] }),
  });

  if (configsQuery.isLoading) return <div className="text-sm text-muted-foreground">Carregando configurações...</div>;

  const configs = configsQuery.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="font-medium">{ruleCode} — Configurações</h2>
        <Button size="sm" onClick={() => createMutation.mutate()} disabled={createMutation.isPending}>
          Nova configuração
        </Button>
      </div>
      {configs.length === 0 && <div className="text-sm text-muted-foreground">Nenhuma configuração encontrada.</div>}
      <div className="space-y-2">
        {configs.map((config) => (
          <Card key={config.id} className={`cursor-pointer ${selectedConfig === config.id ? "ring-2 ring-primary" : ""}`}>
            <CardContent className="p-3" onClick={() => onSelectConfig(config.id)}>
              <div className="flex items-center justify-between">
                <div className="text-sm">
                  <span className="font-mono">v{config.currentVersion}</span>
                  {config.active && <span className="ml-2 rounded bg-green-100 px-1.5 py-0.5 text-xs text-green-800 dark:bg-green-900 dark:text-green-200">active</span>}
                  {config.draft && <span className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-800 dark:bg-amber-900 dark:text-amber-200">draft</span>}
                </div>
                <span className="text-xs text-muted-foreground">{config.createdBy}</span>
              </div>
              <div className="mt-1 text-xs text-muted-foreground">
                Actions: {config.actions.join(", ")} · Expressions: {config.expressions.length}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
      {selectedConfig && <ConfigDetail configId={selectedConfig} facts={facts} actorEmail={actorEmail} ruleCode={ruleCode} />}
    </div>
  );
}

function ConfigDetail({ configId, facts, actorEmail, ruleCode }: {
  configId: string;
  facts: FactDefinitionView[];
  actorEmail: string;
  ruleCode: string;
}) {
  const queryClient = useQueryClient();
  const [dryRunResult, setDryRunResult] = React.useState<DryRunResult | null>(null);
  const [dryRunError, setDryRunError] = React.useState<string | null>(null);

  const versionsQuery = useQuery({ queryKey: ["versions", configId], queryFn: () => getConfigurationVersions(configId) });

  const activateMutation = useMutation({
    mutationFn: () => activateConfiguration(configId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["configs", ruleCode] }),
  });

  const deactivateMutation = useMutation({
    mutationFn: () => deactivateConfiguration(configId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["configs", ruleCode] }),
  });

  const dryRunMutation = useMutation({
    mutationFn: () => executeDryRun(configId, {
      facts: { keywordMatched: { type: "BOOLEAN", value: true }, customerRisk: { type: "ENUM", value: "AR" } },
      executedBy: actorEmail,
    }),
    onSuccess: (data) => { setDryRunResult(data); setDryRunError(null); },
    onError: (err) => { setDryRunError(err instanceof Error ? err.message : "Erro"); setDryRunResult(null); },
  });

  return (
    <Card className="mt-4">
      <CardHeader>
        <CardTitle className="text-sm">Detalhe da configuração</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" onClick={() => dryRunMutation.mutate()} disabled={dryRunMutation.isPending}>
            Executar Dry-Run
          </Button>
          <Button size="sm" onClick={() => activateMutation.mutate()} disabled={activateMutation.isPending}>
            Ativar
          </Button>
          <Button size="sm" variant="secondary" onClick={() => deactivateMutation.mutate()} disabled={deactivateMutation.isPending}>
            Desativar
          </Button>
        </div>

        {dryRunResult && (
          <div className="rounded border p-3 text-sm">
            <div className="font-medium">Dry-Run: <span className={dryRunResult.decision === "ALERT" ? "text-red-600" : "text-green-600"}>{dryRunResult.decision}</span></div>
            <div className="mt-1 text-xs text-muted-foreground">
              Actions: {dryRunResult.actions.join(", ") || "nenhuma"}
            </div>
            <div className="mt-1 text-xs">
              Matched: {dryRunResult.matchedExpressions.map((e) => e.factName).join(", ") || "—"}
            </div>
            <div className="text-xs">
              Failed: {dryRunResult.failedExpressions.map((e) => e.factName).join(", ") || "—"}
            </div>
          </div>
        )}
        {dryRunError && <Alert variant="destructive">{dryRunError}</Alert>}

        {versionsQuery.data && versionsQuery.data.length > 0 && (
          <div>
            <h4 className="text-xs font-medium text-muted-foreground">Histórico de versões</h4>
            <div className="mt-1 space-y-1">
              {versionsQuery.data.map((v) => (
                <div key={v.version} className="flex items-center justify-between rounded border px-2 py-1 text-xs">
                  <span>v{v.version} · {v.expressions.length} expr · {v.actions.join(", ")}</span>
                  <span className="text-muted-foreground">{v.createdBy} · {new Date(v.createdAt).toLocaleDateString("pt-BR")}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}



function CreateRuleForm({ onSubmit, busy, onCancel }: {
  onSubmit: (data: { code: string; name: string; description: string; context: string; category: string; supportedFacts: string[]; supportedActions: string[] }) => void;
  busy: boolean;
  onCancel: () => void;
}) {
  const [code, setCode] = React.useState("");
  const [name, setName] = React.useState("");
  const [description, setDescription] = React.useState("");
  const [context, setContext] = React.useState("SCREENING");
  const [category, setCategory] = React.useState("KEYWORD_SCREENING");
  const [facts, setFacts] = React.useState("keywordMatched, customerRisk");
  const [actions, setActions] = React.useState("GENERATE_ALERT, IGNORE");

  return (
    <div className="rounded-lg border bg-muted/30 p-3 space-y-2 mb-2">
      <div className="text-xs font-medium">Nova Regra</div>
      <input
        className="w-full rounded border px-2 py-1 text-xs"
        placeholder="Código (ex: AML_DETECTION)"
        value={code}
        onChange={(e) => setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, ""))}
      />
      <input
        className="w-full rounded border px-2 py-1 text-xs"
        placeholder="Nome"
        value={name}
        onChange={(e) => setName(e.target.value)}
      />
      <textarea
        className="w-full rounded border px-2 py-1 text-xs"
        placeholder="Descrição"
        rows={2}
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
      <select className="w-full rounded border px-2 py-1 text-xs" value={context} onChange={(e) => setContext(e.target.value)}>
        <option value="SCREENING">SCREENING</option>
        <option value="MONITORING">MONITORING</option>
        <option value="COMPLIANCE">COMPLIANCE</option>
      </select>
      <select className="w-full rounded border px-2 py-1 text-xs" value={category} onChange={(e) => setCategory(e.target.value)}>
        <option value="KEYWORD_SCREENING">KEYWORD_SCREENING</option>
        <option value="TRANSACTION_PATTERN">TRANSACTION_PATTERN</option>
        <option value="CUSTOMER_BEHAVIOR">CUSTOMER_BEHAVIOR</option>
        <option value="SANCTIONS">SANCTIONS</option>
        <option value="AML">AML</option>
      </select>
      <input
        className="w-full rounded border px-2 py-1 text-xs"
        placeholder="Facts suportados (separados por vírgula)"
        value={facts}
        onChange={(e) => setFacts(e.target.value)}
      />
      <input
        className="w-full rounded border px-2 py-1 text-xs"
        placeholder="Ações (separadas por vírgula)"
        value={actions}
        onChange={(e) => setActions(e.target.value)}
      />
      <div className="flex gap-2">
        <Button size="sm" onClick={() => onSubmit({
          code,
          name,
          description,
          context,
          category,
          supportedFacts: facts.split(",").map((f) => f.trim()).filter(Boolean),
          supportedActions: actions.split(",").map((a) => a.trim()).filter(Boolean),
        })} disabled={busy || !code || !name}>
          Criar
        </Button>
        <Button size="sm" variant="outline" onClick={onCancel}>Cancelar</Button>
      </div>
    </div>
  );
}
