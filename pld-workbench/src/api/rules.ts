import type { DevActor } from "./types";
import { correlationId } from "@/lib/correlation";

const RULES_API_BASE_URL = (globalThis as { PLD_RULES_API_BASE_URL?: string }).PLD_RULES_API_BASE_URL ?? "http://localhost:8080";

async function rulesRequest<T>(path: string, options: RequestInit & { actor?: DevActor } = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (options.actor) {
    headers.set("X-Actor-Id", options.actor.id);
    headers.set("X-Actor-Role", options.actor.role);
  }
  headers.set("X-Correlation-Id", correlationId());

  const response = await fetch(`${RULES_API_BASE_URL}${path}`, { ...options, headers });
  if (!response.ok) {
    throw new Error(`Falha HTTP ${response.status} em ${path}`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

// --- Types ---

export type RuleDefinitionView = {
  id: string;
  code: string;
  name: string;
  description: string;
  context: string;
  category: string;
  supportedFacts: string[];
  supportedActions: string[];
  status: string;
  createdAt: string;
};

export type ExpressionView = {
  type: string;
  factName?: string;
  operator?: string;
  expectedValue?: unknown;
  logicalOperator?: string;
  expressions?: ExpressionView[];
};

export type RuleConfigurationView = {
  id: string;
  ruleCode: string;
  expressions: ExpressionView[];
  actions: string[];
  active: boolean;
  draft: boolean;
  currentVersion: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
};

export type ConfigVersionView = {
  version: number;
  expressions: ExpressionView[];
  actions: string[];
  active: boolean;
  createdBy: string;
  createdAt: string;
};

export type DryRunResult = {
  decision: string;
  actions: string[];
  matchedExpressions: Array<{ expressionId?: string; factName: string; satisfied: boolean }>;
  failedExpressions: Array<{ expressionId?: string; factName: string; satisfied: boolean }>;
  configurationVersion: number;
};

export type FactDefinitionView = {
  id: string;
  name: string;
  type: string;
  description: string;
  entity: string;
  supportedOperators: string[];
  enabled: boolean;
};

// --- API calls ---

export function listRules() {
  return rulesRequest<RuleDefinitionView[]>("/v1/decision/rules");
}

export function getRule(code: string) {
  return rulesRequest<RuleDefinitionView>(`/v1/decision/rules/${code}`);
}

export function createRule(body: { code: string; name: string; description: string; context: string; category: string; supportedFacts: string[]; supportedActions: string[] }) {
  return rulesRequest<RuleDefinitionView>("/v1/decision/rules", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function listFacts() {
  return rulesRequest<FactDefinitionView[]>("/v1/decision/facts");
}

export function listConfigurations(ruleCode: string) {
  return rulesRequest<RuleConfigurationView[]>(`/v1/decision/rules/${ruleCode}/configurations`);
}

export function getConfiguration(configId: string) {
  return rulesRequest<RuleConfigurationView>(`/v1/decision/rule-configurations/${configId}`);
}

export function createConfiguration(ruleCode: string, body: { expressions: ExpressionView[]; actions: string[]; createdBy: string }) {
  return rulesRequest<RuleConfigurationView>(`/v1/decision/rules/${ruleCode}/configurations`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function activateConfiguration(configId: string) {
  return rulesRequest<RuleConfigurationView>(`/v1/decision/rule-configurations/${configId}/activate`, {
    method: "POST",
  });
}

export function deactivateConfiguration(configId: string) {
  return rulesRequest<RuleConfigurationView>(`/v1/decision/rule-configurations/${configId}/deactivate`, {
    method: "POST",
  });
}

export function getConfigurationVersions(configId: string) {
  return rulesRequest<ConfigVersionView[]>(`/v1/decision/rule-configurations/${configId}/versions`);
}

export function executeDryRun(configId: string, body: { facts: Record<string, unknown>; executedBy: string }) {
  return rulesRequest<DryRunResult>(`/v1/decision/rule-configurations/${configId}/dry-run`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}
