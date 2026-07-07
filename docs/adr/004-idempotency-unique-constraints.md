# ADR-004: Idempotência via UNIQUE Constraints no PostgreSQL

## Status

Aceita

## Contexto

O Decision Engine consome eventos (DetectionEvent) que podem ser entregues mais de uma vez. O Alert Context recebe DecisionMadeEvents que também podem duplicar. É necessário garantir que reprocessamento não cause efeitos colaterais.

## Decisão

Idempotência implementada em duas camadas:

### 1. Application Layer (check-then-act)

```kotlin
val existing = repository.findByTransactionIdAndRuleId(transactionId, ruleId)
if (existing != null) return existing.result
```

### 2. Database Layer (UNIQUE constraint como última barreira)

```sql
-- decision_execution: uma execução por (transaction_id, rule_id)
CONSTRAINT uk_decision_execution_idempotency UNIQUE (transaction_id, rule_id)

-- alert: um alerta por (transaction_id, rule_id)
CONSTRAINT uk_alert_idempotency UNIQUE (transaction_id, rule_id)

-- rule_configuration: apenas uma config ativa por regra
CREATE UNIQUE INDEX uk_rule_configuration_active ON rule_configuration (rule_id) WHERE active = TRUE
```

### Comportamento em caso de duplicata

- **DecisionExecution**: retorna resultado existente sem reexecutar
- **Alert**: retorna alerta existente sem criar novo
- **Nenhum evento publicado** em caso de duplicata

## Consequências

- At-least-once delivery tolerado sem corrupção de dados
- Sem necessidade de deduplicação externa (Redis, Bloom filter)
- Partial unique index permite apenas uma config ativa por regra
- Performance: lookup por UNIQUE index = O(log n)
