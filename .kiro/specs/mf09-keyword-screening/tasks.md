# Implementation Plan: MF09 — Keyword Screening

## Overview

Implementação da regra de Keyword Screening em Kotlin + Spring Boot, seguindo arquitetura DDD em camadas (domain → application → infrastructure → presentation). A execução é feita em etapas incrementais: estrutura base, camada de domínio, camada de aplicação, infraestrutura (JPA, cache, scheduler, migrations), camada de apresentação (REST), tratamento de erros e testes de propriedade e integração.

O projeto usa **Kotest Property Testing** para PBT, **Testcontainers + PostgreSQL** para testes de integração e **Flyway** para migrations.

---

## Tasks

- [x] 1. Estrutura do projeto e configuração base
  - [x] 1.1 Configurar `build.gradle.kts` com todas as dependências necessárias
    - Adicionar dependências: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `flyway-core`, `postgresql`, `jackson-module-kotlin`, `jackson-datatype-jsr310`
    - Adicionar dependências de test: `kotest-runner-junit5:5.9.1`, `kotest-property:5.9.1`, `kotest-extensions-spring:1.3.0`, `testcontainers-postgresql`, `mockk`, `spring-boot-starter-test`
    - Adicionar plugin `hypersistence-utils` (ou `hibernate-types`) para suporte a `JSONB` via `@Type(JsonType::class)`
    - Configurar task de test com `useJUnitPlatform()`
    - _Requirements: 3.4, 6.1_

  - [x] 1.2 Criar configuração global do Kotest
    - Criar `src/test/kotlin/.../KotestConfig.kt` com `AbstractProjectConfig` definindo `propertyCheckIterations = 1000`
    - _Requirements: Design — Testing Strategy_

  - [x] 1.3 Criar `application.yml` com configurações de datasource, JPA, Flyway e scheduling
    - Configurar `spring.datasource` (PostgreSQL), `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=true`
    - Habilitar `@EnableScheduling` na classe principal ou em `@Configuration`
    - Configurar `spring.jackson.serialization.write-dates-as-timestamps=false` e `JavaTimeModule`
    - _Requirements: 4.3, 6.1_

- [x] 2. Camada de domínio — modelos, value objects e interfaces
  - [x] 2.1 Criar enum `Category` e value objects `MatchResult` e `ScreeningResult`
    - Criar `Category.kt` com valores: `TERRORISM, AML, FRAUD, FINANCIAL_CRIME, SANCTIONS`
    - Criar `MatchResult.kt` como `data class(term: String, category: Category)`
    - Criar `ScreeningResult.kt` como `data class(ruleCode: String, matched: Boolean, matches: List<MatchResult>)`
    - _Requirements: 1.2, 1.3, 1.4_

  - [x] 2.2 Criar entidade de domínio `RestrictedTerm` e aggregate root `RuleExecution`
    - Criar `RestrictedTerm.kt` com campos `id, term, category, active, createdAt, updatedAt`
    - Criar `RuleExecution.kt` com campos `id?, transactionId, ruleCode, result, createdAt` e propriedade computada `idempotencyKey`
    - _Requirements: 5.6_

  - [x] 2.3 Criar interfaces de repositório de domínio
    - Criar `RestrictedTermRepository.kt` com método `findAllActive(): List<RestrictedTerm>`
    - Criar `RuleExecutionRepository.kt` com métodos `findByTransactionIdAndRuleCode` e `save`
    - _Requirements: 3.1, 5.1, 5.3_

  - [x] 2.4 Criar interface `ScreeningRule`
    - Criar `ScreeningRule.kt` com `val ruleCode: String` e `fun evaluate(transactionId, description): ScreeningResult`
    - _Requirements: 1.1, Design — Extensibilidade_

