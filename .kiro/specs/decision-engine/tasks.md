# Implementation Plan: Decision Engine

## Overview

Implementação do Decision Engine como novo bounded context (Decision Context) ao lado do Screening Context existente, incluindo o Alert Context. Segue hexagonal architecture com Kotlin 1.9, Spring Boot 3.3, PostgreSQL 16, e JUnit5 + jqwik para PBT. As tarefas estão organizadas em camadas: shared domain, domain layer, application layer, infrastructure, integração com Screening Context existente, e Alert Context.

## Tasks

- [x] 1. Shared Domain e Fundação
  - [x] 1.1 Criar interfaces DomainEvent e DomainEventPublisher no pacote br.com.shared.domain
    - Criar `DomainEvent` interface com campos: eventId (String), traceId (String), timestamp (Instant)
    - Criar `DomainEventPublisher` interface com método `publish(event: DomainEvent)`
    - Ambas sem dependência de Spring — puro domínio
    - _Requirements: 10.8, 10.9_

  - [x] 1.2 Adicionar dependências jqwik e configuração no build.gradle.kts
    - Adicionar `net.jqwik:jqwik-kotlin:1.8.4` em testImplementation
    - Criar `src/test/resources/jqwik.properties` com `jqwik.tries.default=1000`
    - Garantir que JUnit5 Platform está configurado para descobrir jqwik
    - _Requirements: N/A (infraestrutura de teste)_

  - [x] 1.3 Criar Flyway migration V2 para tabelas do Decision Context
    - Criar `V2__create_decision_context_tables.sql` com: entity_definition, fact_definition, rule_definition, rule_configuration, configuration_version, decision_execution, dry_run_log
    - Incluir índices, constraints UNIQUE, partial unique index para configuração ativa
    - Usar JSONB para expressions, facts, explanation, supported_operators, supported_actions
    - _Requirements: 1.1, 2.1, 8.1, 9.1, 9.2, 11.1_

  - [x] 1.4 Criar Flyway migration V3 para tabelas do Alert Context
    - Criar `V3__create_alert_context_tables.sql` com: alert (id, transaction_id, rule_id, customer_id, facts, configuration_version, trace_id, actions, explanation, status, created_at, updated_at)
    - Incluir UNIQUE constraint para idempotência (transaction_id, rule_id)
    - Incluir índices para consultas por transaction_id, rule_id, status, customer_id
    - _Requirements: 12.2, 12.4, 12.8_

  - [x] 1.5 Criar Flyway migration V4 com seed data do MVP (Rule Catalog + Fact Registry)
    - Inserir Entity_Definitions: Risk (PLD, [customerRisk]), Screening (Screening, [keywordMatched])
    - Inserir Fact_Definitions: keywordMatched (BOOLEAN, operators: EQUALS/NOT_EQUALS), customerRisk (ENUM, operators: EQUALS/NOT_EQUALS/GTE)
    - Inserir Rule_Definition: KEYWORD_SCREENING (context: SCREENING, category: KEYWORD_SCREENING, supportedFacts, supportedActions: [GENERATE_ALERT, IGNORE], status: ACTIVE)
    - _Requirements: 1.6, 2.5, 15.5_

