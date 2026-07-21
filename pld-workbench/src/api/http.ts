import { correlationId } from "@/lib/correlation";
import type { DevActor } from "./types";

const API_BASE_URL = (globalThis as { PLD_API_BASE_URL?: string }).PLD_API_BASE_URL ?? "http://localhost:8080";

type RequestOptions = RequestInit & {
  actor?: DevActor;
};

export class ApiConflictError extends Error {
  constructor() {
    super("O caso mudou no servidor. Recarregue antes de continuar.");
  }
}

export async function requestJson<T>(path: string, options: RequestOptions = {}): Promise<T> {
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

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  if (response.status === 409) {
    throw new ApiConflictError();
  }
  if (!response.ok) {
    throw new Error(`Falha HTTP ${response.status} em ${path}`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}
