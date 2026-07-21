const apiBaseUrl = process.env.PLD_API_BASE_URL ?? "http://localhost:8082";
const scenario = process.env.PLD_SCENARIO ?? "CLEAR";

const response = await fetch(`${apiBaseUrl}/v1/dev/scenarios/transaction-case`, {
  method: "POST",
  headers: {
    "Accept": "application/json",
    "Content-Type": "application/json",
    "X-Actor-Id": "scenario-cli",
    "X-Actor-Role": "SYSTEM",
    "X-Correlation-Id": `seed-${Date.now()}`
  },
  body: JSON.stringify({ scenario })
});

if (!response.ok) {
  throw new Error(`Scenario failed with HTTP ${response.status}`);
}

console.log(await response.json());
