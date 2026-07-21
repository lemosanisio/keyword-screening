# Contratos de integração entre serviços

Status: proposta de contrato lógico `v1`. A serialização final pode ser JSON, Avro ou Protobuf segundo a plataforma do time, desde que preserve a semântica.

> **Serialização normativa:** JSON + JSON Schema (ADR-005). Os schemas versionados e fixtures douradas vivem em [`../schemas/v1/`](../schemas/v1/) e são a fonte executável deste contrato — este documento descreve a semântica; o catálogo é a forma. Não manter cópias divergentes de payload entre os dois.

## Princípios

- Eventos descrevem fatos passados; comandos solicitam uma ação.
- Publicação é at-least-once. Não se promete exactly-once entre serviços.
- Cada backend confirma sua mudança de negócio e o registro de outbox na mesma transação local.
- Todo consumidor persiste inbox/deduplicação antes de produzir efeito externo.
- Nenhum evento transporta o dossiê completo nem PII desnecessária.
- Mudança aditiva compatível permanece na mesma versão; remoção, renomeação ou mudança semântica cria nova versão.
- Horários usam ISO-8601 UTC; valores monetários usam quantia decimal e moeda ISO 4217.
- CPF/CNPJ, nome e endereço não são chaves. Usar IDs internos opacos.

## Envelope comum

```json
{
  "eventId": "01J...",
  "eventType": "CustomerRiskProfileUpdated",
  "eventVersion": 1,
  "occurredAt": "2026-07-20T15:30:00Z",
  "publishedAt": "2026-07-20T15:30:01Z",
  "producer": "pld-customer-analysis",
  "correlationId": "01J...",
  "causationId": "01J...",
  "actor": {
    "type": "SYSTEM",
    "id": "policy-evaluator"
  },
  "subject": {
    "partyId": "pty_...",
    "accountId": "acc_...",
    "analysisCycleId": "acy_...",
    "caseId": null
  },
  "dataClassification": "CONFIDENTIAL",
  "payload": {}
}
```

Campos de `subject` são opcionais individualmente, mas o produtor deve incluir todos os IDs que conhece. O `actor.id` de usuário deve ser o identificador corporativo estável, não nome ou e-mail.

## Correlação, causação e idempotência HTTP

Chamadas síncronas e eventos usam a mesma cadeia de rastreabilidade:

| Entrada | Regra |
|---|---|
| `X-Correlation-Id` ausente | o primeiro backend gera um ULID e propaga como `correlationId` |
| `X-Correlation-Id` presente | validar formato mínimo (não vazio) e propagar sem trocar |
| comando HTTP aceito | se produzir evento, `causationId` recebe o ID do comando/evento/request imediatamente causador |
| `Idempotency-Key` presente | obrigatório em comandos com efeito externo ou criação não natural-idempotente; persistir junto ao comando/processamento |
| `Idempotency-Key` ausente em comando idempotente obrigatório | responder erro de validação antes de executar side effects |

Logs, traces e métricas carregam `correlationId`, `causationId` quando houver, `eventId`, `evaluationId`, `analysisCycleId` e `caseId` como campos estruturados, sem PII. O frontend pode repassar `X-Correlation-Id`; se não repassar, o BFF gera. Replays mantêm o `correlationId` original e usam `causationId` novo que aponta para a operação de replay.

UTC/ISO-8601 é obrigatório em todos os horários de negócio e de publicação. Valores monetários trafegam como string decimal + moeda ISO 4217 (`amount.value`, `amount.currency`).

## Eventos de entrada da plataforma

Estes eventos podem ser produzidos por sistemas externos; não pertencem aos dois backends PLD.

| Evento | Consumidor | Propósito |
|---|---|---|
| `CustomerOnboardingStarted.v1` | customer analysis | criar/atualizar `Party` e iniciar ciclo de onboarding sem pressupor decisão síncrona da conta |
| `CustomerDataChanged.v1` | customer analysis | versionar cadastro e avaliar gatilho de revisão |
| `PartyRelationshipChanged.v1` | customer analysis | atualizar relação de sócio, administrador, representante ou beneficiário |
| `AccountStatusChanged.v1` | customer analysis | refletir resultado aplicado pelo sistema de contas |
| `TransactionOccurred.v1` | transaction screening | iniciar avaliação transacional |

