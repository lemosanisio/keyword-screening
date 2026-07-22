# Marco 9 — Adapters de evidência simulados e visão 360

Status: não iniciado

## Objetivo

Implementar adapters de evidência simulados com proveniência, qualidade, retry e latência realista, substituindo os cenários hardcoded atuais. Expandir o frontend com visão 360 da parte, mídia/processos e aliases — tudo sobre dados simulados mas com contratos reais.

## Hipóteses

- Adapters simulados com falha/latência validam a resiliência do sistema antes de integrar fontes reais.
- A evidence matrix deixa de usar cenários fixos e passa a executar adapters reais (porém mock).
- A visão 360 torna o analista autônomo — toda informação relevante está numa tela.

## Decisões do marco

- [ ] Cada adapter simulado implementa a mesma interface/port que o adapter real usará.
- [ ] Dados são gerados deterministicamente a partir do `partyId` (reproducível para testes).
- [ ] Falhas simuladas: 10% timeout, 5% error, 5% partial — configurável via property.
- [ ] Cada execução registra tentativa, duração, status e evidências com hash de integridade.
- [ ] O frontend não distingue adapter real de simulado — a interface é a mesma.

## Fatias de entrega

### M9.1 — Port e framework de adapters de evidência

- [ ] Definir interface `EvidenceSourceAdapter` com método `execute(partyId, requirementCode, attempt): SourceExecutionResult`.
- [ ] `SourceExecutionResult` contém: status (SUCCESS_WITH_DATA, SUCCESS_NO_RESULTS, PARTIAL, UNAVAILABLE, ERROR, EXPIRED), evidence records, facts, duration, errorCode.
- [ ] Criar `EvidenceSourceRegistry` para descoberta de adapters por `sourceCode`.
- [ ] Refatorar `EvidenceService` para usar adapters via registry em vez de cenários hardcoded.
- [ ] Manter fallback para cenário demo quando nenhum adapter está registrado para o requirement.

### M9.2 — Adapter: Bureau de crédito (simulado)

- [ ] sourceCode: `CREDIT_BUREAU`
- [ ] Retorna: score, renda presumida, endereços, alertas de crédito, histórico de consultas.
- [ ] Fatos gerados: `creditScore` (NUMBER), `presumedIncome` (MONEY), `creditAlerts` (NUMBER).
- [ ] Simula latência 200-800ms, timeout 10% configurável.
- [ ] Evidence record com structuredData tipado e hash SHA-256.

### M9.3 — Adapter: Listas e sanções (simulado)

- [ ] sourceCode: `SANCTIONS_LISTS`
- [ ] Consulta simulada contra: OFAC, EU, ONU, PEP nacional.
- [ ] Retorna: matches com score de similaridade, nome candidato, lista, data de inclusão.
- [ ] Fatos: `sanctionsHit` (BOOLEAN), `pepStatus` (ENUM: NONE/DIRECT/RELATED), `sanctionsMatchScore` (NUMBER).
- [ ] Simula: 5% de false positive (match parcial), 3% indisponível.

### M9.4 — Adapter: Processos judiciais (simulado)

- [ ] sourceCode: `LEGAL_PROCEEDINGS`
- [ ] Retorna: processos com número, tribunal, classe, assuntos, papel (autor/réu/terceiro), status.
- [ ] Fatos: `activeProceedings` (NUMBER), `criminalProceedings` (BOOLEAN), `financialProceedings` (BOOLEAN).
- [ ] Dados derivados deterministicamente do partyId.
- [ ] Simula latência 500-2000ms (consulta mais lenta).

### M9.5 — Adapter: Mídia negativa (simulado)

- [ ] sourceCode: `NEGATIVE_MEDIA`
- [ ] Retorna: menções com veículo, data, título, resumo (gerado), entidade resolvida, relevância.
- [ ] Fatos: `negativeMediaCount` (NUMBER), `highRelevanceMediaCount` (NUMBER).
- [ ] Marca resumos como "extração auxiliar por IA" conforme FE-FR-008.
- [ ] Simula 5% de resultados conflitantes (mesma pessoa, veículos divergentes).

### M9.6 — Evidence policy real (baseada em risco)

