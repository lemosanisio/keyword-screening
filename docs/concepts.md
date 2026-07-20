# Conceitos — Rule Platform de PLD

## O que é a Rule Platform?

A Rule Platform é um sistema de decisão para **Prevenção à Lavagem de Dinheiro (PLD)** que desacopla a **detecção** de condições suspeitas da **decisão** de gerar alertas. Isso permite que analistas de compliance configurem políticas de alerta sem depender de engenharia para cada mudança.

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                           Rule Platform de PLD                                    │
│                                                                                  │
│   ┌────────────────┐      ┌─────────────────────┐      ┌──────────────────┐     │
│   │   Screening    │      │    Decision          │      │    Alert         │     │
│   │   Context      │─────►│    Context           │─────►│    Context       │     │
│   │                │      │                     │      │                  │     │
│   │   "Detecta"    │      │   "Decide"          │      │   "Alerta"       │     │
│   └────────────────┘      └─────────────────────┘      └──────────────────┘     │
│                                                                                  │
│   Produz eventos          Avalia regras                 Cria/gerencia alertas    │
│   de detecção             Aplica configurações          para o analista          │
│                           Audita decisões                                        │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## Bounded Contexts

### Screening Context

**Responsabilidade:** Detectar condições suspeitas em transações PIX.

Contém dois módulos:
- **Keyword Screening (MF09)** — Busca termos restritos pré-cadastrados na descrição
- **Contextual Screening** — Análise semântica via LLM para reduzir falsos positivos

O Screening Context **nunca decide** se deve gerar alerta. Ele apenas detecta e publica um evento.

### Decision Context

**Responsabilidade:** Decidir se uma detecção deve gerar ação (alerta, bloqueio, revisão).

Contém:
- Rule Catalog — Definições de regras
- Fact Registry — Catálogo de fatos disponíveis
- Context Builder — Busca dados de sistemas externos
- Rule Engine — Avalia expressões contra fatos
- Decision Execution — Registra todas as decisões (auditoria)

### Alert Context

**Responsabilidade:** Criar e gerenciar alertas gerados pelo Decision Engine.

Contém:
- Alertas com state machine (OPEN → UNDER_REVIEW → CLOSED | FALSE_POSITIVE)
- Consulta para analistas
- Idempotência (1 alerta por transação + regra)

---

## Conceitos do Decision Context

### Entity (Entidade)

Uma **Entity** representa uma entidade de negócio do mundo real que possui dados relevantes para decisões. Cada Entity:
- Agrupa um conjunto de Facts relacionados
- Identifica um **sourceSystem** (sistema de onde os dados vêm)
- É consultada por um ou mais Fact Resolvers

**Exemplos:**

| Entity | Display Name | Source System | Facts |
|--------|-------------|--------------|-------|
| Risk | Risco | PLD | customerRisk |
| Screening | Screening | Screening | keywordMatched |
| Customer | Cliente | Cadastro | pep, segment, country |
| Transaction | Transação | Core Banking | amount, currency, channel |

**Quem cria:** Engenharia (via migration SQL ou INSERT direto).
**Tabela:** `entity_definition`

---

### Fact (Fato)

Um **Fact** é um dado contextual tipado que representa o **estado observado** de algo no momento da avaliação. Facts:
- São **dados de leitura** (nunca comandos ou ações)
- Possuem **tipo** que determina quais operadores são válidos
- Pertencem a uma **Entity**
- São obtidos por **Fact Resolvers** (componentes de código)

**Exemplos:**

| Fact | Tipo | Entity | Significado |
|------|------|--------|-------------|
| keywordMatched | BOOLEAN | Screening | "A transação teve keyword match?" |
| customerRisk | ENUM (BR/MR/AR) | Risk | "Qual o nível de risco do cliente?" |
| pep | BOOLEAN | Customer | "O cliente é Pessoa Politicamente Exposta?" |
| amount | NUMBER | Transaction | "Qual o valor da transação?" |

**Tipos disponíveis:**

| Tipo | Valor aceito | Exemplo |
|------|-------------|---------|
| BOOLEAN | `true` ou `false` | keywordMatched: true |
| ENUM | String (valor do enum) | customerRisk: "AR" |
| NUMBER | Decimal | amount: 15000.00 |
| STRING | Texto livre | country: "BR" |
| MONEY | Objeto {amount, currency} | value: {amount: 1000, currency: "BRL"} |

**Quem cria:** Engenharia (via migration SQL + implementação do Fact Resolver).
**Tabela:** `fact_definition`

---

### Fact Resolver

