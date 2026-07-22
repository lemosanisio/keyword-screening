# Arquitetura e Fluxos — PLD Workbench

Status: protótipo funcional (Marcos 0–11 concluídos)  
Última atualização: Julho 2026

---

## 1. Visão de sistema

```
                            ┌─────────────────────────────┐
                            │     Sistemas Mestres        │
                            │  (Cadastro, Contas, Core)   │
                            └──────────┬──────────────────┘
                                       │ Eventos (simulados):
                                       │ CustomerOnboardingStarted
                                       │ CustomerDataChanged
                                       │ PartyRelationshipChanged
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                         SQS (LocalStack)                                      │
│  pld-transaction-signals       pld-transaction-signals-dlq                    │
└───────┬──────────────────────────────────────┬───────────────────────────────┘
        │                                      │
        │ TransactionSignalDetected.v1         │ CustomerRiskProfileUpdated.v1
        │ ManualReviewRequested.v2             │
        ▼                                      ▼
┌───────────────────────┐          ┌───────────────────────────────────────────┐
│ pld-customer-analysis │◄─────────│     pld-transaction-screening             │
│      (8082)           │  REST    │            (8080)                          │
│                       │  risco   │                                            │
│ • Party + Snapshot    │          │ • Keyword Screening (MF09)                 │
│ • Case + Sources      │          │ • Contextual Screening (LLM)               │
│ • Evidence (adapters) │          │ • Decision Engine (multi-rule)              │
│ • Decisions + Approve │          │ • TransactionEvaluation aggregate          │
│ • Dossiê + COAF      │          │ • Outbox → SQS                             │
│ • Timeline            │          │ • Projeção local de risco                  │
│ • Revalidação         │          │ • Alert legado (desativável)               │
│ • Relações PF/PJ     │          │                                            │
│ • Onboarding consumer │          │ APIs: 18 endpoints REST                    │
│ • Pesquisa            │          │ Banco: keyword_screening (Postgres 5432)   │
│                       │          │                                            │
│ Banco: customer_      │          └───────────────────────────────────────────┘
│   analysis (PG 5433)  │
└───────────┬───────────┘
            │ REST (BFF)
            ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                      pld-workbench (5173)                                      │
│                                                                               │
│  React 18 + TanStack Query + Tailwind + shadcn/ui                             │
│                                                                               │
│  Telas:                                                                       │
│  • /queue          — Fila única de casos                                      │
│  • /cases/:id      — Workspace do caso (3 colunas)                            │
│  • /admin/rules    — Administração de regras (RULE_ADMIN)                     │
│                                                                               │
│  Componentes:                                                                 │
│  • PartySummary (risco, segmentos, vigência)                                  │
│  • RelationshipsPanel (relações tipadas)                                      │
│  • SignalsCard (avaliação M7: purpose, status, fatos, explicação)              │
│  • EvidenceRequirementMatrix (status por requirement, retry)                  │
│  • EvidenceDetailsPanel (sanções, processos, mídia com badge IA)              │
│  • DecisionPanel (suspicion + account, dual approval)                         │
│  • DossierPanel (gerar, manifesto, gaps, versões)                             │
│  • CoafPanel (workflow DRAFT→ACKNOWLEDGED, prazo, protocolo)                  │
│  • CaseTimeline + CaseCommentsPanel                                           │
│  • GlobalSearch (debounce, party/case por ID ou nome)                         │
│  • RulesAdminPage (catálogo, configs, dry-run, ativação)                      │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Bounded Contexts e responsabilidades

### pld-transaction-screening

| Contexto | Responsabilidade | Modelo principal |
|----------|-----------------|------------------|
| **Screening** | Detectar termos restritos e analisar contexto via LLM | `RestrictedTerm`, `ContextualScreeningAudit`, `HistoricalDecision` |
| **Decision** | Avaliar regras configuráveis, resolver facts, produzir decisões | `RuleDefinition`, `RuleConfiguration`, `DecisionExecution`, `FactDefinition` |
| **Evaluation** | Aggregate imutável da avaliação transacional reproduzível | `TransactionEvaluation`, `TransactionIdentity` |
| **Alert** | Alertas legados (em fase de desativação) | `Alert` com state machine |
| **Integration** | Outbox transacional, relay SQS, projeção de risco | `IntegrationOutbox`, `CustomerRiskProjection` |

### pld-customer-analysis

| Módulo | Responsabilidade | Modelo principal |
|--------|-----------------|------------------|
| **Party** | Identidade PF/PJ, snapshots, relações | `PartyEntity`, `PartySnapshot`, `PartyRelationship` |
| **Analysis** | Ciclos de análise (onboarding, revalidação, event-driven) | `AnalysisCycle` |
| **Case Management** | Fila humana, atribuição, colaboração, decisões | `CaseEntity`, `CaseSource`, `SuspicionDecision`, `AccountDecision` |
| **Evidence** | Adapters de fontes, matriz de requisitos, prontidão decisória | `EvidenceCollection`, `AnalysisRequirement`, `SourceExecution` |
| **Dossier** | Geração de dossiê, comunicação COAF | `Dossier`, `CoafCommunication` |
| **Integration** | Outbox, inbox SQS, consumer de sinais, consumer de onboarding | `OutboxEvent`, `InboxEvent` |
| **Timeline** | Trilha de auditoria regulatória | `TimelineEntry` |
| **Identity/Access** | Resolução de ator (simulada via headers) | `Actor`, `ActorRole` |

---

## 3. Fluxos de negócio

### 3.1 Avaliação transacional (tempo real)

```
1. Transação PIX recebida (POST /v1/rules/keyword-screening/evaluate)
2. Keyword Screening: normaliza descrição, busca match em termos restritos
3. Se match:
   a. DetectionEvent publicado internamente (Spring Events)
   b. Decision Engine resolve facts:
      - keywordMatched = true (do screening)
      - customerRisk = projeção local ou REST fallback
   c. Avalia expressions da configuração ativa
   d. Resultado: ALERT ou IGNORE
