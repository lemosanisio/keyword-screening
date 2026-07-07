# Visão Geral — Rule Platform PLD

Plataforma de regras para **Prevenção à Lavagem de Dinheiro (PLD)** que detecta condições suspeitas em transações PIX e decide quando gerar alertas para analistas de compliance.

## Arquitetura

O sistema é composto por 3 bounded contexts na mesma JVM (mono-deployment):

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          Rule Platform (Spring Boot)                             │
│                                                                                 │
│  ┌─────────────────┐    DetectionEvent    ┌──────────────────┐   DecisionMade  │
│  │  Screening      │ ──────────────────► │  Decision        │ ─────────────►  │
│  │  Context        │                      │  Context         │                 │
│  │                 │                      │                  │   ┌───────────┐ │
│  │  • MF09 Keyword │                      │  • Rule Engine   │   │  Alert    │ │
│  │  • Contextual   │                      │  • Fact Registry │──►│  Context  │ │
│  │    (LLM)        │                      │  • Dry-Run       │   │           │ │
│  └─────────────────┘                      └──────────────────┘   └───────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Bounded Contexts

| Contexto | Responsabilidade |
|----------|-----------------|
| **Screening** | Detecta condições suspeitas (keywords, análise contextual via LLM) |
| **Decision** | Avalia regras configuráveis e decide ações (ALERT/IGNORE) |
| **Alert** | Cria e gerencia alertas com state machine |

## Stack

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Kotlin 1.9, JVM 21 |
| Framework | Spring Boot 3.3 (Web MVC, Data JPA, Validation) |
| Banco | PostgreSQL 16, JSONB via hypersistence-utils |
| Migrations | Flyway (idempotentes) |
| Testes | JUnit 5 (@RepeatedTest para PBT), MockK, Testcontainers |
| API | OpenAPI 3.0.3 (API First) + Swagger UI |
| Build | Gradle (Kotlin DSL) + OpenAPI Generator |
| Cobertura | JaCoCo (98% linhas, 85% branches) |

## Estrutura de Pacotes

```
br.com
├── shared/domain/              # DomainEvent, DomainException, Value Objects
├── screening/                  # Screening Context (MF09 + Contextual)
│   ├── domain/                 # Zero dependências de framework
│   ├── application/            # Use cases, @Service, @Transactional
│   └── infrastructure/         # Controllers, JPA, adapters
├── decision/                   # Decision Context (Rule Engine)
│   ├── domain/
│   ├── application/
│   └── infrastructure/
└── alert/                      # Alert Context
    ├── domain/
    ├── application/
    └── infrastructure/
```

Cada contexto segue **Hexagonal Architecture** (Ports & Adapters) — ver [ADR-001](adr/001-hexagonal-architecture.md).

## Fluxo Principal

```
1. PIX recebida → POST /v1/rules/keyword-screening/evaluate
2. Keyword match? → DetectionEvent publicado internamente
3. Decision Engine consome:
   • Busca CustomerRisk via REST (Cadastro)
   • Avalia: keywordMatched=true AND customerRisk>=MR?
   • Resultado: ALERT ou IGNORE
4. Se ALERT → Alert criado com status OPEN
5. Analista consulta: GET /v1/alerts
```

## Modelo de Dados

| Tabela | Contexto | Propósito |
|--------|----------|-----------|
| `restricted_term` | Screening | Termos restritos para keyword matching |
| `rule_execution` | Screening | Idempotência do keyword screening |
| `contextual_screening_audit` | Screening | Auditoria da análise contextual |
| `historical_decision` | Screening | Decisões do analista (few-shot LLM) |
| `entity_definition` | Decision | Catálogo de entidades de negócio |
| `fact_definition` | Decision | Catálogo de fatos disponíveis |
| `rule_definition` | Decision | Catálogo de regras |
| `rule_configuration` | Decision | Configurações do analista |
| `configuration_version` | Decision | Histórico de versões |
| `decision_execution` | Decision | Auditoria de decisões (imutável) |
| `dry_run_log` | Decision | Log de simulações |
| `alert` | Alert | Alertas gerados |
