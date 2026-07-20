# Guia Técnico — Decision Engine

## Visão Geral

Este guia descreve como expandir a Rule Platform com novas Entidades, Fatos, Regras e Fact Resolvers.
Destinado à **engenharia** — analistas não executam essas ações.

---

## Modelo de Dados

```
┌─────────────────────┐       ┌──────────────────────┐       ┌──────────────────────────┐
│  entity_definition  │       │   fact_definition    │       │    rule_definition       │
├─────────────────────┤       ├──────────────────────┤       ├──────────────────────────┤
│ id (UUID PK)        │       │ id (UUID PK)         │       │ id (UUID PK)             │
│ name (UNIQUE)       │◄──────│ entity (FK lógico)   │       │ code (UNIQUE)            │
│ display_name        │       │ name (UNIQUE)        │       │ name                     │
│ source_system       │       │ display_name         │       │ description              │
│ fact_names (JSONB)  │       │ type                 │──────►│ supported_facts (JSONB)  │
│ created_at          │       │ context              │       │ supported_actions (JSONB) │
└─────────────────────┘       │ source               │       │ context                  │
                              │ supported_operators  │       │ category                 │
                              │ enabled              │       │ status                   │
                              │ created_at           │       │ created_at               │
                              └──────────────────────┘       └──────────────────────────┘
                                                                        │
                                                                        ▼
                              ┌──────────────────────────────────────────────────────┐
                              │              rule_configuration                       │
                              ├──────────────────────────────────────────────────────┤
                              │ id (UUID PK)                                          │
                              │ rule_id (FK → rule_definition.id)                     │
                              │ expressions (JSONB) ← analista configura              │
                              │ actions (JSONB)                                       │
                              │ active (BOOLEAN) ← partial unique por rule_id         │
                              │ draft (BOOLEAN)                                       │
                              │ current_version (INT)                                 │
                              │ created_by, created_at, updated_at                    │
                              └──────────────────────────────────────────────────────┘
```

---

## 1. Adicionar Nova Entidade (Entity)

Uma Entity agrupa Facts de um mesmo sistema de origem.

### Tabela: `entity_definition`

```sql
INSERT INTO entity_definition (id, name, display_name, source_system, fact_names)
VALUES (
    gen_random_uuid(),
    'Customer',                  -- nome técnico (referenciado em fact_definition.entity)
    'Cliente',                   -- nome amigável para UI
    'Cadastro',                  -- sistema de origem dos dados
    '["pep", "segment", "country"]'::JSONB  -- facts que esta entity agrupa
)
ON CONFLICT (name) DO NOTHING;
```

### Verificar via API

```bash
curl http://localhost:8080/v1/decision/entities
```

---

## 2. Adicionar Novo Fato (Fact)

Um Fact é um dado contextual tipado que pode ser usado em expressões de regras.

### Tabela: `fact_definition`

```sql
INSERT INTO fact_definition (id, name, display_name, entity, type, context, source, supported_operators, enabled)
VALUES (
    gen_random_uuid(),
    'pep',                       -- nome técnico (usado em expressions)
    'PEP (Pessoa Exposta)',      -- nome amigável para UI
    'Customer',                  -- entity à qual pertence
    'BOOLEAN',                   -- tipo: BOOLEAN | ENUM | NUMBER | STRING | MONEY
    'SCREENING',                 -- contexto de uso: SCREENING | TRANSACTION | CUSTOMER | ACCOUNT
    'Cadastro',                  -- bounded context de origem
    '["EQUALS", "NOT_EQUALS"]'::JSONB,  -- operadores que o analista pode usar
    TRUE                         -- habilitado para uso em novas configs
)
ON CONFLICT (name) DO NOTHING;
```

### Tipos disponíveis e operadores recomendados

| Tipo | Valores aceitos | Operadores recomendados |
|------|----------------|------------------------|
| BOOLEAN | `true`, `false` | EQUALS, NOT_EQUALS |
| ENUM | String (ex: "BR", "MR", "AR") | EQUALS, NOT_EQUALS, GREATER_THAN_OR_EQUAL |
| NUMBER | Decimal (ex: 1000.50) | EQUALS, NOT_EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL |
| STRING | Texto livre | EQUALS, NOT_EQUALS, CONTAINS |
| MONEY | Objeto `{amount, currency}` | EQUALS, GREATER_THAN, LESS_THAN |

### Verificar via API

```bash
curl "http://localhost:8080/v1/decision/facts?entity=Customer&enabled=true"
```

---

## 3. Adicionar Nova Regra (Rule Definition)

Uma Rule Definition é o template técnico de uma regra. O analista depois configura as expressions.

### Tabela: `rule_definition`

