# Marco 0 — contratos v1 e segurança de mudança

Origem: `agent-handoff.md` (Marco 0). Saída esperada: **contratos testáveis, sem alteração de comportamento em produção.**

Item já concluído do handoff: documentos adicionados ao workspace ✔ (2026-07-20). Convenções de IDs, classificação e single-tenant ✔ (ADR-005). Broker ✔ (ADR-007).

## M0.1 — Catálogo de schemas `v1`

Local: `pld-platform-docs/schemas/v1/`. JSON Schema draft 2020-12 (ADR-005). Cada evento referencia o envelope comum.

- [x] M0.1.1 `common/envelope.schema.json` + tipos compartilhados (`actor`, `subject`, `dataClassification`; `money`/`factValue` entram quando um evento precisar — YAGNI)
- [x] M0.1.2 Eventos de entrada (produtor: sistemas mestres): `CustomerOnboardingStarted`, `CustomerDataChanged`, `PartyRelationshipChanged`, `AccountStatusChanged`, `TransactionOccurred` — schemas são proposta de contrato ao sistema mestre (EXT-2)
- [x] M0.1.3 Eventos de `pld-customer-analysis`: `CustomerRiskProfileUpdated`, `AnalysisCycleStatusChanged`, `CaseStatusChanged`, `AccountDecisionIssued`, `SuspicionDecisionIssued`, `CoafCommunicationStatusChanged`
- [x] M0.1.4 Eventos de `pld-transaction-screening`: `TransactionEvaluationCompleted`, `TransactionSignalDetected`, `ManualReviewRequested`, `TransactionDecisionExecutionCompleted`, `RuleConfigurationActivated`
- [x] M0.1.5 Fixture dourada por evento em `fixtures/` (16/16) — sem PII, `dataClassification` preenchida, IDs no padrão ADR-005

## M0.2 — Convenções normativas restantes

- [x] M0.2.1 Propagação de correlação ponta a ponta: HTTP (`Idempotency-Key`, header de correlação) → envelope (`correlationId`, `causationId`) → logs estruturados. Registrado em `shared/integration-contracts.md`
- [x] M0.2.2 Confirmar UTC/ISO-8601 e moeda — congelado v1 em `shared/integration-contracts.md`

## M0.3 — Testes de contrato

Primeiro código do Marco 0. TDD: teste primeiro, sempre.

- [x] M0.3.1 Harness fixture↔schema rodando em `pld-transaction-screening` (scan parametrizado de `fixtures/`): falha se qualquer fixture violar seu schema
- [x] M0.3.2 Tolerância a campo desconhecido: fixture com campo extra não falha a validação de consumidor (regra já prevista nos contratos)
- [ ] M0.3.3 (Quando houver produtor real, em Marcos seguintes) evento emitido valida contra schema antes de publicar

## M0.4 — Mapa de compatibilidade das APIs atuais

- [x] M0.4.1 Inventário dos endpoints atuais de `pld-transaction-screening` (fonte: `src/main/resources/static/openapi/openapi.yaml`): o que permanece compatível durante a migração, o que vira legado (workflow de `Alert`), janela de convivência. Saída: `pld-transaction-screening/docs/pld-expansion/api-compatibility-map.md`

## Definition of Done do Marco 0

- [x] 16 eventos + envelope com schema e fixture válidos
- [x] Harness de validação verde em CI local (`./gradlew test`)
- [x] Mapa de compatibilidade revisado e commitado
- [x] Nenhuma mudança de comportamento em produção