- [x] 2. Decision Context — Domain Layer (Value Objects, Enums, Models)
  - [x] 2.1 Criar Value Objects com @JvmInline value class
    - Criar em `br.com.decision.domain.valueobject`: RuleId, RuleCode, FactName, ConfigurationVersion (todos @JvmInline value class)
    - Criar sealed class FactValue com subtipos: BooleanValue, EnumValue, NumberValue, StringValue, MoneyValue
    - _Requirements: 13.1, 13.2, 13.6_

  - [x] 2.2 Criar Enums do Decision Context
    - Criar em `br.com.decision.domain.model.enums`: Decision (ALERT, IGNORE, REVIEW, BLOCK), Action (GENERATE_ALERT, IGNORE, REVIEW, BLOCK), ComparisonOperator (EQUALS, NOT_EQUALS, GTE, GT, LT, LTE, IN, NOT_IN, CONTAINS), CustomerRisk (BR, MR, AR com ordering), FactType, RuleContext, RuleCategory, RuleStatus
    - CustomerRisk DEVE usar ordinal para ordering: BR < MR < AR
    - _Requirements: 5.5, 7.1, 13.5, 14.4, 14.5_

  - [x] 2.3 Criar Expression Model (sealed interface)
    - Criar `Expression` sealed interface em `br.com.decision.domain.model`
    - Criar `Condition` data class (factName: FactName, operator: ComparisonOperator, expectedValue: FactValue) : Expression
    - Criar `Group` data class (logicalOperator: LogicalOperator, expressions: List<Expression>) : Expression
    - Criar `LogicalOperator` enum (AND, OR)
    - _Requirements: 5.1, 5.9, 14.3_

  - [x] 2.4 Criar Aggregate Roots do Decision Context
    - Criar `RuleDefinition` data class (id, code, name, description, context, category, supportedFacts, supportedActions, status, createdAt)
    - Criar `RuleConfiguration` data class com invariante: require(expressions.size <= 10), agrega ConfigurationVersionEntry
    - Criar `DecisionExecution` data class (imutável, write-once)
    - Criar `FactDefinition` data class (catálogo de fatos)
    - Criar `EntityDefinition` data class (catálogo de entidades)
    - _Requirements: 2.1, 8.1, 8.7, 8.8, 9.1, 9.9, 1.1, 15.1_

  - [x] 2.5 Criar DecisionResult, DecisionExplanation e ExplanationSteps
    - Criar `DecisionResult` value object com: decision, actions, matchedExpressions, failedExpressions, executionTimeMs, configurationVersion, facts
    - Criar `ExpressionEvaluation` data class com: factName, operator, expectedValue, actualValue, satisfied, justification
    - Criar `DecisionExplanation` com traceId e List<ExplanationStep>
    - Criar sealed interface `ExplanationStep` com 7 implementações: ReceptionStep, RuleIdentificationStep, ContextBuildingStep, EvaluationStep, DecisionStep, PersistenceStep, PublicationStep
    - Criar `ResolverResult` e `ResolverOutcome` (sealed: Success, Failure)
    - _Requirements: 6.7, 16.1, 16.2, 16.5, 16.6_

  - [x] 2.6 Criar Domain Events (DetectionEvent, DecisionMadeEvent)
    - Criar `DetectionEvent` data class implementando DomainEvent (eventId, traceId, timestamp, transactionId, customerId, ruleCode, detectionResult)
    - Criar `DetectionResult` e `DetectionMatch` data classes
    - Criar `DecisionMadeEvent` data class implementando DomainEvent (todos os campos para ser auto-contido)
    - _Requirements: 4.2, 10.3, 10.4, 10.7, 10.8_

  - [x] 2.7 Criar Output Ports (Repository interfaces e CustomerRiskPort)
    - Criar em `br.com.decision.domain.port`: DecisionExecutionRepository, RuleConfigurationRepository, RuleDefinitionRepository, FactDefinitionRepository, EntityDefinitionRepository, CustomerRiskPort
    - Cada interface com métodos conforme design document
    - Nenhuma dependência de Spring — interfaces puras de domínio
    - _Requirements: 3.5, 9.1, 9.4, 9.5, 9.6_

  - [x] 2.8 Criar Domain Exceptions
    - Criar em `br.com.decision.domain.exception`: InvalidConfigurationException, RuleConfigurationNotFoundException, FactResolutionException, DuplicateActiveConfigException
    - Todas extendem DomainException (de br.com.shared.domain)
    - _Requirements: 1.5, 8.6_

