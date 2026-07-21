import { expect, type APIRequestContext, type Page } from "@playwright/test";

const backendUrl = process.env.PLD_API_BASE_URL ?? "http://localhost:8082";

export const analystHeaders = {
  "X-Actor-Id": "analyst-1",
  "X-Actor-Role": "ANALYST",
  "X-Correlation-Id": "playwright-e2e"
};

export async function createScenario(request: APIRequestContext, scenario: "CLEAR" | "SOURCE_UNAVAILABLE" | "RISK_CONTEXT") {
  const response = await request.post(`${backendUrl}/v1/dev/scenarios/transaction-case`, {
    data: { scenario },
    headers: analystHeaders
  });
  expect(response.ok()).toBeTruthy();
  return response.json() as Promise<{ caseId: string; partyId: string }>;
}

export function captureConsoleErrors(page: Page) {
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  return errors;
}

export async function assumeAndStart(page: Page) {
  await page.getByRole("button", { name: "Assumir" }).click();
  await page.getByRole("button", { name: "Iniciar análise" }).click();
  await expect(page.getByText("IN_ANALYSIS", { exact: true })).toBeVisible();
}