O contrato de entrada deve apontar para o sistema mestre. O backend PLD não se torna mestre de cadastro nem razão contábil.

## Eventos publicados por `pld-customer-analysis`

### `CustomerRiskProfileUpdated.v1`

Consumidor principal: `pld-transaction-screening`.

Payload mínimo:

```json
{
  "riskProfileId": "rsk_...",
  "profileVersion": 7,
  "effectiveFrom": "2026-07-20T15:00:00Z",
  "riskLevel": "HIGH",
  "segments": ["PEP_RELATED", "CROSS_BORDER"],
  "transactionFacts": {
    "expectedMonthlyIncome": {"value": "15000.00", "currency": "BRL", "quality": "PRESENT"},
    "expectedCountries": ["BR"]
  },
  "policyVersion": "customer-risk-12"
}
```

Somente fatos necessários a regras transacionais são publicados. Evidências completas, nomes, documentos e narrativas ficam no serviço de origem.

### Outros eventos

| Evento | Quando ocorre | Payload essencial |
|---|---|---|
| `AnalysisCycleStatusChanged.v1` | transição relevante do ciclo | ciclo, tipo, estado anterior/novo, motivo, política |
| `CaseStatusChanged.v1` | criação, atribuição ou transição do caso | caso, origem, estado, responsável opcional, reason codes |
| `AccountDecisionIssued.v1` | decisão explícita de relacionamento | decisão, contexto onboarding/ongoing, rota, política, efeito solicitado |
| `SuspicionDecisionIssued.v1` | decisão explícita de suspeição | decisão, caso/ciclo, rota e política; sem narrativa sensível |
| `CoafCommunicationStatusChanged.v1` | transição de comunicação | communicationId, estado anterior/novo, tentativa e referência de recibo quando permitido |

`AccountDecisionIssued` não afirma que a ação foi aplicada. O resultado real retorna por `AccountStatusChanged` ou outro evento de confirmação do executor.

## Eventos publicados por `pld-transaction-screening`

### `TransactionEvaluationCompleted.v1`

Publicado para toda execução concluída ou para armazenamento analítico conforme política de volume.

Payload essencial:

- `evaluationId`, `transactionId` e versão do evento de entrada;
- `evaluatedAt`;
- snapshot/reference da transação;
- versão do catálogo/configuração de regras;
- versão do perfil de risco local usado;
- IDs/códigos e versões das regras executadas/acionadas;
- fatos relevantes, qualidade e lista de fatos ausentes;
- decisão técnica e explicação estruturada;
- latência e status da execução sem dados secretos.

### `TransactionSignalDetected.v1`

```json
{
  "signalId": "sig_...",
  "evaluationId": "evl_...",
  "transactionId": "txn_...",
  "signalType": "RULE_MATCH",
  "severity": "HIGH",
  "ruleMatches": [
    {"ruleCode": "PIX-009", "ruleVersion": 4, "explanationCode": "AMOUNT_OUTSIDE_PROFILE"}
  ],
  "riskProfileVersion": 7,
  "recommendedRoute": "DERIVED_TO_ANALYST"
}
```

### `ManualReviewRequested.v1`

Publicado quando regra/política exige trabalho humano. `pld-customer-analysis` é dono da deduplicação — chave `(sourceSystem, sourceRequestId, groupingPolicyVersion)`, onde `sourceRequestId` = `reviewRequestId` — e do caso.

Payload essencial:

```json
{
  "reviewRequestId": "rrq_...",
  "evaluationId": "evl_...",
  "transactionId": "txn_...",
  "signalIds": ["sig_..."],
  "reasonCodes": ["POLICY_REQUIRES_REVIEW", "HIGH_IMPACT_ACTION"],
  "recommendedRoute": "MANDATORY_SECOND_APPROVAL"
}
```