- [x] 3. Decision Context — Domain Services (Pure Logic)
  - [x] 3.1 Implementar ExpressionEvaluator
    - Classe pura sem Spring annotations, registrada via @Configuration
    - Implementar avaliação de Condition contra Map<FactName, FactValue>
    - Suportar EQUALS, NOT_EQUALS para Boolean; EQUALS, NOT_EQUALS, GTE para Enum (CustomerRisk ordering)
    - Fact ausente → satisfied=false
    - Gerar justificativa human-readable para cada avaliação
    - _Requirements: 5.3, 5.4, 5.5, 5.7, 16.5_

  - [x] 3.2 Write property test for ExpressionEvaluator (Property 2)
    - **Property 2: Expression Evaluation Correctness**
    - **Validates: Requirements 5.3, 5.4, 5.5, 5.7**
    - Usar jqwik @Property(tries = 1000) com geradores para Condition, FactSet, CustomerRisk
    - Verificar: EQUALS satisfeito iff valores iguais, GTE satisfeito iff actual.ordinal >= expected.ordinal, absent fact → not satisfied

  - [x] 3.3 Implementar RuleEngine
    - Classe pura recebendo ExpressionEvaluator via construtor
    - Método `evaluate(facts: Map<FactName, FactValue>, expressions: List<Expression>): RuleEvaluationResult`
    - Semântica AND implícita: allSatisfied=true iff todas as Conditions satisfeitas
    - Retornar lista de ExpressionEvaluation para cada expressão
    - _Requirements: 5.2, 5.6, 6.2, 6.3_

  - [x] 3.4 Write property test for RuleEngine (Property 3)
    - **Property 3: Decision Logic — AND Semantics**
    - **Validates: Requirements 5.6, 5.8, 6.4, 6.5, 6.6**
    - Verificar: ALERT iff all conditions satisfied AND config active; IGNORE otherwise
    - Usar jqwik Arbitraries.list(condition(), 1..10) × factSet()

  - [x] 3.5 Implementar FactResolver interface e Resolvers (ScreeningResolver, CustomerResolver)
    - Criar interface `FactResolver` com: producedFacts (Set<FactName>), entity (String), resolve(event: DetectionEvent): List<Fact>
    - Implementar `ScreeningResolver`: extrai keywordMatched do DetectionEvent (sem chamadas externas)
    - Implementar `CustomerResolver`: busca customerRisk via CustomerRiskPort, retorna emptyList() se falhar
    - _Requirements: 3.2, 3.4, 3.9_

  - [x] 3.6 Implementar ContextBuilder
    - Classe pura recebendo List<FactResolver> via construtor
    - Método `buildContext(event: DetectionEvent, requiredFacts: List<FactName>): FactSet`
    - Invocar apenas resolvers cujos producedFacts contêm facts requeridos
    - Capturar exceções de resolvers: fact ausente, logar erro, não propagar
    - Registrar timing e resultado por resolver em ResolverResult
    - _Requirements: 3.1, 3.3, 3.6, 3.10_

  - [x] 3.7 Write property test for ContextBuilder (Property 10)
    - **Property 10: Context Builder Selective Resolution**
    - **Validates: Requirements 3.1, 3.3, 3.6**
    - Verificar: apenas resolvers relevantes invocados; resolver failure → fact ausente sem exceção

  - [x] 3.8 Implementar DecisionEngine (orquestrador)
    - Classe pura recebendo ContextBuilder e RuleEngine via construtor
    - Método `evaluate(event: DetectionEvent, configuration: RuleConfiguration, traceId: String): DecisionResult`
    - Orquestrar: identificar facts requeridos → invocar ContextBuilder → invocar RuleEngine → montar DecisionResult com DecisionExplanation (7 etapas)
    - Config inativa → retornar IGNORE sem avaliar
    - _Requirements: 6.1, 6.4, 6.5, 6.6, 6.7, 16.1_

  - [x] 3.9 Write property test for DecisionExplanation completeness (Property 8)
    - **Property 8: Decision Explanation Completeness**
    - **Validates: Requirements 16.1, 16.2, 16.4, 16.5**
    - Verificar: explanation sempre contém 7 steps ordenados, cada step com timestamp e dados obrigatórios