```sql
INSERT INTO rule_definition (id, code, name, description, context, category, supported_facts, supported_actions, status)
VALUES (
    gen_random_uuid(),
    'PEP_SCREENING',                                      -- código único (usado na API)
    'PEP Screening',                                      -- nome legível
    'Regra de screening para Pessoas Politicamente Expostas',
    'CUSTOMER',                                           -- contexto: SCREENING | TRANSACTION | CUSTOMER | ACCOUNT
    'AML',                                                -- categoria: KEYWORD_SCREENING | SANCTIONS | AML | FRAUD | VELOCITY
    '["pep", "customerRisk", "keywordMatched"]'::JSONB,   -- facts que o analista pode usar nas expressions
    '["GENERATE_ALERT", "REVIEW"]'::JSONB,                -- ações disponíveis
    'ACTIVE'                                              -- status: ACTIVE | INACTIVE | DEPRECATED
)
ON CONFLICT (code) DO NOTHING;
```

### Verificar via API

```bash
curl http://localhost:8080/v1/decision/rules/PEP_SCREENING
```

---

## 4. Implementar Fact Resolver (Fonte de Dados)

Um Fact Resolver busca dados de um sistema externo e os transforma em Facts tipados.

### 4.1 Criar o Resolver

```kotlin
// pld-transaction-screening/src/main/kotlin/br/com/decision/domain/resolver/CustomerPepResolver.kt
package br.com.decision.domain.resolver

import br.com.decision.domain.event.DetectionEvent
import br.com.decision.domain.port.CustomerDataPort
import br.com.decision.domain.service.Fact
import br.com.decision.domain.service.FactResolver
import br.com.shared.domain.valueobject.CustomerId
import br.com.decision.domain.valueobject.FactName
import br.com.decision.domain.valueobject.FactValue
import java.time.Instant

class CustomerPepResolver(
    private val customerDataPort: CustomerDataPort
) : FactResolver {

    override val producedFacts = setOf(FactName("pep"))
    override val entity = "Customer"

    override fun resolve(event: DetectionEvent): List<Fact> {
        val isPep = customerDataPort.isPep(event.customerId)
            ?: return emptyList()  // Falha silenciosa: fact ausente

        return listOf(
            Fact(
                name = FactName("pep"),
                value = FactValue.BooleanValue(isPep),
                entity = entity,
                resolvedAt = Instant.now()
            )
        )
    }
}
```

### 4.2 Criar o Port (interface)

```kotlin
// pld-transaction-screening/src/main/kotlin/br/com/decision/domain/port/CustomerDataPort.kt
package br.com.decision.domain.port

import br.com.shared.domain.valueobject.CustomerId

interface CustomerDataPort {
    fun isPep(customerId: CustomerId): Boolean?
}
```

### 4.3 Criar o Adapter (infraestrutura)

```kotlin
// pld-transaction-screening/src/main/kotlin/br/com/decision/infrastructure/output/rest/CustomerDataAdapter.kt
@Component
class CustomerDataAdapter(
    private val properties: CustomerDataProperties
) : CustomerDataPort {

    private val restClient = RestClient.builder()
        .baseUrl(properties.url)
        .build()

    override fun isPep(customerId: CustomerId): Boolean? {
        return try {
            val response = restClient.get()
                .uri("/customers/{id}/pep", customerId.value)
                .retrieve()
                .body(PepResponse::class.java)
            response?.pep
        } catch (e: Exception) {
            null  // Falha → fact ausente → condição não satisfeita
        }
    }
}
```

### 4.4 Registrar como Bean

```kotlin
// Em DecisionContextConfiguration.kt
@Bean
fun customerPepResolver(customerDataPort: CustomerDataPort) = CustomerPepResolver(customerDataPort)
```

O ContextBuilder **descobre automaticamente** o novo resolver via `List<FactResolver>` (Spring DI).

---

## 5. Ativar uma Regra (Fluxo Completo)

Após inserir entity, fact e rule no banco:

### 5.1 Verificar catálogo

```bash
# Regra disponível?
curl http://localhost:8080/v1/decision/rules/PEP_SCREENING

# Facts disponíveis?
curl "http://localhost:8080/v1/decision/facts?enabled=true"
```

### 5.2 Criar configuração (draft)

```bash
curl -X POST http://localhost:8080/v1/decision/rules/PEP_SCREENING/configurations \
  -H "Content-Type: application/json" \
  -d '{
    "expressions": [
      {"type": "CONDITION", "factName": "pep", "operator": "EQUALS", "expectedValue": true},
      {"type": "CONDITION", "factName": "keywordMatched", "operator": "EQUALS", "expectedValue": true}
    ],
    "actions": ["GENERATE_ALERT"],
    "createdBy": "engineer@company.com"
  }'
```

Anotar o `id` retornado.

### 5.3 Testar com dry-run

```bash
curl -X POST http://localhost:8080/v1/decision/rule-configurations/{ID}/dry-run \
  -H "Content-Type: application/json" \
  -d '{
    "facts": {
      "pep": true,
      "keywordMatched": true
    }
  }'
```

Esperar: `"decision": "ALERT"`

### 5.4 Ativar

```bash
curl -X POST http://localhost:8080/v1/decision/rule-configurations/{ID}/activate
```

### 5.5 Verificar em produção

