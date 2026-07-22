# ADR-010 — avaliação transacional reproduzível

- Status: aceita
- Data: 2026-07-21

## Contexto

O Marco 6 provou o caminho `DecisionExecution → outbox → SQS → inbox → caso`, mas a avaliação ainda é representada por registros separados e não congela todo o contexto necessário para reconstrução histórica. Fatos indisponíveis podem perder semântica ao atravessar adapters e o caso ainda nasce diretamente de `TransactionSignalDetected.v1`, embora o contrato já separe sinal explicado de pedido explícito de trabalho humano.

O próximo incremento precisa tornar cada avaliação uma unidade imutável, explicar ausência/erro de dados e separar conclusão técnica, sinal e roteamento humano sem introduzir ainda a projeção local de risco.

## Decisões

1. `TransactionEvaluation` é o aggregate root da execução transacional. Cada avaliação possui `evaluationId`, finalidade, entrada versionada, snapshot/hash, ruleset efetivo, fatos, resultados de regra, decisão técnica, explicação e correlação.
2. Entrega e versão de negócio são identidades diferentes: `inputEventId` deduplica a mensagem, `inputEventSchemaVersion` identifica o contrato e `transactionVersion` identifica a versão da transação. Em `LIVE`, a chave natural `(transactionId, transactionVersion, rulesetVersion, purpose)` é única. Execuções não-LIVE usam `(evaluationRequestId, purpose)`, permitindo nova execução intencional com novo request ID e retry idempotente com o mesmo ID. `evaluationId` é surrogate estável; reutilização com outra chave é rejeitada.
3. Adapters normalizam a identidade sem alterar APIs atuais. Evento usa seu `eventId`; HTTP usa `Idempotency-Key` quando fornecido ou um intake ID persistido pelo serviço. IDs legados fora do padrão são mapeados uma única vez por `(sourceSystem, externalTransactionId)` para um `transactionId` interno `txn_ULID`, preservando o ID externo no snapshot. Entradas legadas sem versão usam `transactionVersion=1` somente enquanto o conteúdo for imutável; mesmo ID com payload divergente é conflito. Novos contratos de ingestão devem fornecer versão de negócio explícita.
4. As finalidades são `LIVE`, `REPLAY`, `BACKTEST`, `DRY_RUN` e `INVESTIGATION`. Somente `LIVE` produz sinais, pedidos de revisão ou efeitos operacionais por padrão.
5. Intake válido, snapshot e ruleset são congelados antes da criação do aggregate. Falha anterior pertence à quarentena de intake, não a uma avaliação. Depois disso, a avaliação transita por `RECEIVED`, `EVALUATING`, `COMPLETED`, `INDETERMINATE` ou `FAILED`. `INDETERMINATE` é uma conclusão auditável com lacunas tratadas pela política; `FAILED` registra `failureStage` e `failureCode` e não possui outcome ou roteamento.
6. Fatos usam qualidade `PRESENT`, `UNKNOWN`, `STALE` ou `ERROR`. Uma expressão que depende de fato diferente de `PRESENT` resulta em `INDETERMINATE`, nunca implicitamente em `FALSE`; toda qualidade não presente exige reason code.
7. Detecção e revisão são dimensões independentes. `evaluationOutcome` é `NO_SIGNAL` ou `SIGNAL_RAISED`; `reviewRequired` indica trabalho humano. Revisão humana usa `DERIVED_TO_ANALYST` ou `MANDATORY_SECOND_APPROVAL`; `TECHNICAL_RETRY` só pode existir sem pedido humano.
8. Os contratos v1 permanecem inalterados. Toda avaliação `LIVE` cria um único evento lógico `TransactionEvaluationCompleted.v2`, entregue at-least-once. `TransactionSignalDetected.v1` explica cada sinal operacional associado a um `partyId` tipado. Entradas HTTP legadas sem essa associação continuam no workflow de `Alert` e não são elegíveis ao cutover até o mapeamento da parte. `ManualReviewRequested.v2` é o gatilho oficial de trabalho humano e só é criado quando `reviewRequired=true` e a parte está resolvida.
9. Pedidos de revisão são deduplicados por `(sourceSystem, reviewRequestId)`, sendo `sourceSystem` derivado do `producer` do envelope. A versão da política de agrupamento pertence ao consumidor e é persistida como metadado da decisão de agrupamento; não altera a identidade do pedido.
10. Existe no máximo um `ManualReviewRequested.v2` por avaliação, garantido por unicidade no produtor. `signalIds` é a lista autoritativa de sinais relacionados e pode ser vazia quando a revisão decorre apenas de indeterminação. O consumidor não associa sinais ao pedido apenas por coincidência de `evaluationId`.
11. SQS Standard permanece o padrão. O consumidor deve tolerar pedido e sinais em qualquer ordem, preservando projeções pendentes até que possam ser anexadas ao caso.
12. O workflow de `Alert` legado e a abertura de caso por sinal permanecem em dual-run controlado durante a comparação. Um único modo de gatilho é ativo: `LEGACY`, `SHADOW` ou `MANUAL_REVIEW_LIVE`. Em `SHADOW`, somente o legado cria caso. Até existir configuração compartilhada de runtime, cutover e rollback exigem pausar e drenar consumidores, implantar uma configuração uniforme e só então retomar o consumo; rolling deployment com modos mistos é proibido.
13. O snapshot usa `snapshotFormatVersion=transaction-snapshot-v1`, JSON canonicalizado conforme RFC 8785, bytes UTF-8 e SHA-256 hexadecimal minúsculo. Arrays preservam ordem de negócio; propriedades de objetos seguem a canonicalização.
14. Enquanto a projeção local de risco não existe, o contexto vindo do REST legado registra `source=LEGACY_REST`, qualidade e reason code; versão de perfil é opcional e nunca inventada. Projeção local exige versão apenas quando a qualidade é `PRESENT`.