4. TransactionEvaluation aggregate criado:
   - Snapshot canônico (JCS + SHA-256)
   - Ruleset congelado (versão de cada regra)
   - Facts com quality (PRESENT/UNKNOWN/STALE/ERROR)
   - Estado: COMPLETED / INDETERMINATE / FAILED
5. Outbox listener produz eventos:
   - TransactionEvaluationCompleted.v2 (sempre)
   - TransactionSignalDetected.v1 (se SIGNAL_RAISED + partyId tipado)
   - ManualReviewRequested.v2 (se reviewRequired)
6. Drain scheduler publica no SQS
7. Alert legado criado (modo LEGACY) ou suprimido (modo MANUAL_REVIEW_LIVE)
```

**Idempotência:** mesma transação + ruleset + snapshot → retorna resultado existente sem reprocessar.

**Quarentena:** intake sem identidade estável ou snapshot vazio → `screening_quarantine`, nenhum aggregate criado.

### 3.2 Abertura de caso (assíncrono)

```
1. pld-customer-analysis consome SQS (poller scheduled)
2. Valida envelope (producer, schema, deduplicação por eventId)
3. Verifica se Party existe (pelo partyId no evento)
4. Abre ou agrupa caso:
   - Se caso aberto existe para a mesma party/policy → agrupa (source anexada)
   - Se não → cria caso OPEN
5. Projeta avaliação, sinais e pedido de revisão
6. Registra na timeline
7. Caso aparece na fila do Workbench
```

**Tolerância:** duplicidade e reorder de mensagens SQS Standard não criam caso duplicado.

### 3.3 Análise e decisão humana

```
1. Analista assume caso (OPEN → ASSIGNED)
2. Inicia análise (ASSIGNED → IN_ANALYSIS)
3. Revisão de evidências:
   - Adapters executam automaticamente ao criar coleção
   - Policy define quais adapters por riskLevel
   - Fontes que falharam podem ser retentadas
   - Prontidão decisória: allowed=true somente quando todos obrigatórios satisfeitos
