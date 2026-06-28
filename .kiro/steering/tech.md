# Tech Stack & Build

## Stack Principal

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Kotlin 1.9, JVM 21 |
| Framework | Spring Boot 3.3 (Web MVC, Data JPA, Validation) |
| Banco de dados | PostgreSQL 16, JSONB via hypersistence-utils |
| Migrations | Flyway |
| Serialização | Jackson (kotlin module + jsr310) |
| Build | Gradle (Kotlin DSL) |

## Testes

| Ferramenta | Uso |
|-----------|-----|
| Kotest | Test runner + Property-Based Testing (1000 iterações padrão) |
| MockK | Mocking |
| Testcontainers | PostgreSQL para testes de integração |
| MockWebServer (OkHttp) | Mock de APIs HTTP externas |

## Comandos

```bash
# Subir banco de dados local
docker compose -f docker/docker-compose.yml up postgres -d

# Rodar aplicação
./gradlew bootRun

# Todos os testes
./gradlew test

# Apenas testes unitários e PBT
./gradlew test --tests '!*.integration.*'

# Apenas testes de integração (requer Docker)
./gradlew test --tests '*.integration.*'

# Build do JAR
./gradlew build
```

## Configuração Local

- **Porta padrão**: 8080 (sem SERVER_PORT)
- **Banco**: `jdbc:postgresql://localhost:5432/keyword_screening` (user/pass: `postgres/postgres`)
- **Flyway**: migrations automáticas no startup

## Dependências Externas

- **coaf-analyzer** (localhost:8080): API REST para análise contextual via LLM. Usada pelo Contextual Screening.
  - Detalhes no steering global `openapi-client-integration.md`

## Convenções Jackson

- Datas serializadas como ISO-8601 (não timestamps)
- `fail-on-unknown-properties: false` para resiliência
- Módulos: `JavaTimeModule` + `KotlinModule`