- [ ] Substituir cenários hardcoded por política versionada.
- [ ] Política define: por riskLevel, quais requirements são obrigatórios.
- [ ] LOW: apenas CREDIT_BUREAU + SANCTIONS_LISTS.
- [ ] MEDIUM: + LEGAL_PROCEEDINGS.
- [ ] HIGH: + NEGATIVE_MEDIA (todos obrigatórios).
- [ ] `policyVersion` registrada no `EvidenceCollection`.
- [ ] Prontidão decisória real: allowed=true somente quando todos os requirements obrigatórios estão SATISFIED.

### M9.7 — Frontend: Visão 360 expandida

- [ ] Expandir `PartySummary` para card completo (FE-FR-005):
  - Risco atual (badge colorido) com effectiveFrom/validUntil.
  - Segmentos (PEP, CROSS_BORDER, etc.).
  - Contas vinculadas com status.
  - Snapshot cadastral: nome, CPF/CNPJ (mascarado), endereço, data de referência.
- [ ] Seção "Evolução de risco" — mini-timeline com mudanças de riskLevel.
- [ ] API: `GET /v1/parties/:partyId/risk-history` (novo endpoint no BFF).

### M9.8 — Frontend: Mídia e processos

- [ ] Nova seção/tab no workspace do caso (FE-FR-008).
- [ ] Listagem de mídia: veículo, data, resumo, relevância, badge "IA" para extração.
- [ ] Listagem de processos: número, tribunal, classe, papel, status.
- [ ] Ação: confirmar/rejeitar vínculo com motivo.
- [ ] Distinção visual: menção vs condenação vs arquivamento.

### M9.9 — Frontend: Nomes e aliases

- [ ] Componente de comparação lado a lado (FE-FR-009).
- [ ] Exibir: nome cadastral vs candidato, score, algoritmo, origem do alias.
- [ ] Ação: match/no-match/inconclusivo com justificativa.
- [ ] Sem linguagem que apresente score como certeza.

## Cenários BDD

```gherkin
Scenario: adapter de bureau retorna dados com proveniência
  Given um Party com partyId=pty_ABC e requirement CREDIT_BUREAU obrigatório
  When o adapter CREDIT_BUREAU é executado
  Then uma SourceExecution com status SUCCESS_WITH_DATA é persistida
  And evidence records contêm creditScore, presumedIncome e hash de integridade
  And o requirement CREDIT_BUREAU é marcado SATISFIED

Scenario: adapter de sanções retorna indisponível
  Given um Party com partyId=pty_DEF e o adapter SANCTIONS_LISTS configurado com 100% unavailable
  When o adapter é executado
  Then o status é UNAVAILABLE com errorCode SIMULATED_OUTAGE
  And o requirement permanece PENDING
  And prontidão decisória retorna allowed=false com blockingReason

Scenario: retry de evidência após falha
  Given um requirement com última execução UNAVAILABLE
  When o analista clica "Retentar"
  Then uma nova tentativa (attempt=2) é executada
  And o resultado atualiza o requirement independentemente da tentativa anterior

Scenario: política de evidência HIGH exige todos os adapters
  Given um Party com riskLevel=HIGH
  When o ciclo de evidência é iniciado
  Then 4 requirements obrigatórios são criados: CREDIT_BUREAU, SANCTIONS_LISTS, LEGAL_PROCEEDINGS, NEGATIVE_MEDIA
  And prontidão decisória exige todos SATISFIED

Scenario: visão 360 mostra risco e segmentos atuais
  Given um Party com riskLevel=HIGH e segments=[PEP_RELATED, CROSS_BORDER]
  When o analista abre o caso no Workbench
  Then a visão 360 exibe badge "HIGH" e chips "PEP_RELATED", "CROSS_BORDER"
  And a data de validade da projeção é visível
```

## Critérios de aceite

- [ ] Todos os 4 adapters funcionam com dados simulados reproduzíveis.
- [ ] Falhas simuladas (timeout, error) são tratadas sem crash — requirement fica PENDING.
- [ ] Retry funciona end-to-end (UI → API → adapter → atualiza requirement).
- [ ] Prontidão decisória real bloqueia decisão quando evidence falta.
- [ ] Visão 360 mostra dados do party + risco + segmentos na sidebar.
- [ ] Mídia, processos e aliases são exibidos no workspace.
- [ ] Testes unitários, integração e Playwright permanecem verdes.
- [ ] Cada adapter < 2s de latência média nos testes.

## Fora deste marco

- Adapters reais (bureau/DataJud/OFAC).
- Geocodificação e Google Maps/Street View (FE-FR-010).
- Assessment automático baseado em evidências.
- Revalidação periódica.
