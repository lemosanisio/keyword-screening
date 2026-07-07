# Guia de Uso — Decision Engine API

## Visão Geral

A Decision Engine é uma plataforma de regras de PLD que avalia condições sobre transações PIX e decide se deve gerar alertas. O analista configura **quando** gerar alertas combinando **fatos** (dados observados) com **operadores** de comparação.

### Fluxo resumido

```
1. Consultar fatos e regras disponíveis (catálogo)
2. Criar configuração de regra com expressões
3. Testar com dry-run (obrigatório antes de ativar)
4. Ativar configuração
5. Sistema avalia transações automaticamente e gera alertas
6. Consultar execuções e alertas
```

---

## 1. Consultar o Catálogo

### Regras disponíveis

```bash
curl http://localhost:8080/v1/decision/rules
```

Filtrar por contexto ou categoria:
```bash
curl "http://localhost:8080/v1/decision/rules?context=SCREENING&category=KEYWORD_SCREENING"
```

### Fatos disponíveis

```bash
curl http://localhost:8080/v1/decision/facts
```

Filtrar por entidade:
```bash
curl "http://localhost:8080/v1/decision/facts?entity=Risk&enabled=true"
```

### Entidades de negócio

```bash
curl http://localhost:8080/v1/decision/entities
```

---

## 2. Criar Configuração de Regra

Uma configuração define **quais condições** devem ser satisfeitas para gerar uma ação (ex.: alerta).

```bash
curl -X POST http://localhost:8080/v1/decision/rules/KEYWORD_SCREENING/configurations \
  -H "Content-Type: application/json" \
  -d '{
    "expressions": [
      {
        "type": "CONDITION",
        "factName": "keywordMatched",
        "operator": "EQUALS",
        "expectedValue": true
      },
      {
        "type": "CONDITION",
        "factName": "customerRisk",
        "operator": "GREATER_THAN_OR_EQUAL",
        "expectedValue": "MR"
      }
    ],
    "actions": ["GENERATE_ALERT"],
    "createdBy": "analyst@company.com"
  }'
```

**Resposta (201 Created):**
```json
{
  "id": "cfg-uuid-001",
  "ruleCode": "KEYWORD_SCREENING",
  "version": 1,
  "active": false,
  "draft": true,
  "expressions": [...],
  "actions": ["GENERATE_ALERT"],
  "createdBy": "analyst@company.com",
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T10:30:00Z"
}
```

A configuração é criada em estado **draft** (não ativa). Precisa de dry-run antes de ativar.

---

## 3. Atualizar Configuração

Cada update cria uma nova versão (monotonicamente crescente):

```bash
curl -X PUT http://localhost:8080/v1/decision/rule-configurations/{id} \
  -H "Content-Type: application/json" \
  -d '{
    "expressions": [
      {
        "type": "CONDITION",
        "factName": "keywordMatched",
        "operator": "EQUALS",
        "expectedValue": true
      }
    ],
    "actions": ["GENERATE_ALERT"],
    "updatedBy": "analyst@company.com"
  }'
```

### Consultar histórico de versões

```bash
curl http://localhost:8080/v1/decision/rule-configurations/{id}/versions
```

---

## 4. Dry-Run (Simulação)

O dry-run testa a configuração com fatos fornecidos manualmente. **Obrigatório antes da ativação.**

```bash
curl -X POST http://localhost:8080/v1/decision/rule-configurations/{id}/dry-run \
  -H "Content-Type: application/json" \
  -d '{
    "facts": {
      "keywordMatched": true,
      "customerRisk": "AR"
    }
  }'
```

**Resposta (200 OK):**
```json
{
  "decision": "ALERT",
  "actions": ["GENERATE_ALERT"],
  "matchedExpressions": [
    {
      "factName": "keywordMatched",
      "operator": "EQUALS",
      "expectedValue": true,
      "actualValue": true,
      "satisfied": true,
      "justification": "Fact 'keywordMatched' (true) é igual a true"
    },
    {
      "factName": "customerRisk",
      "operator": "GREATER_THAN_OR_EQUAL",
      "expectedValue": "MR",
      "actualValue": "AR",
      "satisfied": true,
      "justification": "Fact 'customerRisk' (AR) é >= MR (ordinal 2 >= 1)"
    }
  ],
  "failedExpressions": [],
  "configurationVersion": 1
}
```

