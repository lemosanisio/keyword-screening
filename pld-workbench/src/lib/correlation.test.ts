import { expect, test } from "bun:test";
import { correlationId } from "./correlation";

test("generates UI correlation ids", () => {
  expect(correlationId()).toStartWith("ui-");
});
