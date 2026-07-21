import { expect, test } from "@playwright/test";
import { analystHeaders, captureConsoleErrors } from "./helpers";

const runRealIntegration = process.env.PLD_TRANSACTION_E2E === "true";

test("sinal real do motor aparece uma vez no Workbench", async ({ page, request }) => {
  test.skip(!runRealIntegration, "Requer pld-transaction-screening e integração SQS habilitados");
  const consoleErrors = captureConsoleErrors(page);
  const suffix = ulid();
  const partyResponse = await request.post("http://localhost:8082/v1/parties", {
    data: {
      partyType: "PERSON",
      officialName: `Playwright Transaction ${suffix}`,
      sourceSystem: "playwright-e2e"
    },
    headers: analystHeaders
  });
  expect(partyResponse.ok()).toBeTruthy();
  const party = await partyResponse.json() as { partyId: string };
  const transactionId = `txn_${ulid()}`;
  const evaluate = () => request.post("http://localhost:8081/v1/rules/keyword-screening/evaluate", {
    data: {
      transactionId,
      customerId: party.partyId,
      description: "pagamento relacionado a lavagem de dinheiro"
    },
    headers: { "X-Correlation-Id": `playwright-${suffix}` }
  });

  expect((await evaluate()).ok()).toBeTruthy();
  await expect.poll(async () => {
    const response = await request.get("http://localhost:8082/v1/cases");
    const body = await response.json() as { cases: Array<{ caseId: string; partyId: string }> };
    return body.cases.find((item) => item.partyId === party.partyId);
  }, { timeout: 15_000 }).toBeTruthy();

  const casesResponse = await request.get("http://localhost:8082/v1/cases");
  const cases = await casesResponse.json() as { cases: Array<{ caseId: string; partyId: string }> };
  const caseId = cases.cases.find((item) => item.partyId === party.partyId)?.caseId;
  expect(caseId).toBeTruthy();
  await page.goto(`/cases/${caseId}`);
  await expect(page.getByText(`Playwright Transaction ${suffix}`, { exact: true }).first()).toBeVisible();
  await expect(page.getByText(`Transaction: ${transactionId}`, { exact: true })).toBeVisible();
  await expect(page.getByText("KEYWORD_SCREENING v1", { exact: true })).toBeVisible();
  await expect(page.getByText("Sem matriz de evidências para este caso.", { exact: true })).toBeVisible();

  expect((await evaluate()).ok()).toBeTruthy();
  await page.waitForTimeout(2_000);
  const afterRetry = await (await request.get("http://localhost:8082/v1/cases")).json() as {
    cases: Array<{ partyId: string }>;
  };
  expect(afterRetry.cases.filter((item) => item.partyId === party.partyId)).toHaveLength(1);
  expect(consoleErrors).toEqual([]);
});

function ulid() {
  const alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  let time = Date.now();
  const value = Array.from({ length: 26 }, () => alphabet[Math.floor(Math.random() * alphabet.length)]);
  for (let index = 9; index >= 0; index--) {
    value[index] = alphabet[time & 31];
    time = Math.floor(time / 32);
  }
  return value.join("");
}