- [x] 3. Camada de domínio — serviços de domínio
  - [x] 3.1 Implementar `TextNormalizer`
    - Criar `TextNormalizer.kt` anotado com `@Component`
    - Implementar `normalize(text: String): String` com pipeline sequencial: `toLowerCase → removeAccents (NFD + regex [^\p{ASCII}]) → removeSpecialChars (regex [^a-z0-9 ]) → compactSpaces (regex \s+ → " " + trim)`
    - Garantir que `normalize(normalize(s)) == normalize(s)` (idempotência)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x] 3.2 Escrever testes unitários para `TextNormalizer` (exemplos concretos)
    - Testar: `"Lavagem" → "lavagem"`, `"café" → "cafe"`, `"R$100,00" → "r 100 00"`, `"  dois  espaços  " → "dois espacos"`, string vazia
    - _Requirements: 2.1–2.5_

  - [x] 3.3 Escrever property test para `TextNormalizer` — Property 1: Normalização é idempotente
    - **Property 1: `normalize(normalize(s)) == normalize(s)` para todo `Arb.string()`**
    - **Validates: Requirements 2.5, 2.6**
    - Tag de anotação: `// Feature: mf09-keyword-screening, Property 1: Normalização é idempotente`
    - _Requirements: 2.5, 2.6_

  - [x] 3.4 Escrever property test para `TextNormalizer` — Property 2: Conteúdo normalizado é ASCII minúsculo
    - **Property 2: resultado de `normalize(s)` satisfaz `Regex("[a-z0-9 ]*")` e não contém espaços duplos**
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4**
    - Tag de anotação: `// Feature: mf09-keyword-screening, Property 2: Texto normalizado está em minúsculas e sem acentos nem caracteres especiais`
    - _Requirements: 2.1–2.4_

  - [x] 3.5 Implementar `KeywordMatcher`
    - Criar `KeywordMatcher.kt` anotado com `@Component`
    - Implementar `findMatches(normalizedDescription: String, activeTerms: Set<RestrictedTerm>): List<MatchResult>`
    - Iterar sobre `activeTerms` onde `active=true`; verificar se `normalizedDescription.contains(term.term)`; coletar `MatchResult(term.term, term.category)`
    - _Requirements: 3.2, 3.3, 3.5_

  - [x] 3.6 Escrever testes unitários para `KeywordMatcher`
    - Testar: match exato, match como substring, múltiplos matches, sem match, termos inativos ignorados, lista vazia de termos
    - _Requirements: 3.2, 3.3, 3.5_

  - [x] 3.7 Escrever property test para `KeywordMatcher` — Property 3: Detecção invariante a variações de formatação
    - **Property 3: para qualquer conjunto de termos ativos e descrição contendo ao menos um desses termos (com variações de caixa/acentuação), `matched=true` e o termo está nos matches**
    - **Validates: Requirements 1.1, 1.2, 2.6, 3.2, 3.3**
    - Usar `Arb.list(Arb.term())` e injetar termos com variações na descrição
    - _Requirements: 1.1, 1.2, 3.2, 3.3_

  - [x] 3.8 Escrever property test para `KeywordMatcher` — Property 4: Ausência de termos implica matched=false
    - **Property 4: para qualquer descrição que não contenha os termos ativos, `matched=false` e `matches.isEmpty()`**
    - **Validates: Requirements 1.3, 3.1**
    - _Requirements: 1.3, 3.1_

  - [x] 3.9 Escrever property test para `KeywordMatcher` — Property 5: Termos inativos não produzem matches
    - **Property 5: para qualquer descrição com apenas termos `active=false`, `matched=false` e `matches.isEmpty()`**
    - **Validates: Requirements 3.5**
    - _Requirements: 3.5_

- [x] 4. Camada de infraestrutura — migrations de banco de dados
  - [x] 4.1 Criar migration Flyway `V1__create_restricted_term.sql`
    - Criar tabela `restricted_term(id BIGSERIAL PK, term VARCHAR(255) NOT NULL, category VARCHAR(50) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)`
    - Criar índice `idx_restricted_term_active ON restricted_term(active)`
    - Caminho: `src/main/resources/db/migration/V1__create_restricted_term.sql`
    - _Requirements: 4.1, 4.2_

  - [x] 4.2 Criar migration Flyway `V2__create_rule_execution.sql`
    - Criar tabela `rule_execution(id BIGSERIAL PK, transaction_id VARCHAR(100) NOT NULL, rule_code VARCHAR(20) NOT NULL, result JSONB NOT NULL, created_at TIMESTAMP NOT NULL)`
    - Criar constraint `CONSTRAINT uk_rule_execution UNIQUE(transaction_id, rule_code)`
    - Criar índice `idx_rule_execution_lookup ON rule_execution(transaction_id, rule_code)`
    - Caminho: `src/main/resources/db/migration/V2__create_rule_execution.sql`
    - _Requirements: 5.6_

  - [x] 4.3 Criar migration Flyway `V3__seed_restricted_terms.sql` com dados iniciais de exemplo
    - Inserir ao menos 5 termos restritos ativos (categorias: TERRORISM, AML, FRAUD, FINANCIAL_CRIME, SANCTIONS) com termos já normalizados
    - Caminho: `src/main/resources/db/migration/V3__seed_restricted_terms.sql`
    - _Requirements: 4.1_