- [x] 4. Checkpoint — Domain Layer
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Decision Context — Application Layer
  - [x] 5.1 Criar Use Case interfaces e Commands
    - Criar em `br.com.decision.application.usecase`: ExecuteDecisionUseCase, ExecuteDecisionCommand, ManageRuleConfigurationUseCase, CreateRuleConfigurationCommand, UpdateRuleConfigurationCommand, ExecuteDryRunUseCase, ExecuteDryRunCommand, DryRunResult, QueryDecisionExecutionUseCase, QueryDecisionExecutionResult
    - _Requirements: 6.1, 8.1, 17.1_

  - [x] 5.2 Implementar RuleConfigurationService
    - @Service implementando ManageRuleConfigurationUseCase
    - Métodos: create, update, activate, deactivate
    - Validação cruzada com FactDefinitionRepository (factName existe + enabled), RuleDefinitionRepository (supportedFacts, supportedActions, status), FactDefinition.supportedOperators, type compatibility
    - Rejeitar config ativa duplicada (mesma ruleId) → DuplicateActiveConfigException
    - Max 10 expressions
    - Create em estado draft=true, active=false
    - Versionamento: cada update cria nova ConfigurationVersionEntry (monotonicamente crescente)
    - Ativação: verificar dry-run prévio via DryRunLogRepository
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 2.2, 2.3, 2.4, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 11.1, 11.2, 18.1, 18.2, 18.4, 18.5, 18.6_

  - [x] 5.3 Write property test for Configuration Validation (Property 1)
    - **Property 1: Configuration Validation Correctness**
    - **Validates: Requirements 1.2, 1.3, 1.4, 1.5, 2.2, 2.3, 2.4, 8.5, 8.6**
    - Gerar Conditions com factNames válidos/inválidos, operators válidos/inválidos, types compatíveis/incompatíveis
    - Verificar: validação rejeita iff algum critério violado; aceita iff todos ok

  - [x] 5.4 Write property test for Configuration Version Monotonicity (Property 6)
    - **Property 6: Configuration Version Monotonicity**
    - **Validates: Requirements 8.2, 11.1, 11.2, 11.5**
    - Gerar sequência de updates (1..5), verificar versions consecutivas 1,2,3,... e imutáveis

  - [x] 5.5 Implementar DecisionService
    - @Service @Transactional implementando ExecuteDecisionUseCase
    - Idempotência: findByTransactionIdAndRuleId antes de executar
    - Buscar RuleDefinition por ruleCode, buscar RuleConfiguration ativa
    - Sem config ativa → IGNORE com persistência
    - Invocar DecisionEngine.evaluate, persistir DecisionExecution, publicar DecisionMadeEvent via DomainEventPublisher
    - Retry 3x para falhas de persistência
    - _Requirements: 4.7, 6.1, 6.6, 9.1, 9.7, 10.3, 10.4, 10.5_

  - [x] 5.6 Write property test for Decision Idempotency (Property 4)
    - **Property 4: Decision Execution Idempotency**
    - **Validates: Requirements 4.7, 9.2**
    - Executar fluxo 2x com mesmo DetectionEvent → resultado idêntico, sem nova persistência nem novo evento

  - [x] 5.7 Implementar DryRunService
    - @Service implementando ExecuteDryRunUseCase
    - Usar mesmo RuleEngine e ExpressionEvaluator do fluxo produtivo
    - NÃO persistir DecisionExecution, NÃO publicar evento, NÃO invocar FactResolvers
    - Validar facts input contra FactDefinition (tipo compatível, factName existe)
    - Persistir DryRunLog (configurationId, version, facts, result, executedBy, timestamp)
    - Funcionar tanto para configs draft quanto active
    - _Requirements: 17.1, 17.2, 17.3, 17.5, 17.6, 17.7, 17.8, 17.10, 18.3_

  - [x] 5.8 Write property test for Dry-Run Parity (Property 7)
    - **Property 7: Dry-Run Parity with Production**
    - **Validates: Requirements 17.2, 17.3**
    - Verificar: resultado dry-run idêntico ao que DecisionEngine produziria com mesmos facts/config; zero side effects

  - [x] 5.9 Implementar DecisionQueryService
    - @Service implementando QueryDecisionExecutionUseCase
    - Métodos: findByTransactionId (paginado, desc timestamp), findByRuleId (paginado), findByDecision (filtrado), findByTraceId
    - Page size padrão 20, máximo 100
    - _Requirements: 9.4, 9.5, 9.6, 9.8, 16.4, 16.11_

  - [x] 5.10 Write property test for Activation Requires Dry-Run (Property 12)
    - **Property 12: Activation Requires Prior Dry-Run**
    - **Validates: Requirements 18.1, 18.2, 18.4, 18.5, 18.6**
    - Verificar: ativação sucede iff dry-run log existe para a version; rejeita iff não existe