```bash
# Após uma transação passar pelo screening:
curl "http://localhost:8080/v1/decision/executions?transactionId=TX-TEST"
curl "http://localhost:8080/v1/alerts?transactionId=TX-TEST"
```

---

## 6. Checklist: Adicionando Novo Fact

| # | Ação | Quem | Onde |
|---|------|------|------|
| 1 | Inserir `entity_definition` (se nova) | Engenharia | Migration SQL ou INSERT direto |
| 2 | Inserir `fact_definition` | Engenharia | Migration SQL ou INSERT direto |
| 3 | Atualizar `entity_definition.fact_names` | Engenharia | UPDATE no banco |
| 4 | Implementar `FactResolver` | Engenharia | Código Kotlin |
| 5 | Implementar Port + Adapter | Engenharia | Código Kotlin |
| 6 | Registrar bean no `DecisionContextConfiguration` | Engenharia | @Bean |
| 7 | Adicionar fact nos `supported_facts` da regra | Engenharia | UPDATE rule_definition |
| 8 | Criar/atualizar configuração usando o novo fact | Analista | API REST |
| 9 | Executar dry-run | Analista | API REST |
| 10 | Ativar configuração | Analista | API REST |

---

## 7. Tabelas de Referência

### entity_definition

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| id | UUID PK | Identificador único |
| name | VARCHAR(100) UNIQUE | Nome técnico (referenciado em fact_definition.entity) |
| display_name | VARCHAR(255) | Nome para exibição na UI |
| source_system | VARCHAR(100) | Sistema de origem (Cadastro, PLD, Core Banking, etc.) |
| fact_names | JSONB | Array com nomes dos facts desta entity |
| created_at | TIMESTAMPTZ | Data de criação |

### fact_definition

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| id | UUID PK | Identificador único |
| name | VARCHAR(100) UNIQUE | Nome técnico (usado em expressions) |
| display_name | VARCHAR(255) | Nome para exibição na UI |
| entity | VARCHAR(100) | Entity à qual pertence |
| type | VARCHAR(50) | BOOLEAN, ENUM, NUMBER, STRING, MONEY |
| context | VARCHAR(100) | SCREENING, TRANSACTION, CUSTOMER, ACCOUNT |
| source | VARCHAR(100) | Bounded context de origem |
| supported_operators | JSONB | Operadores permitidos para este fact |
| enabled | BOOLEAN | Se está habilitado para uso |
| created_at | TIMESTAMPTZ | Data de criação |

### rule_definition

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| id | UUID PK | Identificador único |
| code | VARCHAR(50) UNIQUE | Código da regra (usado na API) |
| name | VARCHAR(255) | Nome descritivo |
| description | TEXT | Descrição detalhada |
| context | VARCHAR(100) | SCREENING, TRANSACTION, CUSTOMER, ACCOUNT |
| category | VARCHAR(100) | KEYWORD_SCREENING, SANCTIONS, AML, FRAUD, VELOCITY |
| supported_facts | JSONB | Facts que o analista pode usar em configs |
| supported_actions | JSONB | Ações disponíveis para esta regra |
| status | VARCHAR(50) | ACTIVE, INACTIVE, DEPRECATED |
| created_at | TIMESTAMPTZ | Data de criação |

---

## 8. Exemplo Completo: Adicionando Regra de PEP

### Migration SQL

```sql
-- V9__add_pep_fact_and_rule.sql

-- 1. Entity (Customer já pode existir — ON CONFLICT ignora)
INSERT INTO entity_definition (id, name, display_name, source_system, fact_names)
VALUES (gen_random_uuid(), 'Customer', 'Cliente', 'Cadastro', '["pep", "segment", "country"]'::JSONB)
ON CONFLICT (name) DO UPDATE SET fact_names = '["pep", "segment", "country"]'::JSONB;

-- 2. Fact: pep
INSERT INTO fact_definition (id, name, display_name, entity, type, context, source, supported_operators, enabled)
VALUES (gen_random_uuid(), 'pep', 'PEP (Pessoa Exposta)', 'Customer', 'BOOLEAN', 'SCREENING', 'Cadastro',
        '["EQUALS", "NOT_EQUALS"]'::JSONB, TRUE)
ON CONFLICT (name) DO NOTHING;

-- 3. Rule Definition
INSERT INTO rule_definition (id, code, name, description, context, category, supported_facts, supported_actions, status)
VALUES (gen_random_uuid(), 'PEP_SCREENING', 'PEP Screening',
        'Gera alerta quando transação suspeita envolve Pessoa Politicamente Exposta',
        'SCREENING', 'AML',
        '["pep", "keywordMatched", "customerRisk"]'::JSONB,
        '["GENERATE_ALERT", "REVIEW"]'::JSONB,
        'ACTIVE')
ON CONFLICT (code) DO NOTHING;
```

### Kotlin: Resolver + Port + Adapter + Bean

Veja seção 4 acima.

### API: Configurar + Testar + Ativar

Veja seção 5 acima.