- [x] 5. Camada de infraestrutura — JPA entities e mappers
  - [x] 5.1 Criar JPA entity `RestrictedTermEntity`
    - Criar `RestrictedTermEntity.kt` com `@Entity @Table(name = "restricted_term")`
    - Campos: `id (IDENTITY)`, `term`, `category (@Enumerated(STRING))`, `active`, `createdAt`, `updatedAt`
    - _Requirements: 4.1, 4.6_

  - [x] 5.2 Criar JPA entity `RuleExecutionEntity`
    - Criar `RuleExecutionEntity.kt` com `@Entity @Table(name = "rule_execution", uniqueConstraints = [...])`
    - Campo `result` com `@Type(JsonType::class) @Column(columnDefinition = "jsonb")`
    - _Requirements: 5.4, 5.6_

  - [x] 5.3 Criar mappers entre entities e modelos de domínio
    - Criar `RestrictedTermMapper.kt`: `RestrictedTermEntity → RestrictedTerm` e inverso
    - Criar `RuleExecutionMapper.kt`: `RuleExecutionEntity → RuleExecution` (desserializando o JSONB para `ScreeningResult`) e `RuleExecution → RuleExecutionEntity` (serializando `ScreeningResult` para JSON)
    - Usar `ObjectMapper` injetado para serialização/deserialização do campo `result`
    - _Requirements: 5.4, 5.6_

  - [x] 5.4 Escrever property test para mappers — Property 8: Round-trip de persistência do ScreeningResult
    - **Property 8: para qualquer `ScreeningResult`, `deserialize(serialize(result)) == result`**
    - **Validates: Requirements 5.4, 5.6**
    - Usar `Arb.screeningResult()` com geradores customizados para `MatchResult` e `Category`
    - _Requirements: 5.4, 5.6_

- [x] 6. Camada de infraestrutura — implementações de repositório
  - [x] 6.1 Criar Spring Data JPA interface `RestrictedTermJpaRepository`
    - Criar `RestrictedTermJpaRepository : JpaRepository<RestrictedTermEntity, Long>` com query `findAllByActiveTrue()`
    - _Requirements: 4.1_

  - [x] 6.2 Criar Spring Data JPA interface `RuleExecutionJpaRepository`
    - Criar `RuleExecutionJpaRepository : JpaRepository<RuleExecutionEntity, Long>` com método `findByTransactionIdAndRuleCode(transactionId: String, ruleCode: String): RuleExecutionEntity?`
    - _Requirements: 5.1, 5.3_

  - [x] 6.3 Implementar `RestrictedTermRepositoryImpl`
    - Criar `RestrictedTermRepositoryImpl.kt` anotado com `@Repository`
    - Implementar `RestrictedTermRepository` delegando para `RestrictedTermJpaRepository` e usando `RestrictedTermMapper`
    - _Requirements: 3.1, 4.1_

  - [x] 6.4 Implementar `RuleExecutionRepositoryImpl`
    - Criar `RuleExecutionRepositoryImpl.kt` anotado com `@Repository`
    - Implementar `RuleExecutionRepository` delegando para `RuleExecutionJpaRepository` e usando `RuleExecutionMapper`
    - Tratar `DataIntegrityViolationException` no `save` (race condition): ao capturar, buscar o registro existente
    - _Requirements: 5.3, 5.5_