- [x] 6. Checkpoint — Application Layer
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Decision Context — Infrastructure Layer (Persistence)
  - [x] 7.1 Criar JPA Entities e Mappers
    - Criar @Entity classes em `br.com.decision.infrastructure.output.persistence.entity`: RuleDefinitionEntity, RuleConfigurationEntity, ConfigurationVersionEntity, DecisionExecutionEntity, FactDefinitionEntity, EntityDefinitionEntity, DryRunLogEntity
    - Usar `@Type(JsonType::class)` (hypersistence-utils) para campos JSONB (expressions, facts, explanation, supported_operators)
    - Criar Mappers: RuleDefinitionMapper, RuleConfigurationMapper, DecisionExecutionMapper, FactDefinitionMapper
    - _Requirements: 9.1, 13.3_

  - [x] 7.2 Criar Repository Adapters (Spring Data JPA)
    - Criar JpaRepository interfaces em `br.com.decision.infrastructure.output.persistence.repository`: RuleDefinitionJpaRepository, RuleConfigurationJpaRepository, DecisionExecutionJpaRepository, FactDefinitionJpaRepository, EntityDefinitionJpaRepository, DryRunLogJpaRepository
    - Criar implementações dos domain ports que delegam para JPA + Mapper
    - Incluir queries customizadas: findActiveByRuleId (partial unique), findByTransactionIdAndRuleId
    - _Requirements: 9.1, 9.4, 9.5_

  - [x] 7.3 Write property test for FactValue Serialization Round-Trip (Property 9)
    - **Property 9: FactValue Serialization Round-Trip**
    - **Validates: Requirements 13.6, 9.1**
    - Gerar todos os subtipos de FactValue, serializar como JSON, deserializar, verificar equivalência

  - [x] 7.4 Criar DomainEventPublisherAdapter
    - Adapter em `br.com.decision.infrastructure.output.event` implementando DomainEventPublisher
    - Delega para `ApplicationEventPublisher` do Spring
    - Publicação síncrona — o listener usa @TransactionalEventListener(phase = AFTER_COMMIT)
    - _Requirements: 10.1, 10.5, 10.8, 10.9_

  - [x] 7.5 Criar CustomerRiskAdapter
    - Adapter em `br.com.decision.infrastructure.output.rest` implementando CustomerRiskPort
    - Chamada REST ao sistema externo (Cadastro) para buscar risco do cliente
    - Timeout de 5 segundos configurável via @ConfigurationProperties
    - Retornar null se falha (timeout, erro de conexão, resposta inválida)
    - _Requirements: 3.5, 3.7_

  - [x] 7.6 Criar DecisionContextConfiguration
    - @Configuration em `br.com.decision.infrastructure.configuration`
    - Registrar domain services como beans: ExpressionEvaluator, RuleEngine, ContextBuilder (com autowired resolvers), DecisionEngine
    - Criar CustomerRiskProperties (@ConfigurationProperties) com URL e timeout
    - _Requirements: 3.8, 6.2, 6.3, 14.6_

- [x] 8. Decision Context — Infrastructure Layer (Input Adapters)
  - [x] 8.1 Criar DetectionEventListener
    - @Component em `br.com.decision.infrastructure.input.event`
    - @EventListener ou @TransactionalEventListener para DetectionEvent
    - Validar campos: transactionId (non-blank, max 64), customerId (non-blank, max 64), ruleCode (deve existir no Rule Catalog)
    - Campos inválidos → log WARN + descartar sem invocar DecisionService
    - Campos válidos → converter para ExecuteDecisionCommand e invocar ExecuteDecisionUseCase
    - _Requirements: 4.1, 4.4, 4.5, 4.6_

  - [x] 8.2 Write property test for DetectionEvent Validation (Property 11)
    - **Property 11: DetectionEvent Validation**
    - **Validates: Requirements 4.4, 4.5, 4.6**
    - Gerar DetectionEvents com campos válidos/inválidos (blank, null, excedendo 64 chars, ruleCode inexistente)
    - Verificar: válido → processado; inválido → descartado

  - [x] 8.3 Criar RuleConfigurationController
    - REST controller em `br.com.decision.infrastructure.input.http`
    - POST /v1/decision/rules/{ruleCode}/configurations → create
    - GET /v1/decision/rules/{ruleCode}/configurations → list by ruleCode
    - GET /v1/decision/rule-configurations/{id} → get by id
    - PUT /v1/decision/rule-configurations/{id} → update
    - POST /v1/decision/rule-configurations/{id}/activate → activate
    - POST /v1/decision/rule-configurations/{id}/deactivate → deactivate
    - GET /v1/decision/rule-configurations/{id}/versions → version history
    - Criar DTOs: CreateRuleConfigurationRequest, RuleConfigurationResponse, ConditionDto
    - _Requirements: 8.1, 8.4, 11.3, 11.4_

  - [x] 8.4 Criar DryRunController
    - POST /v1/decision/rule-configurations/{id}/dry-run
    - Criar DTOs: DryRunRequest (facts map), DryRunResponse (decision, actions, matched/failed expressions, explanation)
    - Validar facts input types via FactDefinition
    - _Requirements: 17.1, 17.4, 17.5_

  - [x] 8.5 Criar DecisionExecutionController
    - GET /v1/decision/executions?transactionId={id}&page=0&size=20
    - GET /v1/decision/executions?ruleId={id}&page=0&size=20
    - GET /v1/decision/executions?decision=ALERT&page=0&size=20
    - GET /v1/decision/executions?traceId={traceId}
    - GET /v1/decision/executions/{id}
    - Criar DTO: DecisionExecutionResponse
    - _Requirements: 9.4, 9.5, 9.6, 9.8, 16.4, 16.11_

  - [x] 8.6 Criar Rule Catalog e Fact Registry Controllers (read-only)
    - GET /v1/decision/rules (filtráveis por context, category)
    - GET /v1/decision/rules/{code}
    - GET /v1/decision/facts (filtráveis por entity, enabled)
    - GET /v1/decision/entities
    - _Requirements: 2.7, 15.7_

  - [x] 8.7 Criar DecisionExceptionHandler (@ControllerAdvice)
    - Tratar InvalidConfigurationException → 422
    - Tratar RuleConfigurationNotFoundException → 404
    - Tratar DuplicateActiveConfigException → 409
    - Retornar ErrorResponse padronizado com timestamp, status, error, message, details
    - _Requirements: 1.5, 8.6_

