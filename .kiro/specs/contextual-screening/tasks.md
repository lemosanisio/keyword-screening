# Implementation Plan: Contextual Screening

## Overview

Implementação da segunda regra de screening — **Contextual Screening** — em Kotlin + Spring Boot 3.3.4, seguindo a mesma arquitetura DDD em camadas do projeto existente (domain → application → infrastructure → presentation). O Contextual Screening avalia semanticamente transações que tiveram match no Keyword Screening, usando um LLM (via coaf-analyzer API) combinado com decisões históricas de analistas (RAG/few-shot learning).

A implementação é feita em etapas incrementais: domínio (modelos, ports, domain services puros), infraestrutura (migrations, entities, adapters), aplicação (use cases, services), apresentação (controller, DTOs, exception handler) e testes (PBT + unitários + integração).

O projeto usa **Kotest Property Testing** para PBT, **Testcontainers + PostgreSQL** para testes de integração, **MockWebServer** (OkHttp) para testes do adapter LLM, e **Flyway** para migrations.

---

## Tasks

- [x] 1. Camada de domínio — modelos, enums e value objects
  - [x] 1.1 Criar enum `Classification` e data class `ContextualScreeningResult`
    - Criar `Classification.kt` em `domain/model` com valores: `FALSE_POSITIVE, SUSPICIOUS, UNCERTAIN`
    - Criar `ContextualScreeningResult.kt` como `data class(classification: Classification, confidence: Double, reason: String, requiresAnalystReview: Boolean)`
    - _Requirements: 5.1, 12.1, 12.2_

  - [x] 1.2 Criar data class `HistoricalDecision`
    - Criar `HistoricalDecision.kt` em `domain/model` com campos: `id: Long? = null, keyword: String, description: String, analystDecision: Classification, createdAt: Instant`
    - _Requirements: 2.1, 8.1, 12.7_

  - [x] 1.3 Criar data class `ContextualScreeningAudit`
    - Criar `ContextualScreeningAudit.kt` em `domain/model` com campos: `id: Long? = null, transactionId: String, ruleId: String, keyword: String, prompt: String, modelResponse: String?, llmClassification: String?, llmConfidence: Double?, finalClassification: Classification, finalConfidence: Double, requiresAnalystReview: Boolean, reason: String, analystDecision: Classification? = null, createdAt: Instant`
    - _Requirements: 7.1, 7.2, 12.8_

- [x] 2. Camada de domínio — ports (interfaces de repositório e adaptador)
  - [x] 2.1 Criar port `LlmClassifierPort` e data class `LlmResponse`
    - Criar `LlmClassifierPort.kt` em `domain/port` com interface contendo `fun classify(prompt: String): LlmResponse`
    - Criar `LlmResponse` como `data class(classification: String?, confidence: Double?, reason: String?, rawResponse: String?, success: Boolean, errorMessage: String? = null)`
    - _Requirements: 4.1, 4.7_

  - [x] 2.2 Criar port `HistoricalDecisionRepository`
    - Criar `HistoricalDecisionRepository.kt` em `domain/port` com métodos: `findByKeyword(keyword: String): List<HistoricalDecision>` e `save(decision: HistoricalDecision): HistoricalDecision`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 8.1, 8.2_

  - [x] 2.3 Criar port `ContextualScreeningAuditRepository`
    - Criar `ContextualScreeningAuditRepository.kt` em `domain/port` com métodos: `findByTransactionIdAndRuleId(transactionId: String, ruleId: String): ContextualScreeningAudit?`, `save(audit: ContextualScreeningAudit): ContextualScreeningAudit`, `updateAnalystDecision(transactionId: String, ruleId: String, decision: Classification)`
    - _Requirements: 7.1, 7.2, 9.1, 9.3_

