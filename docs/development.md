# Desenvolvimento

## Pré-requisitos

- JDK 21
- Docker (para PostgreSQL via Testcontainers e dev local)

## Setup Local

```bash
# Subir banco
docker compose -f docker/docker-compose.yml up postgres -d

# Rodar aplicação
./gradlew bootRun
```

Acessa: http://localhost:8080
Swagger UI: http://localhost:8080/swagger-ui/index.html

## Comandos

```bash
# Testes unitários + Property-Based (exclui integração)
./gradlew test

# Testes de integração (requer Docker)
./gradlew integrationTest

# Build completo
./gradlew build

# Gerar interfaces do OpenAPI (automático antes de compilar)
./gradlew openApiGenerate

# Relatório de cobertura (HTML em build/reports/jacoco/test/html/)
./gradlew jacocoTestReport

# Verificar thresholds de cobertura (falha se abaixo de 98% linhas / 85% branches)
./gradlew check

# Limpar cache corrompido
rm -rf build/ .gradle/
```

## Testes

| Categoria | Quantidade | Framework |
|-----------|-----------|-----------|
| Property-Based | 12+ | JUnit 5 @RepeatedTest (200 iterações) |
| Unit | 100+ | JUnit 5 + MockK |
| Controller (MockMvc) | 30+ | JUnit 5 + MockMvc |
| Integration | 10+ | JUnit 5 + Testcontainers |

### Cobertura (JaCoCo)

- **Linhas:** 98% mínimo
- **Branches:** 85% mínimo

Exclusões: código gerado (OpenAPI), configurações Spring, interfaces (ports/use cases), data classes puras, DTOs, enums sem lógica, JPA entities, repository implementations.

Relatório: `build/reports/jacoco/test/html/index.html`

## Configuração

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/keyword_screening
    username: postgres
    password: postgres

decision:
  customer-risk:
    url: http://localhost:8081
    timeout-ms: 5000

coaf:
  analyzer:
    base-url: http://localhost:8080
    timeout-seconds: 30

contextual-screening:
  auto-close-threshold: 0.95
```

### Variáveis de Ambiente

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/keyword_screening` | URL do banco |
| `SERVER_PORT` | 8080 | Porta da aplicação |
| `DECISION_CUSTOMER_RISK_URL` | `http://localhost:8081` | URL do sistema de Cadastro |
| `COAF_ANALYZER_BASE_URL` | `http://localhost:8080` | URL do coaf-analyzer (LLM) |

## Convenções

- Código em inglês, documentação em português
- Hexagonal Architecture (Ports & Adapters)
- API-First: interfaces geradas a partir de `openapi.yaml`
- Domain services puros (registrados via @Configuration)
- Idempotência via UNIQUE constraints no banco
- Jackson: ISO-8601, fail-on-unknown-properties: false