- [x] 9. Checkpoint — Decision Context Infrastructure
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Alert Context — Completo
  - [x] 10.1 Criar Domain Model do Alert Context
    - Criar `Alert` Aggregate Root em `br.com.alert.domain.model` com transitionTo(newStatus) e invariante canTransitionTo
    - Criar `AlertStatus` enum (OPEN, UNDER_REVIEW, CLOSED, FALSE_POSITIVE) com state machine
    - Criar `AlertId` @JvmInline value class
    - Criar `AlertRepository` interface (port)
    - Criar exceptions: AlertNotFoundException, InvalidAlertTransitionException
    - _Requirements: 12.2, 12.8_

  - [x] 10.2 Criar Application Layer do Alert Context
    - Criar use cases: CreateAlertUseCase, QueryAlertUseCase, UpdateAlertStatusUseCase
    - Implementar AlertService: createAlertIfNotExists (idempotente por transactionId+ruleId), updateStatus (validar transição)
    - Implementar AlertQueryService: findByTransactionId, findByRuleId (paginado), findById
    - _Requirements: 12.2, 12.4, 12.9_

  - [x] 10.3 Criar Infrastructure do Alert Context
    - Criar AlertEntity, AlertMapper, AlertJpaRepository
    - Criar DecisionMadeEventListener (@TransactionalEventListener AFTER_COMMIT): se GENERATE_ALERT → createAlertIfNotExists; se IGNORE → log DEBUG
    - Criar AlertController: GET /v1/alerts?transactionId={id}, GET /v1/alerts?ruleId={id}&page=0&size=20, GET /v1/alerts/{alertId}, PATCH /v1/alerts/{alertId}/status
    - Criar DTOs: AlertResponse, UpdateAlertStatusRequest
    - Criar AlertExceptionHandler e AlertContextConfiguration
    - _Requirements: 12.1, 12.3, 12.4, 12.5, 12.7, 12.9, 12.10_

  - [x] 10.4 Write property test for Alert Generation (Property 5)
    - **Property 5: Alert Generation Correctness**
    - **Validates: Requirements 12.2, 12.3, 12.4, 12.8**
    - Verificar: alert criado iff GENERATE_ALERT em actions; idempotente para mesmo (transactionId, ruleId)