- [x] 3. Camada de domínio — domain services
  - [x] 3.1 Implementar `PromptBuilder`
    - Criar `PromptBuilder.kt` em `domain/service` anotado com `@Component`
    - Implementar `fun build(description: String, matchedKeyword: String, decisions: List<HistoricalDecision>): String`
    - O prompt deve conter: (a) descrição da transação, (b) matchedKeyword, (c) instruções de classificação (FALSE_POSITIVE, SUSPICIOUS, UNCERTAIN), (d) instruções de justificativa e pontuação de confiança, (e) seção de few-shot com decisões históricas (somente se decisions não estiver vazia)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 3.2 Implementar `RoutingClassifier`
    - Criar `RoutingClassifier.kt` em `domain/service` anotado com `@Component`
    - Implementar `fun requiresAnalystReview(classification: Classification, confidence: Double, autoCloseThreshold: Double): Boolean`
    - Regras: SUSPICIOUS → true, UNCERTAIN → true, FALSE_POSITIVE + confidence >= threshold → false, FALSE_POSITIVE + confidence < threshold → true
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [x] 3.3 Implementar `ResponseNormalizer`
    - Criar `ResponseNormalizer.kt` em `domain/service` anotado com `@Component`
    - Implementar `fun normalizeClassification(rawClassification: String?): Classification` — valores inválidos ou nulos → UNCERTAIN
    - Implementar `fun normalizeConfidence(rawConfidence: Double?): Double` — clamp para [0.00, 1.00], nulo → 0.00
    - _Requirements: 5.4, 5.5, 12.1, 12.2, 12.10_

  - [x] 3.4 Escrever testes unitários para `PromptBuilder`
    - Testar: prompt com decisões históricas contém seção few-shot; prompt sem decisões históricas não contém seção few-shot; prompt sempre contém description, keyword e instruções de classificação; caracteres especiais na descrição
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 3.5 Escrever testes unitários para `RoutingClassifier`
    - Testar: SUSPICIOUS→true, UNCERTAIN→true, FP+high_confidence→false, FP+low_confidence→true, threshold exato (boundary)
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [x] 3.6 Escrever testes unitários para `ResponseNormalizer`
    - Testar: classificações válidas (FALSE_POSITIVE, SUSPICIOUS, UNCERTAIN); classificação inválida→UNCERTAIN; nulo→UNCERTAIN; confidence em range; confidence negativa→0.00; confidence>1.0→1.00; confidence nula→0.00
    - _Requirements: 5.4, 5.5_

  - [x] 3.7 Escrever property test para `RoutingClassifier` — Property 3: Determinismo do roteamento
    - **Property 3: Determinismo do roteamento**
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.6, 12.3, 12.4, 12.5**
    - Usar `Arb.enum<Classification>()` × `Arb.double(0.0, 1.0)` × `Arb.double(0.0, 1.0)` e verificar que as regras de routing são satisfeitas para todas as combinações

  - [x] 3.8 Escrever property test para `ResponseNormalizer` — Property 1: Invariante de classificação
    - **Property 1: Invariante de classificação**
    - **Validates: Requirements 5.1, 5.4, 12.1, 12.10**
    - Usar `Arb.string()` (incluindo inválidas) e verificar que `normalizeClassification(s)` sempre retorna valor ∈ `{FALSE_POSITIVE, SUSPICIOUS, UNCERTAIN}`

  - [x] 3.9 Escrever property test para `ResponseNormalizer` — Property 2: Invariante de confiança com clamping
    - **Property 2: Invariante de confiança com clamping**
    - **Validates: Requirements 5.2, 5.5, 12.2**
    - Usar `Arb.double(-100.0, 100.0)` e `Arb.double().orNull()` e verificar que `normalizeConfidence(d)` está em [0.00, 1.00]

  - [x] 3.10 Escrever property test para `PromptBuilder` — Property 4: Completude do prompt
    - **Property 4: Completude do prompt**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
    - Usar `Arb.string(minSize=1)` × `Arb.string(minSize=1)` × `Arb.list(Arb.historicalDecision())` e verificar que o prompt contém description, keyword, instruções e todas as decisões

- [x] 4. Checkpoint — Verificar domínio puro
  - Garantir que todos os testes unitários e de propriedade dos domain services passam
  - Verificar que as interfaces de port estão consistentes com os modelos
  - Perguntar ao usuário se há ajustes antes de prosseguir com infraestrutura.

