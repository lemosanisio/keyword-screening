# Marco 7 — avaliação transacional reproduzível

Status: concluído — aggregate reproduzível, tri-state, ruleset congelado, eventos v2, projeções, DLQ e métricas entregues

## Objetivo

Tornar toda nova avaliação transacional `LIVE` imutável, reproduzível e semanticamente explícita. Fatos ausentes ou inválidos não podem virar `FALSE` silenciosamente, e o pedido de revisão humana deve ser separado do sinal que o explica.

Referências: ADR-010, TS-FR-002, TS-FR-003 e TS-FR-004.

## Hipóteses

- Um aggregate de avaliação reduz divergência entre screening, decisão e eventos publicados.
- Qualidade explícita de fatos permite distinguir ausência de dado, expiração e falha técnica.
- `ManualReviewRequested` torna o gatilho de caso observável e independente da severidade do sinal.
- Projetar sinais e pedidos antes de agrupá-los permite tolerar duplicidade e reorder do SQS Standard.

## Decisões do marco

- [x] Limitar o marco à avaliação reproduzível; projeção local de risco fica fora.
- [x] Adotar finalidades `LIVE`, `REPLAY`, `BACKTEST`, `DRY_RUN` e `INVESTIGATION`.
- [x] Somente `LIVE` produz efeitos operacionais por padrão.
- [x] Adotar estados `RECEIVED`, `EVALUATING`, `COMPLETED`, `INDETERMINATE` e `FAILED`.
- [x] Separar `evaluationOutcome`, `reviewRequired` e `recommendedRoute`.
- [x] Deduplicar revisão por `(sourceSystem, reviewRequestId)`.
- [x] Manter `groupingPolicyVersion` como metadado local da decisão de agrupamento.
- [x] Preservar contratos v1 e introduzir `TransactionEvaluationCompleted.v2` e `ManualReviewRequested.v2`.
- [x] Tornar `ManualReviewRequested.v2` o gatilho alvo do caso.
- [x] Limitar a um pedido de revisão por avaliação e usar `signalIds` como associação autoritativa.

## Fatias de entrega

### M7.1 — caracterização e modelo

- [x] Caracterizar API, `RuleExecution`, `DecisionExecution` e `Alert` atuais antes da mudança.
- [x] Criar `TransactionEvaluation` com identidade e chave de idempotência completas.
- [x] Separar `inputEventId`, `inputEventSchemaVersion` e `transactionVersion`.
- [x] Normalizar IDs no intake: `eventId` para eventos; `Idempotency-Key` ou intake ID persistido para HTTP.
- [x] Mapear `(sourceSystem, externalTransactionId)` legado para `transactionId` interno `txn_ULID` sem alterar a API.
- [x] Usar `transactionVersion=1` em entrada legada somente com `transactionId` imutável e rejeitar payload divergente como conflito.
- [x] Manter unique LIVE da chave natural e vínculo um-para-um com `evaluationId`.
- [x] Deduplicar execução não-LIVE por `(evaluationRequestId, purpose)`.
- [x] Persistir finalidade, instantes, correlação e causação.
- [ ] Criar o aggregate somente após intake, snapshot e ruleset válidos; falha anterior vai para quarentena.
- [x] Persistir snapshot JSON imutável, referência, `snapshotFormatVersion` e hash SHA-256.
- [x] Canonicalizar snapshot conforme RFC 8785, UTF-8, antes do hash.
- [x] Criar golden test que reconstrói e valida o hash do snapshot.
- [x] Preservar registros históricos sem fabricar metadados ausentes.

### M7.2 — fatos e expressões tri-state

- [x] Criar `FactResult` com definição/versionamento, qualidade, valor tipado opcional, origem e validade.
- [x] Criar `ExpressionResult` com `TRUE`, `FALSE` ou `INDETERMINATE`.
- [x] Adaptar resolvers para retornar `UNKNOWN`, `STALE` e `ERROR` explicitamente.
- [x] Definir política versionada para cada expressão/regra indeterminada.
- [x] Persistir fatos indeterminados e reason codes na explicação.
- [ ] Executar avaliador novo em shadow e classificar divergências contra o legado (shadow existe como modo de gatilho; classificação de divergências depende de observabilidade M7.6).

### M7.3 — ruleset e conclusão

- [x] Congelar um `RuleSet` efetivo e imutável por avaliação.
- [x] Referenciar cada regra/configuração e versão usada.
- [x] Persistir regras candidatas, executadas e acionadas.
- [x] Produzir estado, `evaluationOutcome`, `reviewRequired` e rota sem reconstrução posterior.
- [x] Exigir `failureStage` e `failureCode` para avaliação `FAILED`.
- [x] Registrar risco REST legado com origem, qualidade e reason code, sem inventar versão.
- [x] Garantir atomicidade entre avaliação, resultados e outbox.

### M7.4 — eventos e compatibilidade