- [x] 11. Modificações no Screening Context Existente
  - [x] 11.1 Adicionar customerId ao fluxo de Keyword Screening
    - Adicionar campo `customerId: String` ao `EvaluateKeywordScreeningCommand` (non-blank, max 64 chars)
    - Atualizar `EvaluateKeywordScreeningRequest` (DTO de input HTTP) para incluir customerId com validação @NotBlank @Size(max=64)
    - Atualizar controller e testes existentes
    - _Requirements: 4.2_

  - [x] 11.2 Publicar DetectionEvent no KeywordScreeningService
    - Injetar `DomainEventPublisher` (da shared.domain) no KeywordScreeningService
    - Após persistir ScreeningResult e antes de retornar, publicar `DetectionEvent` com transactionId, customerId, ruleCode, detectionResult (mapped from ScreeningResult)
    - Criar DomainEventPublisherAdapter no Screening Context (infrastructure) implementando o port via ApplicationEventPublisher
    - _Requirements: 10.2, 10.10_

- [x] 12. Checkpoint — All Contexts Wired
  - Ensure all tests pass, ask the user if questions arise.

- [x] 13. Integration Tests e Wiring End-to-End
  - [x] 13.1 Criar DecisionFlowIntegrationTest (Testcontainers)
    - Testar fluxo completo: DetectionEvent → DecisionService → DecisionExecution persistido → DecisionMadeEvent publicado → Alert criado
    - Verificar idempotência com DB real (UNIQUE constraint)
    - Verificar seed data presente após Flyway migration
    - _Requirements: 4.7, 9.1, 10.2, 12.2_

  - [x] 13.2 Criar AlertCreationIntegrationTest (Testcontainers)
    - Testar: DecisionMadeEvent com GENERATE_ALERT → Alert(OPEN) no banco
    - Testar: DecisionMadeEvent com IGNORE → nenhum alert criado
    - Testar: Evento duplicado → sem alerta duplicado (idempotência)
    - _Requirements: 12.2, 12.3, 12.4_

  - [x] 13.3 Criar ActivationFlowIntegrationTest (Testcontainers)
    - Testar: criar config draft → dry-run → ativar (sucesso)
    - Testar: criar config draft → tentar ativar sem dry-run → rejeitado (422)
    - _Requirements: 18.1, 18.2_

  - [x] 13.4 Criar Controller Tests (MockMvc)
    - Testar RuleConfigurationController: criação válida (201), validação inválida (422), conflict (409), not found (404)
    - Testar DryRunController: dry-run válido (200), fact inválido (422)
    - Testar DecisionExecutionController: consulta paginada (200), not found (empty content)
    - Testar AlertController: GET alerts (200), PATCH status válido (200), transição inválida (422)
    - _Requirements: 8.6, 9.8, 12.9, 17.7_

- [x] 14. Final Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties using jqwik (JUnit5 integration)
- Unit tests validate specific examples and edge cases
- The design uses Kotlin throughout — all code examples use Kotlin 1.9
- Domain services are pure classes registered via @Configuration, NOT annotated with @Service
- JPA entities use hypersistence-utils for JSONB columns
- Spring Application Events for inter-context communication (same JVM)
- DomainEventPublisher port abstracts event publishing — never depend on ApplicationEventPublisher directly in domain

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "1.4", "1.5", "2.1", "2.2"] },
    { "id": 2, "tasks": ["2.3", "2.4", "2.5", "2.6", "2.7", "2.8"] },
    { "id": 3, "tasks": ["3.1", "3.5", "3.6"] },
    { "id": 4, "tasks": ["3.2", "3.3", "3.7"] },
    { "id": 5, "tasks": ["3.4", "3.8"] },
    { "id": 6, "tasks": ["3.9", "5.1"] },
    { "id": 7, "tasks": ["5.2", "5.5", "5.7", "5.9"] },
    { "id": 8, "tasks": ["5.3", "5.4", "5.6", "5.8", "5.10"] },
    { "id": 9, "tasks": ["7.1", "7.4", "7.5", "7.6"] },
    { "id": 10, "tasks": ["7.2", "7.3"] },
    { "id": 11, "tasks": ["8.1", "8.3", "8.4", "8.5", "8.6", "8.7"] },
    { "id": 12, "tasks": ["8.2", "10.1"] },
    { "id": 13, "tasks": ["10.2", "10.3"] },
    { "id": 14, "tasks": ["10.4", "11.1"] },
    { "id": 15, "tasks": ["11.2"] },
    { "id": 16, "tasks": ["13.1", "13.2", "13.3"] },
    { "id": 17, "tasks": ["13.4"] }
  ]
}
```