- [x] 5. Camada de infraestrutura — migrations de banco de dados
  - [x] 5.1 Criar migration Flyway `V4__create_contextual_screening_audit.sql`
    - Criar tabela `contextual_screening_audit` com campos: `id BIGSERIAL PK, transaction_id VARCHAR(100) NOT NULL, rule_id VARCHAR(50) NOT NULL, keyword VARCHAR(255) NOT NULL, prompt TEXT NOT NULL, model_response JSONB, llm_classification VARCHAR(50), llm_confidence DECIMAL(4,3), final_classification VARCHAR(50) NOT NULL, final_confidence DECIMAL(4,3) NOT NULL, requires_analyst_review BOOLEAN NOT NULL, reason TEXT NOT NULL, analyst_decision VARCHAR(50), created_at TIMESTAMP NOT NULL`
    - Criar constraint `UNIQUE(transaction_id, rule_id)`
    - Criar índices: `idx_ctx_audit_tx_rule`, `idx_ctx_audit_keyword`
    - Caminho: `src/main/resources/db/migration/V4__create_contextual_screening_audit.sql`
    - _Requirements: 7.1, 7.2, 9.3_

  - [x] 5.2 Criar migration Flyway `V5__create_historical_decision.sql`
    - Criar tabela `historical_decision` com campos: `id BIGSERIAL PK, keyword VARCHAR(255) NOT NULL, description TEXT NOT NULL, analyst_decision VARCHAR(50) NOT NULL, created_at TIMESTAMP NOT NULL`
    - Criar índices: `idx_hist_decision_keyword`, `idx_hist_decision_keyword_date ON historical_decision(keyword, created_at DESC)`
    - Caminho: `src/main/resources/db/migration/V5__create_historical_decision.sql`
    - _Requirements: 2.1, 8.1, 8.2_

- [x] 6. Camada de infraestrutura — JPA entities e mappers
  - [x] 6.1 Criar JPA entity `ContextualScreeningAuditEntity`
    - Criar `ContextualScreeningAuditEntity.kt` em `infrastructure/persistence/entity` com `@Entity @Table(name = "contextual_screening_audit", uniqueConstraints = [...])`
    - Campo `modelResponse` com `@Type(JsonType::class) @Column(columnDefinition = "jsonb")`
    - _Requirements: 7.1, 7.2_

  - [x] 6.2 Criar JPA entity `HistoricalDecisionEntity`
    - Criar `HistoricalDecisionEntity.kt` em `infrastructure/persistence/entity` com `@Entity @Table(name = "historical_decision")`
    - _Requirements: 2.1, 8.1_

  - [x] 6.3 Criar mappers `ContextualScreeningAuditMapper` e `HistoricalDecisionMapper`
    - Criar `ContextualScreeningAuditMapper.kt` com funções `toDomain(entity): ContextualScreeningAudit` e `toEntity(domain): ContextualScreeningAuditEntity`
    - Criar `HistoricalDecisionMapper.kt` com funções `toDomain(entity): HistoricalDecision` e `toEntity(domain): HistoricalDecisionEntity`
    - _Requirements: 7.1, 8.1_

- [x] 7. Camada de infraestrutura — JPA repositories e implementações
  - [x] 7.1 Criar Spring Data JPA interfaces
    - Criar `ContextualScreeningAuditJpaRepository` com métodos: `findByTransactionIdAndRuleId(transactionId: String, ruleId: String): ContextualScreeningAuditEntity?` e `@Modifying @Query updateAnalystDecision(...)`
    - Criar `HistoricalDecisionJpaRepository` com método: `findByKeywordOrderByCreatedAtDesc(keyword: String): List<HistoricalDecisionEntity>`
    - _Requirements: 2.2, 7.2, 8.3_

  - [x] 7.2 Implementar `ContextualScreeningAuditRepositoryImpl`
    - Criar `ContextualScreeningAuditRepositoryImpl.kt` anotado com `@Repository`
    - Implementar port `ContextualScreeningAuditRepository` delegando para JPA repository + mapper
    - Tratar `DataIntegrityViolationException` no `save` (race condition): ao capturar, buscar e retornar registro existente
    - _Requirements: 7.1, 7.2, 9.3_

  - [x] 7.3 Implementar `HistoricalDecisionRepositoryImpl`
    - Criar `HistoricalDecisionRepositoryImpl.kt` anotado com `@Repository`
    - Implementar port `HistoricalDecisionRepository` delegando para JPA repository + mapper
    - _Requirements: 2.1, 2.2, 2.4, 8.1, 8.2_