Um **Fact Resolver** é um componente de código que **busca** dados de um sistema externo e os transforma em Facts tipados. Cada Resolver:
- Conhece apenas **seu domínio** (ex.: CustomerResolver só busca dados de cliente)
- Retorna `emptyList()` se falhar (falha silenciosa — fact fica ausente)
- É invocado automaticamente pelo Context Builder quando o fact é necessário

**Resolvers do MVP:**

| Resolver | Entity | Fact produzido | Fonte |
|----------|--------|---------------|-------|
| ScreeningResolver | Screening | keywordMatched | Extrai do DetectionEvent (sem chamada externa) |
| CustomerResolver | Risk | customerRisk | REST call para sistema de Cadastro |

**Quem cria:** Engenharia (código Kotlin + bean registration).

---

### Rule Definition (Definição de Regra)

Uma **Rule Definition** é o template técnico de uma regra de PLD. Define:
- **Quais facts** o analista pode usar nas configurações
- **Quais ações** estão disponíveis (GENERATE_ALERT, IGNORE, REVIEW, BLOCK)
- **Contexto** e **categoria** para organização

Rule Definitions são:
- ❌ **Não editáveis** por analistas
- ✅ Criadas e mantidas pela engenharia
- ✅ Estáticas — evoluem com deploys

**Exemplo:**

```
Rule: KEYWORD_SCREENING
- Contexto: SCREENING
- Categoria: KEYWORD_SCREENING
- Facts suportados: keywordMatched, customerRisk
- Ações suportadas: GENERATE_ALERT, IGNORE
- Status: ACTIVE
```

**Quem cria:** Engenharia (via migration SQL).
**Tabela:** `rule_definition`

---

### Rule Configuration (Configuração de Regra)

Uma **Rule Configuration** é a configuração operacional que o **analista** cria para definir **quando** gerar alertas. Contém:
- **Expressions** — Lista de condições (semântica AND)
- **Actions** — O que fazer quando todas as condições são satisfeitas
- **Versioning** — Cada edição cria nova versão (imutável)
- **Estado** — draft (não ativa) ou active (avaliando transações)

**Ciclo de vida:**

```
┌────────┐     editar      ┌────────┐     dry-run     ┌─────────┐     ativar     ┌────────┐
│ Criar  │ ─────────────► │ Draft  │ ──────────────► │ Testada │ ─────────────► │ Active │
│ (v1)   │                 │ (vN)   │                 │         │                │        │
└────────┘                 └────────┘                 └─────────┘                └────────┘
```

**Regras:**
- Máximo 10 expressions por configuração
- Apenas **uma** configuração ativa por regra
- Dry-run **obrigatório** antes de ativar
- Cada update cria nova versão (monotonicamente crescente)

**Quem cria:** Analista (via API REST).
**Tabela:** `rule_configuration`

---

### Expression (Expressão)

Uma **Expression** é uma condição atômica que compara um Fact contra um valor esperado:

```
factName + operator + expectedValue
```

**Exemplo:**
```
keywordMatched EQUALS true         → "Houve keyword match?"
customerRisk GREATER_THAN_OR_EQUAL MR  → "Risco do cliente é médio ou alto?"
```

**Semântica AND implícita:** Quando uma configuração tem múltiplas expressions, **TODAS** devem ser satisfeitas para que a decisão seja ALERT. Se qualquer uma falhar → IGNORE.

**Operadores MVP:**

| Operador | Significado | Tipos compatíveis |
|----------|-------------|-------------------|
| EQUALS | Valor é igual ao esperado | BOOLEAN, ENUM, STRING, NUMBER |
| NOT_EQUALS | Valor é diferente do esperado | BOOLEAN, ENUM, STRING, NUMBER |
| GREATER_THAN_OR_EQUAL | Valor é >= esperado (por ordinal) | ENUM (CustomerRisk: BR < MR < AR) |

---

### Decision (Decisão)

O resultado da avaliação de uma Rule Configuration:

| Decisão | Quando | Efeito |
|---------|--------|--------|
| **ALERT** | Todas as expressions satisfeitas | Executa as actions configuradas |
| **IGNORE** | Pelo menos uma expression falhou | Nenhuma ação (apenas registro) |

Decisões futuras (pós-MVP): REVIEW (enviar para revisão manual), BLOCK (bloquear transação).

---

### Action (Ação)

O que acontece quando a decisão é ALERT:

| Ação | Efeito |
|------|--------|
| GENERATE_ALERT | Cria um Alert no Alert Context com status OPEN |
| IGNORE | Nenhum efeito (registro apenas) |

Ações futuras: REVIEW (criar caso para revisão), BLOCK (bloquear transação).

---

### Decision Execution (Execução)