- [x] 7. Checkpoint — Verificar estrutura base e migrations
  - Garantir que a aplicação sobe com banco PostgreSQL via Testcontainers
  - Verificar que as migrations Flyway são aplicadas sem erros
  - Garantir que as entities JPA mapeiam corretamente as tabelas
  - Perguntar ao usuário se há ajustes antes de prosseguir com application layer.

- [x] 8. Camada de aplicação — cache e scheduler
  - [x] 8.1 Implementar `RestrictedTermsCache`
    - Criar `RestrictedTermsCache.kt` anotado com `@Component`
    - Campo `@Volatile private var terms: Set<RestrictedTerm> = emptySet()`
    - Método `@PostConstruct fun initialize()`: chama `restrictedTermRepository.findAllActive()`, normaliza cada `term.term` via `textNormalizer.normalize()`, atribui ao campo `terms`; lança exceção (fail-fast) se banco indisponível
    - Método `fun reload()`: mesma lógica de `initialize()` mas captura exceção, loga erro e mantém `terms` atual sem alteração
    - Método `fun getActiveTerms(): Set<RestrictedTerm>` retorna snapshot imutável
    - _Requirements: 4.1, 4.2, 4.4, 4.5, 4.6_

  - [x] 8.2 Escrever testes unitários para `RestrictedTermsCache`
    - Testar: inicialização carrega termos normalizados; reload substitui cache; falha no reload mantém cache anterior; banco indisponível no startup lança exceção
    - _Requirements: 4.1, 4.4, 4.5_

  - [x] 8.3 Escrever property test para `RestrictedTermsCache` — Property 6: Termos no cache estão normalizados
    - **Property 6: para qualquer conjunto de `RestrictedTerm` carregados no cache, `∀ t ∈ cache: t.term == normalize(t.term)`**
    - **Validates: Requirements 4.6**
    - _Requirements: 4.6_

  - [x] 8.4 Implementar `RestrictedTermsReloadScheduler`
    - Criar `RestrictedTermsReloadScheduler.kt` anotado com `@Component`
    - Método `@Scheduled(fixedDelay = 300_000) fun reload()` que chama `restrictedTermsCache.reload()`
    - Adicionar `@EnableScheduling` na classe de configuração ou na `@SpringBootApplication`
    - _Requirements: 4.3_

  - [x] 8.5 Escrever testes unitários para `RestrictedTermsReloadScheduler`
    - Testar que `reload()` delega para `restrictedTermsCache.reload()` usando MockK
    - _Requirements: 4.3_