### O que NÃO acontece no dry-run

- ❌ Alertas não são gerados
- ❌ Execuções não são persistidas na auditoria
- ❌ Eventos não são publicados
- ❌ FactResolvers não são invocados

---

## 5. Ativar Configuração

Após dry-run bem-sucedido:

```bash
curl -X POST http://localhost:8080/v1/decision/rule-configurations/{id}/activate
```

**Regras de ativação:**
- Dry-run obrigatório na versão atual (senão → 422)
- Apenas uma configuração ativa por regra (senão → 409)

### Desativar

```bash
curl -X POST http://localhost:8080/v1/decision/rule-configurations/{id}/deactivate
```

---

## 6. Consultar Execuções de Decisão

Toda execução do Decision Engine é persistida para auditoria (inclusive IGNORE).

### Por transação
```bash
curl "http://localhost:8080/v1/decision/executions?transactionId=TX-001&page=0&size=20"
```

### Por regra
```bash
curl "http://localhost:8080/v1/decision/executions?ruleId={uuid}&page=0&size=20"
```

### Por tipo de decisão
```bash
curl "http://localhost:8080/v1/decision/executions?decision=ALERT&page=0&size=20"
```

### Por traceId (correlação)
```bash
curl "http://localhost:8080/v1/decision/executions?traceId=trace-uuid-123"
```

### Por ID específico
```bash
curl http://localhost:8080/v1/decision/executions/{id}
```

---

## 7. Consultar e Gerenciar Alertas

### Por transação
```bash
curl "http://localhost:8080/v1/alerts?transactionId=TX-001"
```

### Por regra (paginado)
```bash
curl "http://localhost:8080/v1/alerts?ruleId={uuid}&page=0&size=20"
```

### Por ID
```bash
curl http://localhost:8080/v1/alerts/{alertId}
```

### Atualizar status

State machine de status:
```
OPEN → UNDER_REVIEW → CLOSED
                    → FALSE_POSITIVE
```

```bash
curl -X PATCH http://localhost:8080/v1/alerts/{alertId}/status \
  -H "Content-Type: application/json" \
  -d '{"status": "UNDER_REVIEW"}'
```

---

## Referência: Operadores Suportados

| Operador | Descrição | Tipos compatíveis |
|----------|-----------|-------------------|
| EQUALS | Igual | Boolean, Enum, String, Number |
| NOT_EQUALS | Diferente | Boolean, Enum, String, Number |
| GREATER_THAN_OR_EQUAL | Maior ou igual (ordinal) | Enum (CustomerRisk: BR < MR < AR) |

## Referência: Fatos do MVP

| Fact | Tipo | Operadores | Descrição |
|------|------|------------|-----------|
| keywordMatched | BOOLEAN | EQUALS, NOT_EQUALS | Indica se keyword screening detectou match |
| customerRisk | ENUM | EQUALS, NOT_EQUALS, GTE | Nível de risco do cliente (BR, MR, AR) |

## Referência: Decisões e Ações

| Decisão | Significado |
|---------|-------------|
| ALERT | Todas as condições satisfeitas → ação executada |
| IGNORE | Pelo menos uma condição falhou → sem ação |

| Ação | Efeito |
|------|--------|
| GENERATE_ALERT | Cria alerta no Alert Context com status OPEN |
| IGNORE | Nenhum efeito (apenas registro) |

---

## Erros Comuns

| HTTP | Código | Significado |
|------|--------|-------------|
| 400 | Bad Request | Campos obrigatórios ausentes ou formato inválido |
| 404 | Not Found | Recurso não encontrado (regra, configuração, alerta) |
| 409 | Conflict | Já existe configuração ativa para a mesma regra |
| 422 | Unprocessable Entity | Validação falhou (fact inexistente, operador incompatível, dry-run não realizado) |

---

## Documentação Técnica

- **OpenAPI Spec**: `src/main/resources/openapi/openapi.yaml`
- **ADRs**: `docs/adr/`
- **Requirements**: `.kiro/specs/decision-engine/requirements.md`
- **Design**: `.kiro/specs/decision-engine/design.md`