- `reasonCodes` usa o vocabulário fechado de motivos de deriva do glossário.
- Sem `severity`/`priority`: prioridade é calculada pelo consumidor conforme política; a explicação detalhada vive nos sinais/avaliação referenciados.
- `recommendedRoute` distingue revisão de analista (`DERIVED_TO_ANALYST`) de segunda aprovação (`MANDATORY_SECOND_APPROVAL`).

### Outros eventos

| Evento | Quando ocorre | Observação |
|---|---|---|
| `ManualReviewRequested.v1` | regra/política requer caso humano | ver seção própria acima |
| `TransactionDecisionExecutionCompleted.v1` | uma ação técnica foi tentada/aplicada | distingue `REQUESTED`, `APPLIED`, `FAILED`, `REVERSED` |
| `RuleConfigurationActivated.v1` | nova configuração entra em vigor | útil para auditoria/monitoramento, sem distribuir lógica |

## Chaves de idempotência

| Operação | Chave recomendada |
|---|---|
| consumir evento | `(consumerName, eventId)` |
| avaliar transação | `(transactionId, transactionEventVersion, rulesetVersion, evaluationPurpose)` ou `evaluationId` fornecido pela origem |
| criar sinal | `(evaluationId, ruleCode, ruleVersion, signalType)` |
| abrir caso a partir de pedido | `(sourceSystem, sourceRequestId, groupingPolicyVersion)` |
| aplicar comando em conta | `commandId` imutável |
| enviar comunicação COAF | `(communicationId, submissionVersion, attemptId)`; retentativa técnica não cria nova comunicação |

Não usar apenas `(transactionId, ruleId)`: reprocessamento legítimo sob outra versão ou finalidade seria perdido.

## APIs síncronas

### Frontend → `pld-customer-analysis`

O frontend usa APIs orientadas a tela:

- fila e contadores;
- pesquisa de parte/conta/caso;
- visão 360 e timeline;
- detalhe e comandos do ciclo/caso;
- evidências e tentativas de fonte;
- decisões e aprovações;
- dossiê e comunicação COAF;
- administração de políticas quando pertencer ao novo backend.

Essas APIs podem compor projeções locais de eventos transacionais. Não fazem fan-out síncrono para montar cada tela.

### Administração transacional

As APIs de catálogo, configuração, dry-run, backtest e ativação permanecem no motor transacional. O BFF pode expor uma fachada autorizada para o frontend ou usar gateway/reverse proxy; não duplica as regras.

### Comandos entre serviços

Preferir eventos para propagação de estado. Uma chamada síncrona só é aceita quando o usuário precisa de resposta imediata e a operação é claramente dona do serviço chamado. Toda chamada tem timeout, idempotency key, autenticação de workload e resultado auditável.

## Projeções e consistência

- A visão unificada é eventualmente consistente e exibe `updatedAt`/estado de sincronização quando relevante.
- O backend de clientes guarda uma projeção somente leitura de sinais e avaliações transacionais necessárias à UI e ao dossiê.
- O motor transacional guarda somente a projeção mínima do perfil de risco necessária às regras.
- Uma projeção pode ser reconstruída por replay controlado; replay não dispara efeitos externos sem modo explícito.
- Divergência de projeção deve ser detectável por métricas, cursor/offset e reconciliador.

## Evolução e testes de contrato

1. Schemas vivem em catálogo versionado acessível aos três repositórios.
2. CI valida compatibilidade retroativa e exemplos dourados.
3. Consumidores aceitam campos desconhecidos.
4. Enum novo não pode quebrar desserialização; mapear para `UNKNOWN` e alertar quando apropriado.
5. Eventos possuem fixtures sem PII real.
6. Cada produtor executa teste contra expectativas publicadas de consumidores críticos.
7. Deprecação inclui telemetria de uso, data-alvo e período de convivência.