- [x] 8. Camada de infraestrutura — adaptador LLM (CoafAnalyzerAdapter)
  - [x] 8.1 Implementar `CoafAnalyzerAdapter`
    - Criar `CoafAnalyzerAdapter.kt` em `infrastructure/llm` anotado com `@Component`
    - Implementar `LlmClassifierPort` usando Spring `RestClient`
    - Configurar timeout via `@Value("\${coaf.analyzer.timeout-seconds:30}")`
    - Enviar requisição POST para `/api/coaf/analisar` com body `{texto: prompt, prioridade: "ALTA"}`
    - Parsear resposta: mapear campos `decisao`, `confianca`, `justificativa` para `LlmResponse`
    - Em caso de exceção (timeout, erro HTTP, JSON inválido): retornar `LlmResponse(success=false, errorMessage=...)`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 8.2 Escrever testes unitários para `CoafAnalyzerAdapter` com MockWebServer
    - Testar: resposta OK com COMUNICAR → LlmResponse com success=true e classificação COMUNICAR; resposta OK com NAO_COMUNICAR; timeout → LlmResponse com success=false; HTTP 500 → LlmResponse com success=false; JSON inválido → LlmResponse com success=false
    - Adicionar dependência `testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")` se ainda não existir
    - _Requirements: 4.1, 4.3, 4.4, 4.5_

  - [x] 8.3 Escrever property test para `CoafAnalyzerAdapter` — Property 9: Mapeamento correto de resposta do coaf-analyzer
    - **Property 9: Mapeamento correto de resposta do coaf-analyzer**
    - **Validates: Requirements 4.3, 5.1**
    - Usar `Arb.element("COMUNICAR", "NAO_COMUNICAR", "REVISAO_MANUAL")` × `Arb.double(0.0, 1.0)` e MockWebServer para verificar mapeamento correto dos campos

- [x] 9. Camada de infraestrutura — configuração
  - [x] 9.1 Atualizar `application.yml` com configurações do Contextual Screening
    - Adicionar seção `contextual-screening.auto-close-threshold: 0.95`
    - Adicionar seção `coaf.analyzer.base-url: http://localhost:8080` e `coaf.analyzer.timeout-seconds: 30`
    - _Requirements: 6.5, 6.6, 11.2_

- [x] 10. Checkpoint — Verificar infraestrutura
  - Garantir que migrations Flyway são aplicadas sem erros com Testcontainers
  - Verificar que entities JPA mapeiam corretamente as tabelas
  - Verificar que CoafAnalyzerAdapter passa nos testes com MockWebServer
  - Perguntar ao usuário se há ajustes antes de prosseguir com application layer.