## Consequências

- Novas avaliações podem ser explicadas sem executar novamente regras atuais.
- Replay sob outra finalidade ou versão não é confundido com duplicidade.
- Os schemas v1 não mudam; os contratos reproduzíveis entram em v2 e convivem até retirada formal.
- `pld-customer-analysis` precisa projetar pedidos e sinais independentemente da existência imediata de um caso.
- A projeção local de risco e a remoção da chamada REST permanecem para o marco seguinte.
- Dados históricos sem snapshot não são inventados; permanecem identificados como legado.

## Matriz de efeitos

| Finalidade | Persistir avaliação | Criar evento de avaliação | Criar sinal | Criar pedido | Executar ação |
|---|---|---|---|---|---|
| `LIVE` | sim | sim, v2 | conforme resultado | conforme política | conforme política |
| `REPLAY` | sim | não, por padrão | não | não | não |
| `BACKTEST` | sim, em escopo próprio | não | não | não | não |
| `DRY_RUN` | sim, em escopo próprio | não | não | não | não |
| `INVESTIGATION` | sim | não, por padrão | não | não | não |

`TransactionEvaluationCompleted.v2` representa um único evento lógico com `eventId` estável. A entrega física pode ocorrer mais de uma vez e consumidores devem deduplicá-la.

## Runbook de replay sem efeitos operacionais

1. Emitir `DetectionEvent` com `purpose=REPLAY`, `evaluationRequestId` único e os mesmos `transactionId`, `transactionVersion`, `rulesetVersion` e snapshot da avaliação original.
2. O `DecisionService` resolve a identidade LIVE e reconhece que já existe uma avaliação com a chave natural — retorna o resultado existente sem reexecutar regras nem persistir novos registros.
3. Para forçar nova execução (ex.: novo ruleset), use novo `evaluationRequestId` com `purpose=REPLAY` — a unicidade por `(evaluationRequestId, purpose)` garante idempotência de retry.
4. `REPLAY`, `BACKTEST`, `DRY_RUN` e `INVESTIGATION` não publicam eventos de integração nem criam efeitos operacionais por padrão; opt-in explícito pode ser configurado por `evaluationRequestId`.
5. Verificar a integridade do resultado comparando `snapshotHash` e `evaluationOutcome` com a avaliação LIVE correspondente.

## Significado de reproduzível

Neste marco, reproduzir significa reconstruir e consultar os fatos, versões, resultados e explicação usados na época, mesmo após alteração do catálogo. Não significa reexecutar byte a byte código antigo. Uma reexecução intencional cria outra avaliação com finalidade `REPLAY`.
