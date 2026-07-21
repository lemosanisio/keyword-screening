const apiBaseUrl = process.env.PLD_API_BASE_URL ?? "http://localhost:8080";

const response = await fetch(`${apiBaseUrl}/v1/dev/scenarios/transaction-case`, {
  method: "POST",
  headers: {
    "Accept": "application/json",
    "X-Actor-Id": "scenario-cli",
    "X-Actor-Role": "SYSTEM",
    "X-Correlation-Id": `seed-${Date.now()}`
  }
});

if (!response.ok) {
  throw new Error(`Scenario failed with HTTP ${response.status}`);
}

console.log(await response.json());