- [x] 11. Camada de aplicação — use cases e services
  - [x] 11.1 Criar comandos e interfaces de use case
    - Criar `EvaluateContextualScreeningCommand.kt` com campos: `transactionId, ruleId, description, matchedKeyword`
    - Criar `EvaluateContextualScreeningUseCase` interface com `fun execute(command): ContextualScreeningResultDto`
    - Criar `ContextualScreeningResultDto` com campos: `classification: String, confidence: Double, reason: String, requiresAnalystReview: Boolean`
    - Criar `RegisterAnalystDecisionCommand.kt` com campos: `transactionId, ruleId, analystDecision`
    - Criar `RegisterAnalystDecisionUseCase` interface com `fun execute(command): AnalystDecisionResultDto`
    - Criar `AnalystDecisionResultDto` com campos: `transactionId, ruleId, analystDecision, registeredAt`
    - _Requirements: 1.1, 8.1_

  - [x] 11.2 Implementar `ContextualScreeningService`
    - Criar `ContextualScreeningService.kt` anotado com `@Service` implementando `EvaluateContextualScreeningUseCase`
    - Injetar: `ContextualScreeningAuditRepository`, `HistoricalDecisionRepository`, `LlmClassifierPort`, `PromptBuilder`, `RoutingClassifier`, `ResponseNormalizer`, `@Value autoCloseThreshold`
    - Fluxo: (1) idempotência via audit lookup, (2) recuperar decisões históricas com fallback lista vazia, (3) construir prompt, (4) invocar LLM, (5) mapear decisão coaf-analyzer→Classification, (6) normalizar classificação e confidence, (7) determinar roteamento, (8) persistir auditoria, (9) retornar resultado
    - Implementar `mapDecisaoToClassification`: COMUNICAR→SUSPICIOUS, NAO_COMUNICAR→FALSE_POSITIVE, REVISAO_MANUAL→UNCERTAIN
    - _Requirements: 1.1, 2.5, 3.1, 4.6, 5.1, 5.4, 5.5, 6.1, 6.3, 6.4, 7.1, 7.3, 7.4, 9.1, 9.2, 10.1, 10.2, 10.3, 10.4_

  - [x] 11.3 Implementar `AnalystDecisionService`
    - Criar `AnalystDecisionService.kt` anotado com `@Service` implementando `RegisterAnalystDecisionUseCase`
    - Injetar: `ContextualScreeningAuditRepository`, `HistoricalDecisionRepository`
    - Fluxo: (1) validar classificação (valueOf), (2) buscar audit existente (throw se não encontrado), (3) persistir HistoricalDecision para RAG, (4) atualizar analystDecision no audit
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [x] 11.4 Criar exception classes
    - Criar `ContextualScreeningAuditNotFoundException.kt` em `application/exception`
    - Criar `InvalidAnalystDecisionException.kt` em `application/exception`
    - _Requirements: 8.4, 8.5_

  - [x] 11.5 Escrever testes unitários para `ContextualScreeningService`
    - Testar com MockK: fluxo completo com LLM OK; idempotência (audit existente retorna cached); fallback LLM (erro → UNCERTAIN/0.00/requiresReview=true); fallback historical decisions (exceção → lista vazia); mapeamento COMUNICAR→SUSPICIOUS, NAO_COMUNICAR→FALSE_POSITIVE, REVISAO_MANUAL→UNCERTAIN; normalização de classificação inválida; normalização de confidence fora de range
    - _Requirements: 1.1, 4.4, 4.5, 5.4, 5.5, 9.1, 10.1, 10.2, 10.3_

  - [x] 11.6 Escrever testes unitários para `AnalystDecisionService`
    - Testar com MockK: decisão válida persiste HistoricalDecision e atualiza audit; audit não encontrado → exception; decisão inválida (fora do enum) → exception
    - _Requirements: 8.1, 8.3, 8.4, 8.5_

  - [x] 11.7 Escrever property test para service — Property 7: Idempotência de avaliação
    - **Property 7: Idempotência de avaliação**
    - **Validates: Requirements 9.1, 9.2, 12.6**
    - Usar mock que simula audit existente e verificar que LLM não é invocado na segunda chamada; resultado é idêntico

  - [x] 11.8 Escrever property test para service — Property 8: Completude do registro de auditoria
    - **Property 8: Completude do registro de auditoria**
    - **Validates: Requirements 7.1, 7.3, 7.4, 12.8**
    - Usar `Arb.contextualScreeningCommand()` com mock LLM variado e verificar que campos obrigatórios do audit são não nulos

  - [x] 11.9 Escrever property test para service — Property 11: Completude do resultado para entrada válida
    - **Property 11: Completude do resultado para entrada válida**
    - **Validates: Requirements 5.3, 12.9**
    - Usar `Arb.contextualScreeningCommand()` e verificar que resultado tem classificação válida, confidence em [0.00, 1.00], reason não vazio, requiresAnalystReview consistente com routing

