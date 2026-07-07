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
| JUnit 5 | Test runner + @RepeatedTest para Property-Based Testing (200 iterações) |
| MockK | Mocking |
| Testcontainers | PostgreSQL para testes de integração |
| MockWebServer (OkHttp) | Mock de APIs HTTP externas |

## Cobertura (JaCoCo)

- Branch coverage mínimo: 85%
- Line coverage mínimo: 98%
- Exclusões: código gerado, data classes puras, interfaces (ports/use cases), configurações, enums, exceções, entities JPA, repository impls

## Comandos

```bash
# Subir banco de dados local
docker compose -f docker/docker-compose.yml up postgres -d

# Rodar aplicação
./gradlew bootRun

# Testes unitários + PBT (default task, integração excluída automaticamente)
./gradlew test

# Testes de integração (requer Docker)
./gradlew integrationTest

# Build do JAR (inclui verificação JaCoCo)
./gradlew build

# Gerar interfaces a partir do OpenAPI spec (executado automaticamente antes da compilação)
./gradlew openApiGenerate
```

## API-First (OpenAPI Generator)

- Spec: `src/main/resources/static/openapi/openapi.yaml`
- Gerador: `kotlin-spring` com `interfaceOnly=true`
- Pacotes gerados: `br.com.generated.api` (interfaces) e `br.com.generated.model` (DTOs)
- Output: `build/generated/openapi/src/main/kotlin`
- Controllers implementam as interfaces geradas — não editar código em `build/generated/`

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
