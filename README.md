# Keyword Screening

Sistema de screening de transações PIX que identifica termos restritos em descrições de transações, detectando possíveis indícios de terrorismo, lavagem de dinheiro (AML), fraude, crime financeiro e sanções.

## Stack

- **Kotlin 1.9** + **Spring Boot 3.3**
- **PostgreSQL 16** (JSONB para resultados)
- **Flyway** (migrations de banco)
- **Kotest** (Property-Based Testing)
- **Testcontainers** (testes de integração)
- **Docker** + **Docker Compose**

## Arquitetura

O projeto segue DDD (Domain-Driven Design) com as seguintes camadas:

```
br.com.screening
├── domain          # Modelos, value objects, interfaces de repositório, domain services
├── application     # Use cases, services, cache, comandos/resultados
├── infrastructure  # JPA entities, mappers, repositório impl, scheduler, config
└── presentation    # Controller REST, DTOs, exception handler
```

### Fluxo de Processamento

```
POST /v1/rules/keyword-screening/evaluate
        │
        ▼
  Verificar idempotência (KEYWORD_SCREENING:{transactionId})
        │
        ├── Existe resultado → retornar resultado salvo
        │
        ▼
  Normalizar descrição (lowercase → remove acentos → remove especiais → compacta espaços)
        │
        ▼
  Buscar termos no cache em memória
        │
        ▼
  Identificar matches
        │
        ▼
  Persistir resultado (rule_execution)
        │
        ▼
  Retornar resposta
```

## Quick Start com Docker

### Pré-requisitos

- Docker e Docker Compose instalados

### Subir a aplicação

```bash
docker compose up -d
```

Isso sobe:
- **PostgreSQL 16** na porta `5432` (db: `keyword_screening`, user: `postgres`, pass: `postgres`)
- **Aplicação** na porta `8081`

### Verificar se está rodando

```bash
curl http://localhost:8081/v1/rules/keyword-screening/evaluate \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "PIX-001",
    "description": "Pagamento para terrorismo internacional"
  }'
```

Resposta esperada:
```json
{
  "ruleCode": "KEYWORD_SCREENING",
  "matched": true,
  "matches": [
    { "term": "terrorismo", "category": "TERRORISM" }
  ]
}
```

### Parar a aplicação

```bash
docker compose down
```

### Parar e remover dados persistidos

```bash
docker compose down -v
```

## Desenvolvimento Local (sem Docker para a app)

### Pré-requisitos

- JDK 21
- Docker (apenas para PostgreSQL)

### Subir apenas o banco

```bash
docker compose up postgres -d
```

### Rodar a aplicação

```bash
./gradlew bootRun
```

A aplicação sobe na porta `8080` por padrão (quando não há `SERVER_PORT` configurado).

### Rodar testes unitários e PBT

```bash
./gradlew test --tests '!*.integration.*'
```

### Rodar testes de integração (requer Docker)

```bash
./gradlew test --tests '*.integration.*'
```

### Rodar todos os testes

```bash
./gradlew test
```

## API

### POST /v1/rules/keyword-screening/evaluate

Avalia uma descrição de transação PIX contra termos restritos.

**Request:**
```json
{
  "transactionId": "PIX-123456",
  "description": "Pagamento para al qaeda"
}
```

**Response (com match):**
```json
{
  "ruleCode": "KEYWORD_SCREENING",
  "matched": true,
  "matches": [
    { "term": "al qaeda", "category": "TERRORISM" }
  ]
}
```

**Response (sem match):**
```json
{
  "ruleCode": "KEYWORD_SCREENING",
  "matched": false,
  "matches": []
}
```

**Validações (HTTP 400):**
- `transactionId` ausente, vazio ou apenas espaços
- `description` ausente ou vazia
- `description` maior que 140 caracteres

**Idempotência:**
- Mesma `transactionId` retorna o resultado persistido sem re-executar a regra

## Normalização de Texto

A descrição é normalizada antes da busca:

