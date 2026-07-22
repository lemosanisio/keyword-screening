# Marco 8 — Projeção local de risco, cutover v2 e administração de regras

Status: não iniciado

## Objetivo

Eliminar a dependência síncrona de REST para risco do cliente no motor transacional, completar o cutover do gatilho de caso para `ManualReviewRequested.v2`, retirar o dual-run do Alert legado e entregar a primeira tela de administração de regras no Workbench.

## Hipóteses

- Uma projeção local atualizada por evento reduz latência e elimina falha técnica na resolução do fato `customerRisk`.
- O cutover para v2 é seguro se o shadow mode não apresentou divergências significativas.
- A tela de admin permite ao analista PLD gerenciar regras sem depender de engenharia.

## Decisões do marco

- [ ] O `CustomerRiskProfileUpdated.v1` será publicado por um adapter mock no `pld-customer-analysis` com dados simulados.
- [ ] A projeção no motor transacional será um cache local (tabela `customer_risk_projection`) atualizado via inbox.
- [ ] O fact resolver `customerRisk` usará a projeção local; fallback para REST legado com quality `STALE` se projeção ausente.
- [ ] O cutover SHADOW → MANUAL_REVIEW_LIVE será um flag de configuração com deploy restart (sem runtime toggle nesta versão).
- [ ] Após cutover, o `Alert` legado deixa de ser criado para novas avaliações; alertas existentes permanecem consultáveis.
- [ ] A tela de admin consome as APIs já existentes no `pld-transaction-screening` (catálogo, config, dry-run, activate).

## Fatias de entrega

### M8.1 — Publisher mock de CustomerRiskProfileUpdated

- [ ] Criar adapter simulado no `pld-customer-analysis` que publica `CustomerRiskProfileUpdated.v1` ao criar ou atualizar Party.
- [ ] Payload segue o schema v1 existente: riskLevel, segments, transactionFacts, policyVersion, validUntil.
- [ ] Dados simulados derivados do cenário do Party (risco HIGH para nomes com "suspicious", MEDIUM padrão).
- [ ] Publicação via outbox existente no `pld-customer-analysis`.

### M8.2 — Projeção local no motor transacional

- [ ] Criar tabela `customer_risk_projection` com: partyId, riskLevel, segments, transactionFacts (JSONB), profileVersion, effectiveFrom, validUntil, updatedAt.
- [ ] Criar inbox consumer (SQS) para `CustomerRiskProfileUpdated.v1` com deduplicação por `riskProfileId + profileVersion`.
- [ ] Atualizar o fact resolver `customerRisk` para consultar projeção local primeiro.
- [ ] Se projeção ausente ou expirada: fallback REST legado com quality `STALE` e reasonCode `PROJECTION_UNAVAILABLE`.
- [ ] Se projeção presente e válida: quality `PRESENT`, source `LOCAL_PROJECTION`.
- [ ] Migration Flyway para a tabela.
- [ ] Testes: consumer idempotente, fact resolver com projeção presente/ausente/expirada.

### M8.3 — Cutover do gatilho de caso

- [ ] Adicionar property `evaluation.case-trigger-mode` com valores `LEGACY`, `SHADOW`, `MANUAL_REVIEW_LIVE` (default: `MANUAL_REVIEW_LIVE`).
- [ ] Quando `MANUAL_REVIEW_LIVE`: `TransactionSignalOutboxListener` NÃO publica o sinal legado que cria Alert; apenas os eventos v2.
- [ ] Quando `LEGACY`: comportamento atual preservado (dual-run).
- [ ] Remover condição de shadow do outbox listener.
- [ ] Testes de integração validam ambos os modos.
- [ ] Documentar runbook de rollback (trocar property e restart).

### M8.4 — Tela de administração de regras no Workbench

- [ ] Nova rota `/admin/rules` com listagem de regras do catálogo.
- [ ] Tela de detalhe da regra com configurações (draft/active), versões.
- [ ] Formulário de criação/edição de configuração (expressions + actions).
- [ ] Execução de dry-run com facts manuais e resultado visual.
- [ ] Botão de ativação/desativação com confirmação.
- [ ] Histórico de versões com diff visual.
- [ ] Guard de permissão: apenas `RULE_ADMIN` acessa a rota.
- [ ] API layer: chamar endpoints existentes do `pld-transaction-screening` (porta 8081 ou via proxy).

### M8.5 — Pesquisa global básica

- [ ] Campo de pesquisa no shell (`WorkbenchShell`).
- [ ] Busca por partyId, caseId, nome (substring match no BFF).
- [ ] Resultados agrupados por tipo (Party, Case) com link direto.
- [ ] Endpoint no BFF: `GET /v1/search?q=...` com resultados limitados.

## Cenários BDD

```gherkin
Scenario: projeção de risco atualizada por evento
  Given um CustomerRiskProfileUpdated.v1 publicado para pty_ABC
  When o consumer processa o evento
  Then a projeção local contém riskLevel=HIGH e segments=[PEP_RELATED]
  And o fact resolver retorna customerRisk com quality PRESENT e source LOCAL_PROJECTION

Scenario: projeção ausente usa fallback REST com quality STALE
  Given nenhuma projeção existente para pty_XYZ
  When uma avaliação precisa do fato customerRisk
  Then o resolver usa REST legado
  And o fato é registrado com quality STALE e reasonCode PROJECTION_UNAVAILABLE

Scenario: cutover elimina criação de Alert legado
  Given evaluation.case-trigger-mode=MANUAL_REVIEW_LIVE
  When uma avaliação LIVE com SIGNAL_RAISED é concluída
  Then TransactionEvaluationCompleted.v2, TransactionSignalDetected.v1 e ManualReviewRequested.v2 são publicados
  And nenhum Alert é criado na tabela alert

Scenario: admin cria configuração e ativa após dry-run
  Given um RULE_ADMIN na tela /admin/rules
  When cria uma configuração draft com keywordMatched=true AND customerRisk>=MR
  And executa dry-run com facts {keywordMatched: true, customerRisk: AR}
  Then o dry-run retorna decision=ALERT
  And o botão de ativação fica habilitado
  When ativa a configuração
  Then a configuração passa a ser usada em avaliações futuras
```

## Critérios de aceite

- [ ] Fato `customerRisk` resolvido via projeção local em < 5ms (sem chamada HTTP).
- [ ] Projeção ausente não causa falha; avaliação prossegue com quality degradada.
- [ ] Em modo `MANUAL_REVIEW_LIVE`, nenhum Alert legado é criado.
- [ ] Rollback para `LEGACY` restaura o comportamento anterior sem perda de dados.
- [ ] Tela de admin funciona end-to-end: criar config → dry-run → ativar → verificar na avaliação.
- [ ] Pesquisa global retorna resultados em < 500ms para datasets de teste.
- [ ] Testes unitários, integração e Playwright permanecem verdes.

## Fora deste marco

- Governança completa maker-checker para ativação de regras (Marco 11).
- Projeção de risco com múltiplas versões simultâneas.
- Consumer de `TransactionOccurred.v1` (ingestão produtiva).
- Remoção física da tabela/código Alert.