- [x] 12. Camada de apresentação — controller, DTOs e exception handler
  - [x] 12.1 Criar DTOs de request e response
    - Criar `ContextualScreeningRequest.kt` com `@field:NotBlank transactionId, ruleId (opcional), @field:NotBlank description, @field:NotBlank matchedKeyword`
    - Criar `ContextualScreeningResponse.kt` com campos: `classification, confidence, reason, requiresAnalystReview`
    - Criar `AnalystDecisionRequest.kt` com `@field:NotBlank transactionId, ruleId (opcional), @field:NotBlank analystDecision`
    - Criar `AnalystDecisionResponse.kt` com campos: `transactionId, ruleId, analystDecision, registeredAt`
    - _Requirements: 1.4, 1.5, 1.6, 8.5_

  - [x] 12.2 Implementar `ContextualScreeningController`
    - Criar `ContextualScreeningController.kt` com `@RestController @RequestMapping("/v1/rules/contextual-screening")`
    - Endpoint `@PostMapping("/evaluate")` com `@Valid @RequestBody ContextualScreeningRequest`
    - Endpoint `@PostMapping("/decisions")` com `@Valid @RequestBody AnalystDecisionRequest`
    - _Requirements: 1.1, 1.4, 1.5, 1.6, 8.1_

  - [x] 12.3 Implementar `ContextualScreeningExceptionHandler`
    - Criar `ContextualScreeningExceptionHandler.kt` com `@RestControllerAdvice`
    - Handler para `ContextualScreeningAuditNotFoundException` → HTTP 404
    - Handler para `InvalidAnalystDecisionException` → HTTP 400
    - Handler para `IllegalArgumentException` (enum parsing) → HTTP 400 com valores permitidos
    - _Requirements: 8.4, 8.5_

  - [x] 12.4 Escrever testes unitários para `ContextualScreeningController` (WebMvcTest)
    - Testar: request válida evaluate → 200; transactionId ausente/branco → 400; description ausente/vazia → 400; matchedKeyword ausente/vazio → 400; decisions endpoint com audit não encontrado → 404; analystDecision inválido → 400
    - _Requirements: 1.4, 1.5, 1.6, 8.4, 8.5_

  - [x] 12.5 Escrever property test para controller — Property 10: Validação transactionId em branco
    - **Property 10: Validação de entrada — transactionId em branco**
    - **Validates: Requirements 1.4**
    - Usar `Arb.stringPattern("\\s*")` (whitespace-only strings) e verificar HTTP 400

  - [x] 12.6 Escrever property test para controller — Property 11 (endpoint): Completude do resultado para entrada válida
    - **Property 11: Completude do resultado para entrada válida (via endpoint)**
    - **Validates: Requirements 5.3, 12.9**
    - Usar `@WebMvcTest` com mock do use case e verificar que response contém todos os campos esperados para qualquer input válido

- [x] 13. Checkpoint — Verificar testes unitários e de propriedade
  - Garantir que todos os testes unitários e de propriedade passam
  - Garantir que a aplicação compila sem erros
  - Perguntar ao usuário se há ajustes antes de prosseguir com testes de integração.

- [x] 14. Testes de integração
  - [x] 14.1 Escrever teste de integração end-to-end com Testcontainers + MockWebServer
    - Usar `@SpringBootTest(webEnvironment = RANDOM_PORT)` com PostgreSQL via Testcontainers e MockWebServer para simular coaf-analyzer
    - Testar fluxo completo: POST `/v1/rules/contextual-screening/evaluate` → 200 com classificação correta
    - Testar idempotência: segunda chamada com mesmo `transactionId`/`ruleId` retorna resultado idêntico sem invocar LLM novamente
    - Verificar que apenas 1 registro `contextual_screening_audit` é criado no banco
    - _Requirements: 1.1, 5.1, 9.1, 9.2, 9.3_

  - [x] 14.2 Escrever teste de integração para o fluxo de feedback do analista
    - Testar: POST `/v1/rules/contextual-screening/decisions` persiste HistoricalDecision e atualiza analystDecision no audit
    - Verificar que a decisão histórica é recuperável por keyword
    - Testar: decisão para transactionId inexistente → 404
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [x] 14.3 Escrever teste de integração para fallback do LLM
    - Configurar MockWebServer para retornar timeout/500/JSON inválido
    - Verificar que resultado é UNCERTAIN/confidence=0.00/requiresReview=true
    - Verificar que auditoria é persistida com erro no modelResponse
    - _Requirements: 4.4, 4.5, 7.4, 10.1, 10.4_

  - [x] 14.4 Escrever teste de integração para race condition no audit (UNIQUE constraint)
    - Disparar chamadas concorrentes com o mesmo `transactionId`/`ruleId`
    - Verificar que apenas 1 registro de auditoria existe no banco
    - Verificar que ambas as chamadas retornam o mesmo resultado
    - _Requirements: 9.3_

  - [x] 14.5 Escrever property test de integração — Property 5: Filtragem e ordenação de decisões históricas
    - **Property 5: Filtragem e ordenação de decisões históricas**
    - **Validates: Requirements 2.1, 2.2, 2.4**
    - Usar Testcontainers + `Arb.list(Arb.historicalDecision())` com keywords variadas; verificar filtro por keyword e ordenação DESC por createdAt

  - [x] 14.6 Escrever property test de integração — Property 6: Round-trip de persistência de feedback
    - **Property 6: Round-trip de persistência de feedback**
    - **Validates: Requirements 8.1, 8.2, 12.7**
    - Usar Testcontainers + `Arb.historicalDecision()` e verificar que save seguido de findByKeyword recupera a decisão