| Etapa | Exemplo |
|-------|---------|
| Lowercase | `AL QAEDA` → `al qaeda` |
| Remove acentos | `ação` → `acao` |
| Remove especiais | `al-qaeda!!!` → `al qaeda` |
| Compacta espaços | `al   qaeda` → `al qaeda` |

## Modelo de Dados

### restricted_term

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| id | BIGSERIAL | PK |
| term | VARCHAR(255) | Termo normalizado |
| category | VARCHAR(50) | TERRORISM, AML, FRAUD, FINANCIAL_CRIME, SANCTIONS |
| active | BOOLEAN | Se o termo está ativo para matching |
| created_at | TIMESTAMP | Criação |
| updated_at | TIMESTAMP | Última atualização |

### rule_execution

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| id | BIGSERIAL | PK |
| transaction_id | VARCHAR(100) | ID da transação |
| rule_code | VARCHAR(20) | KEYWORD_SCREENING |
| result | JSONB | ScreeningResult serializado |
| created_at | TIMESTAMP | Momento da execução |

Constraint: `UNIQUE(transaction_id, rule_code)` — garante idempotência.

## Cache de Termos

- Termos ativos são carregados em memória no startup da aplicação
- Atualização periódica a cada 5 minutos via `@Scheduled`
- Se o banco ficar indisponível após a carga inicial, o cache continua servindo os termos anteriores
- Termos são armazenados já normalizados no cache

## Termos Seed (pré-carregados)

| Termo | Categoria |
|-------|-----------|
| terrorismo | TERRORISM |
| financiamento ao terror | TERRORISM |
| lavagem de dinheiro | AML |
| ocultar origem | AML |
| fraude | FRAUD |
| estelionato | FRAUD |
| crime financeiro | FINANCIAL_CRIME |
| desvio de verbas | FINANCIAL_CRIME |
| sancao | SANCTIONS |
| embargo | SANCTIONS |

## Testes

### Estratégia de Testes

O projeto usa uma abordagem dual:

1. **Testes unitários** — exemplos concretos por componente
2. **Property-Based Testing (PBT)** — 11 propriedades formais validadas com 1000 iterações cada

### Propriedades Verificadas por PBT

| # | Propriedade | Componente |
|---|-------------|-----------|
| P1 | Normalização é idempotente | TextNormalizer |
| P2 | Resultado normalizado é ASCII minúsculo | TextNormalizer |
| P3 | Detecção invariante a variações de formatação | KeywordMatcher |
| P4 | Ausência de termos implica matched=false | KeywordMatcher |
| P5 | Termos inativos não produzem matches | KeywordMatcher |
| P6 | Termos no cache estão normalizados | RestrictedTermsCache |
| P7 | Idempotência de avaliação | KeywordScreeningService |
| P8 | Round-trip de persistência do ScreeningResult | RuleExecutionMapper |
| P9 | Requisições válidas retornam HTTP 200 | Controller |
| P10 | transactionId em branco retorna HTTP 400 | Controller |
| P11 | description vazia retorna HTTP 400 | Controller |

### Testes de Integração

Requerem Docker (Testcontainers):
- End-to-end com banco real
- Race condition na persistência (threads concorrentes)
- Reload do cache após mudança no banco
- Idempotência com banco real (PBT com 20 iterações)

## Configuração

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/keyword_screening
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
  jackson:
    serialization:
      write-dates-as-timestamps: false
```

### Variáveis de Ambiente (Docker)

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| SPRING_DATASOURCE_URL | jdbc:postgresql://localhost:5432/keyword_screening | URL do banco |
| SPRING_DATASOURCE_USERNAME | postgres | Usuário do banco |
| SPRING_DATASOURCE_PASSWORD | postgres | Senha do banco |
| SERVER_PORT | 8080 | Porta da aplicação |

## Extensibilidade

O projeto é projetado para suportar múltiplas regras de screening via a interface `ScreeningRule`:

```kotlin
interface ScreeningRule {
    val ruleCode: String
    fun evaluate(transactionId: String, description: String): ScreeningResult
}
```

O **Contextual Screening** (segunda camada, em desenvolvimento) utiliza LLM para reduzir falsos positivos gerados pelo Keyword Screening.