- [x] 9. Camada de aplicação — idempotência e serviço principal
  - [x] 9.1 Implementar `IdempotencyService`
    - Criar `IdempotencyService.kt` anotado com `@Service`
    - Método `findExisting(transactionId, ruleCode): ScreeningResult?`: delega para `ruleExecutionRepository.findByTransactionIdAndRuleCode`; retorna `existing.result` ou `null`
    - Método `persist(transactionId, ruleCode, result): ScreeningResult`: tenta `ruleExecutionRepository.save(...)`; ao capturar `DataIntegrityViolationException`, recupera registro existente e retorna seu `result`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [x] 9.2 Escrever testes unitários para `IdempotencyService`
    - Testar: execução existente retorna resultado salvo; execução nova persiste e retorna; race condition — `DataIntegrityViolationException` capturada e resultado existente retornado
    - _Requirements: 5.1, 5.2, 5.5_

  - [x] 9.3 Implementar `KeywordScreeningService`
    - Criar `KeywordScreeningService.kt` anotado com `@Service`
    - Implementar `EvaluateKeywordScreeningUseCase` e `ScreeningRule` com `ruleCode = "KEYWORD_SCREENING"`
    - Método `execute(command)`:
      1. Verificar idempotência via `idempotencyService.findExisting(transactionId, ruleCode)`; se encontrado, retornar resultado imediatamente
      2. Normalizar `command.description` via `textNormalizer.normalize()`
      3. Buscar termos via `restrictedTermsCache.getActiveTerms()`
      4. Executar matches via `keywordMatcher.findMatches(normalizedDescription, terms)`
      5. Construir `ScreeningResult` com `matched = matches.isNotEmpty()`
      6. Persistir via `idempotencyService.persist(transactionId, ruleCode, result)`
      7. Mapear para `EvaluateKeywordScreeningResult` e retornar
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 5.3, 5.4_

  - [x] 9.4 Escrever testes unitários para `KeywordScreeningService`
    - Testar fluxo completo com mocks (MockK): caminho com idempotência ativa, caminho novo sem idempotência, resultado com matches, resultado sem matches
    - _Requirements: 1.1, 1.2, 1.3, 5.1, 5.2_

  - [x] 9.5 Escrever property test para `KeywordScreeningService` — Property 7: Idempotência de avaliação
    - **Property 7: para qualquer `transactionId` e `description` válidos, duas chamadas com os mesmos parâmetros retornam resultados idênticos e apenas 1 `RuleExecution` existe no banco**
    - **Validates: Requirements 5.1, 5.2, 5.3**
    - Usar banco em memória (H2) ou Testcontainers para verificar count de `RuleExecution`
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 10. Camada de apresentação — controller e DTOs
  - [x] 10.1 Criar DTOs de request e response
    - Criar `EvaluateKeywordScreeningRequest.kt` com `@field:NotBlank transactionId: String?` e `@field:NotBlank @field:Size(max=140) description: String?`
    - Criar `EvaluateKeywordScreeningResponse.kt` com `ruleCode, matched, matches: List<MatchResultResponse>`
    - Criar `MatchResultResponse.kt` com `term: String, category: String`
    - _Requirements: 6.1, 6.3, 6.4, 6.5_

  - [x] 10.2 Implementar `KeywordScreeningController`
    - Criar `KeywordScreeningController.kt` com `@RestController @RequestMapping("/v1/rules/keyword-screening")`
    - Método `@PostMapping("/evaluate") fun evaluate(@Valid @RequestBody request, ...)` retornando `ResponseEntity<EvaluateKeywordScreeningResponse>`
    - Mapear `EvaluateKeywordScreeningCommand` a partir do request e `EvaluateKeywordScreeningResult` para o response
    - _Requirements: 6.1, 6.2_

  - [x] 10.3 Implementar `GlobalExceptionHandler`
    - Criar `GlobalExceptionHandler.kt` com `@RestControllerAdvice`
    - Handler para `MethodArgumentNotValidException` → HTTP 400 com `ErrorResponse` contendo campos inválidos
    - Handler genérico para `Exception` → HTTP 500 com `ErrorResponse`
    - Criar `ErrorResponse.kt` com `timestamp: Instant, status: Int, error: String, message: String`
    - _Requirements: 6.3, 6.4, Error Handling table_

  - [x] 10.4 Escrever testes unitários para `KeywordScreeningController` (MockMvc ou WebMvcTest)
    - Testar: request válida → 200 com body correto; `transactionId` ausente/em branco → 400; `description` ausente/vazia → 400; `description` > 140 chars → 400
    - _Requirements: 6.2, 6.3, 6.4, 6.5_

  - [x] 10.5 Escrever property test para controller — Property 9: Requisições válidas retornam HTTP 200
    - **Property 9: para qualquer `transactionId` não vazio e `description` não vazia (até 140 chars), resposta é HTTP 200 com `ruleCode="KEYWORD_SCREENING"`**
    - **Validates: Requirements 1.4, 6.1, 6.2**
    - Usar `@WebMvcTest` com mock do use case
    - _Requirements: 1.4, 6.1, 6.2_

  - [x] 10.6 Escrever property test para controller — Property 10: transactionId em branco retorna HTTP 400
    - **Property 10: para qualquer string composta só de espaços (incluindo vazia) como `transactionId`, resposta é HTTP 400**
    - **Validates: Requirements 6.3**
    - Usar `Arb.stringPattern("\\s*")` incluindo string vazia
    - _Requirements: 6.3_

  - [x] 10.7 Escrever property test para controller — Property 11: description vazia retorna HTTP 400
    - **Property 11: para qualquer requisição com `description` ausente ou vazia, resposta é HTTP 400**
    - **Validates: Requirements 6.4**
    - _Requirements: 6.4_

- [x] 11. Checkpoint — Verificar todos os testes unitários e de propriedade
  - Garantir que todos os testes unitários e de propriedade passam
  - Garantir que a aplicação compila sem erros
  - Perguntar ao usuário se há ajustes antes de prosseguir com testes de integração.