4. Decisão de suspeição: COMUNICAR / NAO_COMUNICAR + justificativa
5. Decisão de conta: MANTER / RESTRINGIR / SUSPENDER / ENCERRAR (se aplicável)
6. Se decisão sensível → vai para PENDING_APPROVAL
7. Segundo analista (APPROVER) aprova ou rejeita
8. Caso decidido (DECIDED)
```

### 3.4 Dossiê e COAF

```
1. Analista solicita geração de dossiê
2. Sistema coleta: party summary, evidence matrix, decisions, timeline, signals
3. Detecta gaps (evidências pendentes, decisões ausentes)
4. Dossiê READY com manifesto versionado + hash
5. Analista cria comunicação COAF:
   - Pre-preenche campos do dossiê
   - Edita narrativa
   - Submete para revisão (PENDING_REVIEW)
6. Aprovador revisa e aprova (APPROVED)
7. Envio ao adapter mock:
   - 80% → ACKNOWLEDGED (protocolo COAF-2026-XXXXXX)
   - 10% → REJECTED (retificação necessária)
   - 10% → PENDING (retry automático)
8. Timeline registra todas as transições
```

### 3.5 Onboarding e revalidação

```
Onboarding:
1. Evento CustomerOnboardingStarted (via dev endpoint ou SQS futuro)
2. Cria Party + snapshot + perfil de risco (mock derivado do nome)
3. Abre AnalysisCycle tipo ONBOARDING
4. Publica CustomerRiskProfileUpdated.v1

Revalidação periódica:
1. Scheduler (cron) verifica parties com revisão vencida
2. LOW: vence em 365 dias / MEDIUM: 180 / HIGH: 90
3. Se vencida e sem ciclo aberto → abre PERIODIC_REVIEW
4. Executa adapters de evidência conforme policy do risco