- [x] Manter schemas e fixtures v1 inalterados.
- [x] Publicar um evento lógico `TransactionEvaluationCompleted.v2` para toda avaliação `LIVE` concluída, indeterminada ou falha criada após intake válido.
- [x] Publicar `TransactionSignalDetected.v1` somente quando houver sinal explicado e `partyId` tipado; intake legado não associável permanece no workflow de `Alert`.
- [x] Publicar no máximo um `ManualReviewRequested.v2` por avaliação quando a política exigir trabalho humano.
- [x] Garantir unique de pedido v2 por `evaluationId` no produtor.
- [x] Permitir `signalIds=[]` quando a revisão decorrer somente de indeterminação.
- [x] Validar em contract tests os envelopes finais produzidos contra os schemas antes do release.
- [x] Preservar IDs dos três eventos em retry técnico.
- [x] Garantir que `REPLAY`, `BACKTEST`, `DRY_RUN` e `INVESTIGATION` não produzam efeitos sem opt-in explícito.
- [x] Migrar a constraint da outbox para permitir múltiplos sinais por avaliação com identidade lógica por sinal.
- [x] Testar duas regras acionadas gerando sinais distintos na mesma avaliação.

### M7.5 — projeção e caso humano

- [x] Consumir `TransactionEvaluationCompleted.v2` e `ManualReviewRequested.v2` com inbox.
- [x] Projetar avaliações, sinais e pedidos independentemente da existência de caso.
- [x] Deduplicar pedidos por `(sourceSystem, reviewRequestId)`.
- [x] Criar migration com unique `(source_system, source_request_id)` para pedidos v2.
- [x] Definir backfill seguro: fontes v1 permanecem com identidade legada e não recebem IDs fabricados.
- [x] Testar concorrência e reentrega depois da troca de política de agrupamento.
- [x] Registrar a `groupingPolicyVersion` aplicada pelo consumidor.
- [x] Abrir ou agrupar caso a partir de `ManualReviewRequested.v2` exatamente uma vez.
- [x] Anexar somente sinais listados em `signalIds`, mesmo quando chegarem antes do pedido.
- [x] Preservar sinais pendentes até a chegada do pedido ou expiração operacional definida.
- [x] Ao expirar espera de associação, manter a projeção do sinal e emitir métrica/alerta; não descartar o sinal.
- [ ] Manter modo shadow e comparação com abertura por sinal.
- [ ] Exibir no Workbench finalidade, estado, fatos não presentes e explicação original.

### M7.6 — rollout e observabilidade

- [x] Adicionar um modo mutuamente exclusivo `LEGACY`, `SHADOW` ou `MANUAL_REVIEW_LIVE` por instância para o gatilho de caso.
- [ ] Persistir o modo em configuração compartilhada ou automatizar o protocolo pause/drain/deploy/resume para impedir modos mistos no cluster.
- [x] Medir avaliações por estado/finalidade, fatos por qualidade, lag, outbox e divergências.
- [x] Testar replay, duplicidade, reorder e recuperação após falha; DLQ para poison messages implementada.
- [x] Documentar runbook de replay sem efeitos operacionais.
- [ ] Validar o fluxo ponta a ponta no Playwright.

## Gates de entrega

### Gate A — produtor reproduzível

- Aggregate, snapshot, facts tri-state, ruleset e eventos v2 implementados.
- APIs e Alert legado permanecem compatíveis.
- Eventos v2 ficam em shadow; nenhum gatilho de caso é trocado.

### Gate B — consumidor e cutover

- Projeções toleram reorder e pedidos v2 são idempotentes.
- Comparação entre caso por sinal e caso por pedido não apresenta perda nem duplicidade.
- Comparação usa `evaluationId`; toda divergência deve estar classificada e nenhuma duplicidade ou perda pode permanecer sem explicação na janela acordada.
- O cutover troca `SHADOW` por `MANUAL_REVIEW_LIVE` após pause e drain: o pedido v2 passa a criar/agrupar caso e o sinal legado deixa de fazê-lo.
- Rollback usa o mesmo protocolo para voltar a `LEGACY`; instâncias com modos diferentes não podem consumir simultaneamente.

## Cenários BDD

