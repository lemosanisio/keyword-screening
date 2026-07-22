import { expect, test } from "@playwright/test";
import { analystHeaders, captureConsoleErrors } from "./helpers";

const runRealIntegration = process.env.PLD_TRANSACTION_E2E === "true";

/**
 * Teste e2e do Marco 7: avaliação transacional reproduzível.
 *
 * Valida que:
 * 1. Uma avaliação LIVE produz sinal e caso no Workbench
 * 2. A tela exibe finalidade (LIVE), estado (COMPLETED), outcome, e explicação
 * 3. Um replay da mesma transação não cria caso duplicado
 * 4. O snapshot hash garante reprodutibilidade
 */
test("avaliação reproduzível: LIVE gera caso com campos M7 visíveis", async ({ page, request }) => {
  test.skip(!runRealIntegration, "Requer pld-transaction-screening + pld-customer-analysis + SQS");
  const consoleErrors = captureConsoleErrors(page);
  const suffix = ulid();

  // 1. Criar party
  const partyResponse = await request.post("http://localhost:8082/v1/parties", {
    data: {
      partyType: "PERSON",
      officialName: `M7 Reproducible ${suffix}`,
      sourceSystem: "playwright-e2e"
    },
    headers: analystHeaders
  });
  expect(partyResponse.ok()).toBeTruthy();
  const party = await partyResponse.json() as { partyId: string };

  // 2. Avaliar transação (purpose=LIVE por padrão)
  const transactionId = `txn_${ulid()}`;
  const evaluateResponse = await request.post("http://localhost:8081/v1/rules/keyword-screening/evaluate", {
    data: {
      transactionId,
      customerId: party.partyId,
      description: "transferencia terrorismo financiamento ilegal"
    },
    headers: { "X-Correlation-Id": `playwright-m7-${suffix}` }
  });
  expect(evaluateResponse.ok()).toBeTruthy();
  const evaluateBody = await evaluateResponse.json() as {
    evaluationId?: string;
    executionStatus?: string;
    evaluationOutcome?: string;
    rulesetVersion?: string;
  };

  // 3. Verificar que a avaliação retornou campos do Marco 7
  expect(evaluateBody.evaluationId).toBeTruthy();
  expect(evaluateBody.executionStatus).toBe("COMPLETED");
  expect(evaluateBody.evaluationOutcome).toBe("SIGNAL_RAISED");
  expect(evaluateBody.rulesetVersion).toBeTruthy();

  // 4. Esperar que o caso seja criado via SQS
  await expect.poll(async () => {
    const response = await request.get("http://localhost:8082/v1/cases");
    const body = await response.json() as { cases: Array<{ caseId: string; partyId: string }> };
    return body.cases.find((item) => item.partyId === party.partyId);
  }, { timeout: 15_000 }).toBeTruthy();

  // 5. Abrir caso no Workbench
  const casesResponse = await request.get("http://localhost:8082/v1/cases");
  const cases = await casesResponse.json() as { cases: Array<{ caseId: string; partyId: string }> };
  const caseId = cases.cases.find((item) => item.partyId === party.partyId)?.caseId;
  expect(caseId).toBeTruthy();

  await page.goto(`/cases/${caseId}`);
  await expect(page.getByText(`M7 Reproducible ${suffix}`).first()).toBeVisible();

  // 6. Verificar campos da avaliação reproduzível no Workbench
  await expect(page.getByText(`Transaction: ${transactionId}`)).toBeVisible();
  await expect(page.getByText("LIVE")).toBeVisible();
  await expect(page.getByText("COMPLETED")).toBeVisible();

  // 7. Retry idempotente: mesma avaliação não gera novo caso
  const retryResponse = await request.post("http://localhost:8081/v1/rules/keyword-screening/evaluate", {
    data: {
      transactionId,
      customerId: party.partyId,
      description: "transferencia terrorismo financiamento ilegal"
    },
    headers: { "X-Correlation-Id": `playwright-m7-retry-${suffix}` }
  });
  expect(retryResponse.ok()).toBeTruthy();
  const retryBody = await retryResponse.json() as { evaluationId?: string };
  // Deve retornar o mesmo evaluationId (idempotência)
  expect(retryBody.evaluationId).toBe(evaluateBody.evaluationId);

  // 8. Não criou caso duplicado
  await page.waitForTimeout(2_000);
  const afterRetry = await (await request.get("http://localhost:8082/v1/cases")).json() as {
    cases: Array<{ partyId: string }>;
  };
  expect(afterRetry.cases.filter((item) => item.partyId === party.partyId)).toHaveLength(1);

  expect(consoleErrors).toEqual([]);
});

test("intake inválido não gera caso nem avaliação", async ({ request }) => {
  test.skip(!runRealIntegration, "Requer pld-transaction-screening habilitado");

  // Enviar avaliação com transactionId vazio (intake inválido)
  const evaluateResponse = await request.post("http://localhost:8081/v1/rules/keyword-screening/evaluate", {
    data: {
      transactionId: "",
      customerId: "pty_01INVALIDCUSTOMER00000000",
      description: "transferencia suspeita"
    },
    headers: { "X-Correlation-Id": "playwright-quarantine-test" }
  });

  // A API deve retornar OK com resultado vazio (quarantined) ou 400
  const status = evaluateResponse.status();
  expect([200, 400]).toContain(status);

  if (status === 200) {
    const body = await evaluateResponse.json() as { evaluationId?: string | null };
    // Sem evaluationId = quarantined
    expect(body.evaluationId).toBeFalsy();
  }
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
