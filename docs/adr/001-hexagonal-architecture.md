# ADR-001: Hexagonal Architecture (Ports & Adapters)

## Status

Aceita

## Contexto

O sistema de screening precisa evoluir para suportar múltiplos bounded contexts (Screening, Decision, Alert) mantendo baixo acoplamento entre camadas e facilitando testabilidade sem dependência de infraestrutura.

## Decisão

Adotamos Hexagonal Architecture (Ports & Adapters) com DDD como base arquitetural para todos os bounded contexts.

### Estrutura de pacotes

```
br.com.<context>/
├── domain/         # Núcleo — zero dependências de framework
│   ├── model/
│   ├── port/       # Output ports (driven interfaces)
│   ├── service/    # Domain services puros (sem @Component)
│   └── exception/
├── application/    # Orquestração (input ports + implementações @Service)
│   ├── usecase/
│   └── service/
└── infrastructure/ # Tudo fora do hexágono
    ├── input/      # Adapters de entrada (HTTP, Event Listeners)
    └── output/     # Adapters de saída (JPA, REST, Event Publisher)
```

### Regras de dependência

- `domain` NÃO importa Spring, JPA, ou qualquer framework
- `domain/service` são classes puras registradas como beans via `@Configuration`
- `application` pode usar `@Service`, `@Transactional`
- `infrastructure` implementa interfaces de `domain` e `application`

## Consequências

- Domínio testável sem mocks de framework
- Troca de infraestrutura (ex.: JPA → JDBC, REST → gRPC) sem alterar domínio
- Curva de aprendizado inicial para novos desenvolvedores
