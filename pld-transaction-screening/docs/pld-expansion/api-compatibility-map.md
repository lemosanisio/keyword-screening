# Mapa de compatibilidade das APIs atuais

Fonte: `src/main/resources/static/openapi/openapi.yaml` (versão 1.0.0). Objetivo do Marco 0: identificar quais contratos HTTP atuais precisam permanecer compatíveis durante a migração para os eventos `v1` e para o novo backend `pld-customer-analysis`.

Regra geral: **nenhuma mudança comportamental em produção no Marco 0**. Novos eventos e projeções entram por convivência; remoções só depois de medir dependências e confirmar substitutos.

## Mantidas no `pld-transaction-screening`

Estas APIs pertencem ao motor transacional/regra e continuam sendo responsabilidade deste serviço.

| Método | Path | Operation | Compatibilidade |
|---|---|---|---|
| GET | `/v1/decision/rules` | `listRules` | Manter. Catálogo técnico de regras permanece no motor. |
| GET | `/v1/decision/rules/{code}` | `getRuleByCode` | Manter. |
| GET | `/v1/decision/facts` | `listFacts` | Manter. Pode evoluir para incluir fatos vindos da projeção local de risco. |
| GET | `/v1/decision/entities` | `listEntities` | Manter. |
| POST | `/v1/decision/rules/{ruleCode}/configurations` | `createRuleConfiguration` | Manter. Administração transacional segue no motor. |
| GET | `/v1/decision/rules/{ruleCode}/configurations` | `listConfigurationsByRuleCode` | Manter. |
| GET | `/v1/decision/rule-configurations/{id}` | `getConfigurationById` | Manter. |
| PUT | `/v1/decision/rule-configurations/{id}` | `updateRuleConfiguration` | Manter. |
| POST | `/v1/decision/rule-configurations/{id}/activate` | `activateConfiguration` | Manter. No Marco 2 deve publicar `RuleConfigurationActivated.v1`. |
| POST | `/v1/decision/rule-configurations/{id}/deactivate` | `deactivateConfiguration` | Manter. |
| GET | `/v1/decision/rule-configurations/{id}/versions` | `getVersionHistory` | Manter. |
| POST | `/v1/decision/rule-configurations/{id}/dry-run` | `executeDryRun` | Manter. Dry-run não publica eventos externos. |
| GET | `/v1/decision/executions` | `searchExecutions` | Manter. Futuramente complementa a projeção consumida pelo customer-analysis. |
| GET | `/v1/decision/executions/{id}` | `getExecutionById` | Manter. |

## Mantidas com publicação/eventos futuros

Estas APIs continuam existindo e passam a produzir eventos duráveis sem alterar a resposta HTTP atual.

| Método | Path | Operation | Evolução planejada |
|---|---|---|---|
| POST | `/v1/rules/keyword-screening/evaluate` | `evaluateKeywordScreening` | Manter resposta atual. No Marco 7: persistir snapshot/avaliação e publicar `TransactionEvaluationCompleted.v2`, `TransactionSignalDetected.v1` e, quando aplicável, `ManualReviewRequested.v2`. |
| POST | `/v1/rules/contextual-screening/evaluate` | `evaluateContextualScreening` | Manter resposta atual. Futuro: decidir se avaliação contextual participa do mesmo fluxo de eventos ou permanece como capacidade auxiliar/legada. |

## Legado em convivência

Estas APIs representam a fila humana local antiga. O ownership futuro de caso/fila/decisão humana é `pld-customer-analysis` (ADR-001). Devem permanecer até existir substituto e período de comparação.

| Método | Path | Operation | Plano |
|---|---|---|---|
| GET | `/v1/alerts` | `searchAlerts` | Legado. Manter durante convivência; futuro substituto é fila/casos do customer-analysis. |
| GET | `/v1/alerts/{alertId}` | `getAlertById` | Legado. |
| PATCH/PUT | `/v1/alerts/{alertId}/status` | `updateAlertStatus` | Legado. Bloquear novas decisões humanas somente em marco posterior, após comparação e migração. |
| POST | `/v1/rules/contextual-screening/decisions` | `registerAnalystDecision` | Legado/ponte. Futuro substituto é decisão em caso no customer-analysis. |

## Invariantes de compatibilidade

- Não remover path, campo obrigatório ou valor de enum existente sem janela de convivência.
- Não mudar semântica de status HTTP já publicado.
- Novos campos de resposta devem ser aditivos e opcionais.
- Publicação de eventos não pode alterar resposta HTTP atual.
- APIs legadas devem ter telemetria de uso antes de qualquer remoção.
- Se o Workbench precisar expor administração transacional, usar BFF/gateway/fachada autorizada; não duplicar regra no customer-analysis.

## Dependências para retirada futura

- `pld-customer-analysis` consumindo `ManualReviewRequested.v2` com inbox e abrindo/atualizando caso sem duplicidade.
- Workbench usando fila/caso do customer-analysis como tela principal.
- Período medido comparando casos gerados no legado e no novo fluxo.
- Confirmação de que não há consumidores externos dos endpoints legados de alertas/decisão contextual.