- [x] 12. Testes de integração
  - [x] 12.1 Escrever teste de integração end-to-end com Testcontainers
    - Usar `@SpringBootTest(webEnvironment = RANDOM_PORT)` com PostgreSQL via Testcontainers
    - Testar fluxo completo: POST `/v1/rules/keyword-screening/evaluate` → 200 com resultado correto
    - Testar idempotência: segunda chamada com mesmo `transactionId` retorna resultado idêntico
    - Verificar que apenas 1 registro `rule_execution` é criado no banco
    - _Requirements: 1.1, 1.2, 1.3, 5.1, 5.2, 6.2_

  - [x] 12.2 Escrever teste de integração para `RestrictedTermsCache` com banco real
    - Verificar reload após mudança no banco (inserir novo termo, chamar `reload()`, verificar presença no cache)
    - Verificar que termos estão normalizados após carga
    - _Requirements: 4.1, 4.2, 4.3, 4.6_

  - [x] 12.3 Escrever teste de integração para race condition na persistência
    - Disparar dois threads concorrentes com o mesmo `transactionId`
    - Verificar que apenas 1 `RuleExecution` é persistida no banco (constraint UNIQUE)
    - Verificar que ambos os threads recebem o mesmo `ScreeningResult`
    - _Requirements: 5.5_

  - [x] 12.4 Escrever property test de integração — Property 7 com banco real (Testcontainers)
    - **Property 7: idempotência verificada com banco real — duas execuções com o mesmo `transactionId` retornam resultados idênticos e `COUNT(rule_execution) = 1`**
    - **Validates: Requirements 5.1, 5.2, 5.3**
    - _Requirements: 5.1–5.3_

- [x] 13. Checkpoint final — Garantir que todos os testes passam
  - Executar suite completa: testes unitários, de propriedade e de integração
  - Verificar que a aplicação sobe corretamente com PostgreSQL e termos no cache
  - Garantir que o endpoint responde dentro do SLA de 10ms para descrições de até 140 caracteres
  - Perguntar ao usuário se há ajustes antes de considerar a implementação concluída.

---

## Notes

- Tarefas marcadas com `*` são opcionais e podem ser puladas para um MVP mais rápido; todas as demais são obrigatórias.
- A ordem das tarefas garante que nenhum código fique órfão: cada componente é integrado ao anterior antes de avançar.
- As properties PBT são numeradas (P1–P11) e mapeadas diretamente às seções do design document para rastreabilidade.
- Todas as classes Kotlin devem ser `data class` onde aplicável; usar `@Volatile` no campo `terms` do cache para thread safety sem lock.
- O campo `result` da entidade `RuleExecutionEntity` é armazenado como `JSONB`; usar `hypersistence-utils` (`io.hypersistence:hypersistence-utils-hibernate-63`) para o `@Type(JsonType::class)`.
- Flyway migrations devem ser aplicadas em ordem (`V1`, `V2`, `V3`); nunca alterar migrations já aplicadas em produção.
- Checkpoints (tarefas 7, 11, 13) não contêm código — são pontos de verificação onde o agente deve pausar e confirmar com o usuário.

---

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["2.1", "2.2", "2.3", "2.4"] },
    { "id": 2, "tasks": ["3.1", "4.1", "4.2", "4.3"] },
    { "id": 3, "tasks": ["3.2", "3.3", "3.4", "3.5", "5.1", "5.2"] },
    { "id": 4, "tasks": ["3.6", "3.7", "3.8", "3.9", "5.3", "6.1", "6.2"] },
    { "id": 5, "tasks": ["5.4", "6.3", "6.4", "8.1"] },
    { "id": 6, "tasks": ["8.2", "8.3", "8.4", "9.1"] },
    { "id": 7, "tasks": ["8.5", "9.2", "9.3"] },
    { "id": 8, "tasks": ["9.4", "9.5", "10.1"] },
    { "id": 9, "tasks": ["10.2", "10.3"] },
    { "id": 10, "tasks": ["10.4", "10.5", "10.6", "10.7"] },
    { "id": 11, "tasks": ["12.1", "12.2", "12.3"] },
    { "id": 12, "tasks": ["12.4"] }
  ]
}
```