Revalidação event-driven:
1. Mudança material (país, PEP) detectada via CustomerDataChanged
2. Abre AnalysisCycle tipo EVENT_DRIVEN_REVIEW
3. Coalescência: se ciclo já aberto, não duplica
```

### 3.6 Administração de regras

```
1. RULE_ADMIN acessa /admin/rules
2. Catálogo mostra regras disponíveis (KEYWORD_SCREENING, etc.)
3. Cria configuração draft (expressions + actions)
4. Executa dry-run com facts manuais → verifica decisão
5. Ativa configuração (com pending_activation para maker-checker futuro)
6. Configuração ativa usada em avaliações futuras
7. Versão anterior preservada no histórico
```

---

## 4. Modelo de dados (resumo)

### pld-transaction-screening (18 tabelas)

| Tabela | Propósito |
|--------|-----------|
| `restricted_term` | Termos restritos por categoria (TERRORISM, AML, FRAUD...) |
| `rule_execution` | Idempotência do keyword screening |
| `contextual_screening_audit` | Auditoria da análise contextual (LLM) |
| `historical_decision` | Decisões do analista para few-shot LLM |
| `entity_definition` | Catálogo de entidades (Customer, Transaction) |
| `fact_definition` | Catálogo de fatos (keywordMatched, customerRisk) |
| `rule_definition` | Catálogo de regras |
| `rule_configuration` | Configurações do analista (expressions, actions, versions) |
| `configuration_version` | Histórico de versões de configuração |
| `decision_execution` | Execuções de decisão (imutável, com evaluationId) |
| `dry_run_log` | Log de simulações |
| `alert` | Alertas legados |
| `transaction_evaluation` | Aggregate reproduzível M7 |
| `transaction_evaluation_execution` | Vínculo avaliação ↔ execuções |
| `transaction_identity` | Mapeamento ID externo → txn_ULID interno |
| `integration_outbox` | Outbox transacional (eventos para SQS) |
| `screening_quarantine` | Intakes inválidos rejeitados |
| `customer_risk_projection` | Cache local do perfil de risco |

### pld-customer-analysis (14+ tabelas)

| Tabela | Propósito |
|--------|-----------|
| `party` | Identidade PF/PJ com riskLevel e última revisão |
| `party_snapshot` | Versões do cadastro |
| `party_relationship` | Relações tipadas entre parties |
| `analysis_cycle` | Ciclos de análise (onboarding, revalidação, alerta) |
| `pld_case` | Casos da fila humana |
| `case_source` | Sinais/fontes anexados ao caso |
| `case_comment` | Comentários do analista |
| `suspicion_decision` | Decisões de suspeição |
| `account_decision` | Decisões de conta/relacionamento |
| `evidence_collection` | Coleção de evidências por caso |
| `analysis_requirement` | Requisitos da policy de evidência |
| `source_execution` | Execuções de adapters com proveniência |
| `evidence_record` | Evidências produzidas |
| `fact_version` | Fatos extraídos das evidências |
| `dossier` / `dossier_section` | Dossiê interno versionado |
| `coaf_communication` / `coaf_communication_event` | Workflow COAF |
| `outbox_event` | Outbox para publicação de eventos |
| `timeline_entry` | Trilha de auditoria regulatória |
| `revalidation_policy` | Política de intervalo de revisão por risco |

---

## 5. Padrões arquiteturais

| Padrão | Onde | Propósito |
|--------|------|-----------|
| **Hexagonal Architecture** | pld-transaction-screening | Domain puro, ports/adapters, inversão de dependência |
| **Transactional Outbox** | Ambos backends | Garantir at-least-once sem two-phase commit |
| **Inbox/Deduplicação** | pld-customer-analysis | Idempotência no consumo de eventos |
| **Advisory Locks** | Avaliação transacional | Serializar avaliações concorrentes da mesma transação |
| **Snapshot Canônico** | TransactionEvaluation | Reprodutibilidade (JCS + SHA-256) |
| **Tri-state Facts** | Decision Engine | Distinguir PRESENT/UNKNOWN/STALE/ERROR |
| **Maker-Checker** | Decisões sensíveis | Dual approval (ANALYST propõe, APPROVER confirma) |
| **Evidence Policy** | Adapters | Requirements obrigatórios variam por riskLevel |
| **CQRS leve** | Cases/Timeline | Commands mutam estado, queries projetam views |
| **Spring Application Events** | Entre bounded contexts | Comunicação intra-JVM sem acoplamento direto |

---

## 6. Integrações e contratos

### Eventos publicados por pld-transaction-screening

| Evento | Versão | Gatilho | Consumidor |
|--------|--------|---------|------------|
| `TransactionEvaluationCompleted` | v2 | Toda avaliação LIVE | customer-analysis (projeção) |
| `TransactionSignalDetected` | v1 | SIGNAL_RAISED + partyId | customer-analysis (caso) |
| `ManualReviewRequested` | v2 | reviewRequired=true | customer-analysis (caso v2) |

### Eventos publicados por pld-customer-analysis

| Evento | Versão | Gatilho | Consumidor |
|--------|--------|---------|------------|
| `CustomerRiskProfileUpdated` | v1 | Criação de Party | transaction-screening (projeção local) |
| `PartyCreated` | v1 | Criação de Party | — |
| `CaseStatusChanged` | v1 | Transições de caso | — |
| `AnalysisCycleCreated` | v1 | Abertura de ciclo | — |

### APIs REST externas

| API | Provedor | Uso | Status |
|-----|----------|-----|--------|
| coaf-analyzer | Serviço LLM | Análise contextual | Mock (MockWebServer em testes) |
| Customer Risk REST | Sistema cadastro | Fallback se projeção ausente | Mock (retorna null em prod) |
| COAF Submission | Regulador | Envio de comunicação | Mock (MockCoafSubmissionAdapter) |
| Evidence Sources (4) | Bureaus/Listas | Consultas de evidência | Mock (adapters simulados) |

---

## 7. Configuração e deploy

### Variáveis de ambiente principais

```bash
# Motor transacional
PLD_SQS_ENABLED=true                    # Habilitar publicação SQS
PLD_OUTBOX_DRAIN_ENABLED=true           # Scheduler de drain do outbox
PLD_RISK_PROFILE_CONSUMER_ENABLED=true  # Consumir projeção de risco
EVALUATION_CASE_TRIGGER_MODE=LEGACY     # LEGACY | MANUAL_REVIEW_LIVE

