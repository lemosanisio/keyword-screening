# Marco 2 — case transacional mínimo

Status: em andamento  
Escopo: iniciar a fila humana única com casos transacionais derivados dos sinais já consumidos pelo `pld-customer-analysis`.

## Concluído

- [x] Configuração de Java 21 via `mise` no repositório.
- [x] Schema inicial de `pld_case` e `case_source`.
- [x] Criação idempotente de caso aberto a partir de `TransactionSignalDetected.v1` com rota humana.
- [x] Agrupamento de novos sinais transacionais no caso aberto da mesma `Party` pela política `transaction-alert-grouping-1`.
- [x] Timeline `CASE_CREATED` e outbox `CaseStatusChanged` na criação de caso.
- [x] API mínima de fila: `GET /v1/cases`.
- [x] API mínima de detalhe: `GET /v1/cases/{caseId}` com Party, fontes anexadas e timeline.

## Próximo corte

- [ ] Persistir/projetar detalhes mínimos do sinal transacional para o workspace do caso.
- [ ] Incluir ações disponíveis no workspace conforme status/perfil.
- [ ] Implementar assumir/devolver caso (`ASSIGNED`/`OPEN`) com versionamento simples.
- [ ] Emitir `CaseStatusChanged.v1` nas transições operacionais.

## Fora deste corte inicial

- Comentários, anexos e solicitação de informação.
- Decisão de relacionamento/suspeição.
- Dossiê e COAF.
