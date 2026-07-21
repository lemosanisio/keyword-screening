import { expect, test } from "@playwright/test";
import { captureConsoleErrors, createScenario } from "./helpers";

test("fila e workspace permanecem utilizáveis em viewport mobile", async ({ page, request }) => {
  const consoleErrors = captureConsoleErrors(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/queue");

  await expect(page.getByRole("combobox", { name: "Ator dev" })).toBeVisible();
  await expect(page.getByRole("combobox", { name: "Cenário demonstrável" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Criar caso demo" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true);

  const tableScroller = page.locator("table").locator("..");
  await tableScroller.evaluate((element) => { element.scrollLeft = element.scrollWidth; });
  await expect(page.getByRole("link", { name: "Abrir" }).last()).toBeVisible();

  const created = await createScenario(request, "CLEAR");
  await page.goto(`/cases/${created.caseId}`);
  await expect(page.getByRole("heading", { name: /Matriz de evidências/ })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Painel de decisão" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true);
  expect(consoleErrors).toEqual([]);
});
