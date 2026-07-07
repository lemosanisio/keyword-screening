# ADR-003: Property-Based Testing com JUnit 5

## Status

Substituída (anteriormente usava Kotest; migrado para JUnit 5 com @RepeatedTest)

## Contexto

O Decision Engine possui lógica complexa de avaliação de expressões, validação de configurações e idempotência que precisa ser verificada com garantias mais fortes que testes baseados em exemplos.

## Decisão

Utilizamos JUnit 5 com `@RepeatedTest` e geradores aleatórios em Kotlin (`kotlin.random.Random`) para property-based testing. Cada propriedade é executada 200 vezes com inputs gerados aleatoriamente.

### Abordagem

- `@RepeatedTest(200)` — 200 iterações por propriedade
- Geradores aleatórios escritos em Kotlin puro (sem dependência adicional)
- Assertions via JUnit 5 (`org.junit.jupiter.api.Assertions`)
- MockK para mocking

### Propriedades implementadas

12+ property tests cobrindo: avaliação de expressões, semântica AND, context builder seletivo, idempotência, dry-run parity, validação de configuração, monotonicity de versão, explicação completa, serialização round-trip, validação de eventos, ativação com dry-run, e geração de alertas.

## Consequências

- Cobertura mais forte que testes unitários tradicionais
- Descobre edge cases não previstos manualmente
- Sem dependências adicionais de framework de teste
- Geradores simples e idiomáticos em Kotlin