- [x] 15. Checkpoint final — Garantir que todos os testes passam
  - Executar suite completa: testes unitários, de propriedade e de integração
  - Verificar que a aplicação sobe corretamente com PostgreSQL
  - Verificar que o endpoint responde corretamente com MockWebServer simulando coaf-analyzer
  - Perguntar ao usuário se há ajustes antes de considerar a implementação concluída.

---

## Notes

- Tarefas marcadas com `*` são opcionais e podem ser puladas para um MVP mais rápido; todas as demais são obrigatórias.
- A ordem das tarefas garante que nenhum código fique órfão: cada componente é integrado ao anterior antes de avançar.
- As properties PBT são numeradas (P1–P11) e mapeadas diretamente às seções do design document para rastreabilidade.
- O Contextual Screening **não implementa** a interface `ScreeningRule` existente (contrato diferente); é um serviço autônomo com seu próprio controller.
- O campo `modelResponse` da entidade `ContextualScreeningAuditEntity` é armazenado como `JSONB`; usar `hypersistence-utils` já configurado no projeto.
- Flyway migrations devem ser `V4` e `V5` (continuando a numeração existente V1–V3 do keyword-screening).
- Checkpoints (tarefas 4, 10, 13, 15) não contêm código — são pontos de verificação onde o agente deve pausar e confirmar com o usuário.
- A dependência `mockwebserver` deve ser adicionada ao `build.gradle.kts` para testes do adapter LLM.
- O mapeamento de respostas do coaf-analyzer segue: `COMUNICAR → SUSPICIOUS`, `NAO_COMUNICAR → FALSE_POSITIVE`, `REVISAO_MANUAL → UNCERTAIN`.
- O threshold de auto-close é configurável via `contextual-screening.auto-close-threshold` (padrão: 0.95).

---

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["2.1", "2.2", "2.3"] },
    { "id": 2, "tasks": ["3.1", "3.2", "3.3", "5.1", "5.2"] },
    { "id": 3, "tasks": ["3.4", "3.5", "3.6", "6.1", "6.2"] },
    { "id": 4, "tasks": ["3.7", "3.8", "3.9", "3.10", "6.3", "7.1"] },
    { "id": 5, "tasks": ["7.2", "7.3", "8.1", "9.1"] },
    { "id": 6, "tasks": ["8.2", "8.3", "11.1"] },
    { "id": 7, "tasks": ["11.2", "11.3", "11.4"] },
    { "id": 8, "tasks": ["11.5", "11.6", "11.7", "11.8", "11.9"] },
    { "id": 9, "tasks": ["12.1"] },
    { "id": 10, "tasks": ["12.2", "12.3"] },
    { "id": 11, "tasks": ["12.4", "12.5", "12.6"] },
    { "id": 12, "tasks": ["14.1", "14.2", "14.3", "14.4"] },
    { "id": 13, "tasks": ["14.5", "14.6"] }
  ]
}
```