# Customer analysis
PLD_INTEGRATION_SQS_INBOUND_ENABLED=true  # Consumir sinais transacionais
PLD_INTEGRATION_OUTBOX_DRAIN_ENABLED=true # Publicar eventos do outbox
```

### Portas

| Serviço | Porta | Banco |
|---------|-------|-------|
| pld-transaction-screening | 8080 | localhost:5432/keyword_screening |
| pld-customer-analysis | 8082 | localhost:5433/customer_analysis |
| pld-workbench | 5173 | — (SPA consome APIs) |
| LocalStack (SQS) | 4566 | — |

---

## 8. O que muda para produção

### Substituições diretas (adapter por adapter)

| Simulação atual | Produção | Esforço estimado |
|-----------------|----------|------------------|
| MockCoafSubmissionAdapter | Webservice/lote COAF real | Médio (contrato definido) |
| CreditBureauAdapter (mock) | API bureau real (Serasa, etc.) | Médio |
| SanctionsListsAdapter (mock) | API de listas reais (OFAC, PEP) | Médio |
| LegalProceedingsAdapter (mock) | DataJud/tribunais | Alto (múltiplas fontes) |
| NegativeMediaAdapter (mock) | Fornecedor de mídia | Médio |
| DevActorSwitcher (headers) | SSO/OAuth + RBAC real | Alto |
| OnboardingDevController | Consumer SQS real de sistema mestre | Baixo (infra pronta) |
| `Thread.sleep` nos adapters | Latência real da rede | Zero (remover sleep) |

### Mudanças arquiteturais necessárias

| Mudança | Razão |
|---------|-------|
| Broker real (SQS produtivo ou Kafka) | Substituir LocalStack |
| Observabilidade (Prometheus + Grafana) | Métricas, alertas, dashboards |
| Schema Registry | Validação de contratos em runtime |
| Feature flags | Rollout gradual de adapters reais |
| Calendário de feriados | Prazo COAF em dias úteis reais |
| Multi-tenancy (se aplicável) | Isolamento por instituição |
| Backup/DR | Replicação, restore |
| Rate limiting / circuit breaker | Resiliência com fontes externas |

### O que NÃO muda

- Modelo de domínio (aggregates, events, value objects)
- Hexagonal architecture e inversão de dependência
- Contratos de eventos (schemas v1/v2 já definidos)
- Fluxo de decisão (expressions, facts, tri-state)
- Outbox/inbox pattern
- Frontend (apenas trocar URL base e autenticação)
- Timeline regulatória
- Idempotência e reprodutibilidade da avaliação

---

## 9. Métricas do protótipo

| Métrica | Valor |
|---------|-------|
| Testes unitários (transaction-screening) | 15.028 |
| Testes de integração (transaction-screening) | 165 |
| Testes (customer-analysis) | 36 |
| Endpoints REST (transaction-screening) | 18 |
| Migrations Flyway (transaction-screening) | 18 |
| Migrations Flyway (customer-analysis) | 14 |
| Componentes React (workbench) | ~25 |
| Telas (workbench) | 3 |
| Bounded contexts (transaction-screening) | 5 |
| Módulos (customer-analysis) | 9 |
| Adapters de evidência | 4 |
| Eventos versionados | 7 tipos |
| ADRs documentados | 10 |
| Marcos executados | 12 (0–11) |
