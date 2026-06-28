# Project Structure

## Arquitetura

Hexagonal Architecture (Ports & Adapters) com DDD. `infrastructure` = tudo que está fora do hexágono.

## Layout de Pacotes

```
br.com
├── shared/                              # Compartilhado entre todos os contextos
│   └── domain/                          # DomainException (base para 422)
│
└── screening/                           # Contexto de screening
    ├── domain/                          # Núcleo — zero dependências de framework
    │   ├── model/                       # Entidades, value objects, enums
    │   ├── exception/                   # Exceções de domínio específicas do contexto
    │   ├── port/                        # Output ports (driven) — LlmClassifierPort, repositories
    │   ├── repository/                  # Output ports (driven) — RestrictedTermRepository, etc.
    │   └── service/                     # Domain services puros (sem @Component)
    │
    ├── application/                     # Orquestração (input ports + implementações)
    │   ├── usecase/                     # Input port interfaces + Commands + Results
    │   ├── service/                     # Implementações dos use cases (@Service)
    │   └── cache/                       # Cache em memória
    │
    └── infrastructure/                  # Tudo fora do hexágono
        ├── input/
        │   └── http/                    # Input adapter: REST
        │       ├── dto/                 # Request/Response DTOs
        │       └── handler/             # Exception handlers (@ControllerAdvice)
        ├── output/
        │   ├── persistence/             # Output adapter: JPA
        │   │   ├── entity/              # @Entity classes
        │   │   ├── mapper/              # Mapeadores domain ↔ entity
        │   │   └── repository/          # Spring Data JPA interfaces
        │   ├── llm/                     # Output adapter: API coaf-analyzer
        │   └── scheduler/               # Output adapter: jobs agendados
        └── configuration/               # @Configuration, @ConfigurationProperties
```

## Convenções de Nomenclatura

| Tipo | Padrão | Exemplo |
|------|--------|---------|
| Use Case Interface (input port) | `Verbo + Substantivo + UseCase` | `EvaluateKeywordScreeningUseCase` |
| Use Case Impl | Nome de domínio + `Service` | `KeywordScreeningService` |
| Command (input) | `Verbo + Substantivo + Command` | `EvaluateKeywordScreeningCommand` |
| Result (output) | `Verbo + Substantivo + Result` | `EvaluateKeywordScreeningResult` |
| DTO Request | `Substantivo + Request` | `EvaluateKeywordScreeningRequest` |
| DTO Response | `Substantivo + Response` | `EvaluateKeywordScreeningResponse` |
| JPA Entity | `Substantivo + Entity` | `RuleExecutionEntity` |
| JPA Repository | `Substantivo + JpaRepository` | `RestrictedTermJpaRepository` |
| Repository Impl | `Substantivo + RepositoryImpl` | `RestrictedTermRepositoryImpl` |
| Mapper | `Substantivo + Mapper` | `RestrictedTermMapper` |
| Port (output interface) | `Substantivo + Port` ou `Substantivo + Repository` | `LlmClassifierPort` |
| Adapter (impl) | `Substantivo + Adapter` | `CoafAnalyzerAdapter` |

## Testes — Organização

```
src/test/kotlin/br/com/screening/
├── domain/service/                              # PBT + unit tests dos domain services
├── application/                                 # Unit tests dos use cases/services
│   ├── cache/
│   └── service/
├── infrastructure/
│   ├── input/http/                              # Controller tests (MockMvc)
│   └── output/
│       ├── llm/                                 # Adapter tests (MockWebServer)
│       ├── persistence/mapper/                  # Mapper tests
│       └── scheduler/                           # Scheduler tests
├── integration/                                 # End-to-end com Testcontainers
└── KotestConfig.kt                              # Configuração global Kotest (PBT 1000 iterações)
```

## Regras de Dependência

- `domain` NÃO importa Spring, JPA, ou qualquer framework
- `domain/service` são classes puras — registradas como beans via `@Configuration` em `infrastructure/configuration`
- `application` depende de `domain`, pode usar Spring annotations (`@Service`, `@Transactional`)
- `infrastructure` implementa interfaces de `domain` e `application`
- `infrastructure/input` depende de `application` (use cases), nunca de `infrastructure/output`
- DTOs de request/response ficam em `infrastructure/input/http/dto`, NUNCA no domínio
- `@ConfigurationProperties` em `infrastructure/configuration` para configurações tipadas
