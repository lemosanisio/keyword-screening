import { expect, test } from "@playwright/test";
import { assumeAndStart, captureConsoleErrors, createScenario } from "./helpers";

test("fonte indisponível bloqueia decisão, retry libera e caso é concluído", async ({ page }) => {
  const consoleErrors = captureConsoleErrors(page);
  await page.goto("/queue");
  await page.getByRole("combobox", { name: "Cenário demonstrável" }).selectOption("SOURCE_UNAVAILABLE");

  const scenarioResponse = page.waitForResponse((response) =>
    response.url().endsWith("/v1/dev/scenarios/transaction-case") && response.request().method() === "POST"
  );
  await page.getByRole("button", { name: "Criar caso demo" }).click();
  const created = await (await scenarioResponse).json() as { caseId: string };

  const row = page.getByRole("row").filter({ hasText: created.caseId });
  await expect(row).toBeVisible();
  await row.getByRole("link", { name: "Abrir" }).click();

  await expect(page.getByText("TECHNICAL_PENDING", { exact: true })).toBeVisible();
  await expect(page.getByText(/Decisão bloqueada por requisitos obrigatórios/)).toBeVisible();
  await expect(page.getByRole("button", { name: "Registrar decisão de relacionamento" })).toBeDisabled();

  await page.getByRole("button", { name: "Retentar fonte" }).click();
  await expect(page.getByText("tentativa 2", { exact: true })).toBeVisible();
  await expect(page.getByText("SUCCESS_NO_RESULTS", { exact: true }).first()).toBeVisible();
  await expect(page.getByText(/Decisão bloqueada por requisitos obrigatórios/)).toHaveCount(0);

  await assumeAndStart(page);
  await page.getByRole("textbox", { name: "Narrativa da decisão de relacionamento" })
    .fill("Evidências obrigatórias revisadas após retentativa da fonte.");
  await page.getByRole("button", { name: "Registrar decisão de relacionamento" }).click();
  await expect(page.getByText("ACCOUNT_DECISION_MAINTAIN", { exact: true })).toBeVisible();

  await page.getByRole("textbox", { name: "Narrativa da decisão de suspeição" })
    .fill("Sem elementos suficientes para caracterizar suspeição após revisão.");
  await page.getByRole("button", { name: "Registrar decisão de suspeição" }).click();
  await expect(page.getByText("SUSPICION_DECISION_NO_SUSPICION", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "Concluir caso" }).click();
  await expect(page.getByText("DECIDED", { exact: true })).toBeVisible();
  await expect(page.getByText("CASE_COMPLETED", { exact: true })).toBeVisible();
  expect(consoleErrors).toEqual([]);
});

test("decisão sensível exige segundo aprovador", async ({ page, request }) => {
  const consoleErrors = captureConsoleErrors(page);
  const created = await createScenario(request, "RISK_CONTEXT");
  await page.goto(`/cases/${created.caseId}`);
  await assumeAndStart(page);

  await page.getByRole("combobox", { name: "Decisão de suspeição" }).selectOption("COMMUNICATE_TO_COAF");
  await page.getByRole("textbox", { name: "Narrativa da decisão de suspeição" })
    .fill("Contexto de risco exige comunicação após revisão das evidências.");
  await page.getByRole("button", { name: "Registrar decisão de suspeição" }).click();

  await expect(page.getByText("PENDING_APPROVAL", { exact: true }).first()).toBeVisible();
  await expect(page.getByRole("button", { name: "Aprovar decisão" })).toHaveCount(0);
  await page.getByRole("combobox", { name: "Ator dev" }).selectOption("approver-1");
  await page.getByRole("button", { name: "Aprovar decisão" }).click();

  await expect(page.getByText("SUSPICION_DECISION_APPROVED", { exact: true })).toBeVisible();
  await expect(page.getByText("APPROVED", { exact: true })).toBeVisible();
  await expect(page.getByText("IN_ANALYSIS", { exact: true }).first()).toBeVisible();
  expect(consoleErrors).toEqual([]);
});
