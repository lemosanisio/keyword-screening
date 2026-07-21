# Marco 2 — case transacional mínimo

Status: concluído  
Escopo: iniciar a fila humana única com casos transacionais derivados dos sinais já consumidos pelo `pld-customer-analysis`.

## Concluído

- [x] Configuração de Java 21 via `mise` no repositório.
- [x] Schema inicial de `pld_case` e `case_source`.
- [x] Criação idempotente de caso aberto a partir de `TransactionSignalDetected.v1` com rota humana.
- [x] Agrupamento de novos sinais transacionais no caso aberto da mesma `Party` pela política `transaction-alert-grouping-1`.
- [x] Timeline `CASE_CREATED` e outbox `CaseStatusChanged` na criação de caso.
- [x] API mínima de fila: `GET /v1/cases`.
- [x] API mínima de detalhe: `GET /v1/cases/{caseId}` com Party, fontes anexadas e timeline.
- [x] Projeção mínima de sinal transacional no workspace: avaliação, transação, tipo, rota, versão de risco e regras acionadas.
- [x] Ações disponíveis no workspace conforme status.
- [x] Transições operacionais `ASSIGN`, `START_ANALYSIS` e `RETURN_TO_QUEUE` com versionamento simples.
- [x] Timeline e outbox `CaseStatusChanged.v1` nas transições operacionais.

## Encerramento

- Marco 2 fechado como caso transacional mínimo. Próximo marco deve iniciar comentários/anexos ou decisão humana, conforme prioridade de produto.

## Fora deste corte inicial

- Comentários, anexos e solicitação de informação.
- Decisão de relacionamento/suspeição.
- Dossiê e COAF.