Um **registro imutável** de cada decisão tomada pelo Decision Engine. Contém:
- Todos os facts avaliados (snapshot do contexto)
- Quais expressions foram satisfeitas/falhas
- Decisão tomada e ações executadas
- Explicação estruturada em 7 etapas (auditoria COAF/BACEN)
- Tempo de execução e traceId para correlação

**Características:**
- ✅ Imutável — nunca alterado após criação
- ✅ Idempotente — apenas 1 execução por (transactionId, ruleId)
- ✅ Registra IGNORE também (toda decisão é auditada)

**Tabela:** `decision_execution`

---

### Dry-Run (Simulação)

O **Dry-Run** permite testar uma configuração com facts fornecidos manualmente, **sem** produzir side effects:

| O que FAZ | O que NÃO faz |
|-----------|---------------|
| ✅ Avalia expressions com o mesmo motor de produção | ❌ Não gera alertas |
| ✅ Retorna decisão (ALERT/IGNORE) + detalhamento | ❌ Não persiste execução na auditoria |
| ✅ Persiste DryRunLog (para habilitar ativação) | ❌ Não publica eventos |
| ✅ Funciona para configs draft E active | ❌ Não invoca Fact Resolvers |

**Obrigatório antes de ativar** — garante que o analista validou o comportamento.

---

### Alert (Alerta)

Um **Alert** é gerado automaticamente quando o Decision Engine decide ALERT com action GENERATE_ALERT. O analista gerencia alertas via state machine:

```
┌──────┐             ┌──────────────┐             ┌────────┐
│ OPEN │ ──────────► │ UNDER_REVIEW │ ──────────► │ CLOSED │
│      │             │              │             │        │
└──────┘             └──────┬───────┘             └────────┘
                            │
                            │             ┌────────────────┐
                            └───────────► │ FALSE_POSITIVE │
                                          │                │
                                          └────────────────┘
```

**Transições válidas:**
- OPEN → UNDER_REVIEW (analista inicia revisão)
- UNDER_REVIEW → CLOSED (confirmado suspeito)
- UNDER_REVIEW → FALSE_POSITIVE (falso positivo)

**Idempotência:** Apenas 1 alerta por (transactionId, ruleId).

---

## Fluxo Completo (End-to-End)

```
1. Transação PIX recebida (description: "transferencia lavagem dinheiro")

2. Screening Context (MF09):
   → Normaliza descrição
   → Busca termos no cache
   → Match: "lavagem" (AML)
   → Publica DetectionEvent

3. Decision Context:
   → Recebe DetectionEvent
   → Verifica idempotência (TX + ruleId)
   → Carrega Rule Configuration ativa para KEYWORD_SCREENING
   → Context Builder:
     • ScreeningResolver → keywordMatched = true (do evento)
     • CustomerResolver → customerRisk = AR (REST call ao Cadastro)
   → Rule Engine avalia:
     • keywordMatched EQUALS true → ✅ satisfeita
     • customerRisk GTE MR → AR >= MR → ✅ satisfeita
   → Decisão: ALERT (todas satisfeitas)
   → Persiste DecisionExecution (auditoria)
   → Publica DecisionMadeEvent

4. Alert Context:
   → Recebe DecisionMadeEvent com action GENERATE_ALERT
   → Cria Alert com status OPEN
   → Analista pode consultar e gerenciar via API
```

---

## Quem faz o quê?

| Ação | Responsável | Como |
|------|-------------|------|
| Criar Entity | Engenharia | Migration SQL |
| Criar Fact | Engenharia | Migration SQL + Fact Resolver |
| Criar Rule Definition | Engenharia | Migration SQL |
| Implementar Fact Resolver | Engenharia | Código Kotlin |
| Criar Rule Configuration | Analista | API REST |
| Editar Configuration | Analista | API REST |
| Executar Dry-Run | Analista | API REST |
| Ativar Configuration | Analista | API REST |
| Consultar Execuções | Analista | API REST |
| Gerenciar Alertas | Analista | API REST |

---

## Documentação Relacionada

| Doc | Propósito |
|-----|-----------|
| [User Guide](user-guide.md) | Como o analista usa a API (passo a passo com curl) |
| [Technical Guide](technical-guide.md) | Como a engenharia expande o catálogo (SQL + código) |
| [OpenAPI Spec](../pld-transaction-screening/src/main/resources/static/openapi/openapi.yaml) | Contrato formal da API REST |
| [ADRs](adr/) | Decisões arquiteturais |
| [Requirements](./../.kiro/specs/decision-engine/requirements.md) | Requisitos detalhados |
| [Design](./../.kiro/specs/decision-engine/design.md) | Design técnico |