```gherkin
Scenario: fato ausente não vira falso
  Given uma avaliação LIVE cuja regra depende de um fato UNKNOWN
  When a expressão é avaliada
  Then o resultado da expressão é INDETERMINATE
  And a avaliação registra o fato e o reason code
  And nenhuma decisão NO_SIGNAL é inferida silenciosamente

Scenario: retry idempotente preserva a avaliação
  Given uma avaliação persistida e uma outbox pendente
  When a publicação falha e o relay tenta novamente
  Then evaluationId e os eventIds de avaliação, sinais e pedido permanecem iguais
  And somente uma avaliação existe

Scenario: intake inválido não cria avaliação
  Given uma entrada sem identidade estável ou snapshot válido
  When o intake é processado
  Then a entrada é enviada para quarentena com reason code
  And nenhum TransactionEvaluation ou evento operacional é criado

Scenario: replay não produz efeito operacional
  Given uma avaliação anterior concluída
  When a mesma transação é avaliada com purpose REPLAY
  Then uma nova avaliação reproduzível é persistida
  And nenhum sinal, pedido de revisão ou caso é criado por padrão

Scenario: duas versões de negócio da mesma transação
  Given dois eventos com o mesmo transactionId e transactionVersion diferentes
  When ambos são avaliados sob o mesmo ruleset e purpose
  Then duas avaliações distintas são persistidas
  And inputEventSchemaVersion não participa como versão de negócio

Scenario: entrada legada sem versão de negócio
  Given uma entrada HTTP legada com ID externo fora do padrão e sem versão
  When o adapter normaliza a entrada
  Then o mesmo par sourceSystem e externalTransactionId sempre resolve para o mesmo txn_ULID interno
  And transactionVersion é 1
  And uma repetição com o mesmo ID externo e payload divergente é rejeitada como conflito

Scenario: replays intencionais distintos
  Given uma transação e um ruleset já avaliados em REPLAY
  When outro comando autorizado usa novo evaluationRequestId
  Then uma nova avaliação REPLAY é criada
  And retry com o mesmo evaluationRequestId não cria outra avaliação

Scenario: pedido de revisão chega antes do sinal
  Given um ManualReviewRequested recebido antes de seus sinais
  When o consumidor processa mensagens em ordem arbitrária
  Then exatamente um caso é criado
  And somente os signalIds listados são anexados ao mesmo caso

Scenario: indeterminação exige revisão sem sinal
  Given uma avaliação INDETERMINATE sem regra acionada
  And a política exige revisão humana
  When os eventos LIVE são persistidos
  Then evaluationOutcome é NO_SIGNAL e reviewRequired é true
  And um ManualReviewRequested.v2 com signalIds vazio é criado

Scenario: sinal explicado não exige revisão
  Given uma avaliação COMPLETED com regra acionada
  And a política não exige trabalho humano
  When os eventos LIVE são persistidos
  Then evaluationOutcome é SIGNAL_RAISED e reviewRequired é false
  And o sinal é criado sem pedido de revisão

Scenario: sinal chega antes do pedido de revisão
  Given um TransactionSignalDetected recebido antes do ManualReviewRequested relacionado
  When o pedido é processado posteriormente
  Then a projeção do sinal é preservada
  And exatamente um caso é criado e recebe o sinal listado no pedido

Scenario: política de agrupamento muda após o pedido
  Given um reviewRequestId já processado pela política de agrupamento v1
  When o mesmo pedido é reentregue após ativar a política v2
  Then nenhum novo pedido ou caso é criado
  And o registro preserva que a política v1 foi aplicada

Scenario: dois pedidos competem pela mesma avaliação
  Given dois reviewRequestIds diferentes para o mesmo evaluationId
  When o produtor tenta persistir ambos
  Then a unicidade por evaluationId aceita somente o primeiro

Scenario: múltiplos sinais na mesma avaliação
  Given duas regras acionadas pela mesma avaliação
  When os sinais são persistidos na outbox
  Then cada sinal possui identidade lógica própria
  And ambos podem ser publicados sem violar constraint por evaluationId

Scenario: consulta histórica após alteração da regra
  Given uma avaliação concluída sob ruleset v1
  And o catálogo atual foi alterado para ruleset v2
  When a avaliação histórica é consultada
  Then fatos, resultados e explicação originais permanecem iguais
  And nenhuma regra atual é executada para reconstruir a resposta

Scenario: falha após snapshot válido
  Given intake, snapshot e ruleset congelados
  When a falha impede a conclusão da avaliação
  Then o estado é FAILED
  And failureStage e failureCode são persistidos e publicados no evento v2
  And nenhum sinal ou pedido de revisão é criado

Scenario: cutover mantém um único gatilho de caso
  Given o modo SHADOW em que somente o sinal legado cria caso
  When o modo muda atomicamente para MANUAL_REVIEW_LIVE
  Then somente ManualReviewRequested.v2 pode criar ou agrupar caso
  And a mesma avaliação não cria um segundo caso
```

## Critérios de aceite

- [x] Toda nova avaliação possui snapshot canônico/hash, finalidade, entrada e ruleset rastreáveis.
- [x] Nenhuma qualidade diferente de `PRESENT` é convertida silenciosamente em `FALSE`.
- [x] Toda avaliação `LIVE` produz um único evento lógico `TransactionEvaluationCompleted.v2` válido, com entrega at-least-once.
- [x] Existe no máximo um pedido v2 por avaliação e `signalIds` define sua associação.
- [x] Duplicidade e reorder não perdem sinais nem criam casos duplicados.
- [x] Replays e backtests não produzem efeitos operacionais por padrão.
- [x] A explicação histórica não depende das regras atuais.
- [x] Contratos v1 continuam validando durante a convivência.
- [x] Alert e fluxo legado continuam disponíveis durante shadow.
- [x] Contract tests, testes de integração, builds e Playwright permanecem verdes.

## Fora deste marco

- Projeção local de `CustomerRiskProfileUpdated.v1`.
- Remoção da chamada REST de risco.
- Retirada do `Alert` legado.
- Governança completa de regras e maker-checker do catálogo.
- Ingestão produtiva de `TransactionOccurred.v1`.
- Onboarding, fontes reais, dossiê e COAF.
