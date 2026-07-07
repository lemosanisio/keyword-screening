# ADR-005: API First com OpenAPI 3.1

## Status

Aceita

## Contexto

A API REST do Decision Engine será consumida por analistas via UI e por integrações entre sistemas. É necessário um contrato formal que sirva como fonte de verdade, habilite geração de código client-side, e facilite documentação.

## Decisão

Adotamos abordagem **API First**:

1. O contrato OpenAPI 3.1 (`docs/api/openapi.yaml`) é a fonte de verdade para endpoints, schemas, e respostas
2. Controllers são implementados manualmente mas devem aderir ao contrato
3. Qualquer alteração de API começa pela atualização do spec antes do código

### Localização

```
src/main/resources/openapi/openapi.yaml   # Contrato OpenAPI 3.1 completo (dentro do classpath)
```

### Cobertura do spec

- Decision Context: Rule Catalog, Fact Registry, Rule Configuration, Dry-Run, Decision Execution
- Alert Context: Alerts (CRUD + state machine)
- Error responses padronizados (`ErrorResponse`)

### Evolução futura

- Geração de código server-side (springdoc-openapi ou openapi-generator)
- Geração de SDKs client-side
- Contract testing automatizado (Pact ou Spring Cloud Contract)

## Consequências

- Contrato explícito para consumidores da API
- Documentação sempre atualizada (spec = implementação)
- Overhead de manutenção do spec manual (até adotar code-generation)
- Habilita Swagger UI para exploração interativa
