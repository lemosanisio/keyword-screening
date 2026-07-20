# `keyword-screening` → `pld-transaction-screening` — plano de migração

## Objetivo

Evoluir o repositório existente sem uma reescrita big bang. O plano mantém os contextos e testes úteis, introduz contratos duráveis e transfere apenas o workflow humano para o novo backend.

## Diagnóstico do ponto de partida

Aspectos a preservar:

- separação conceitual entre `screening`, `decision` e `alert`;
- arquitetura hexagonal com ports/adapters;
- regras e configurações versionadas;
- dry-run;
- auditoria de `DecisionExecution`;
- testes de domínio/property-based e ADRs já existentes;
- API-first/OpenAPI e Flyway.

Lacunas prioritárias:

- `alert` mistura evento técnico com fila/decisão humana que será de outro serviço;
- risco do cliente é resolvido por REST no caminho da decisão;
- idempotência baseada apenas em transação/regra não representa replay e versões;
- fato ausente pode colapsar para falso/IGNORE;
- eventos Spring locais não garantem entrega entre deploys;
- execução não congela todo o contexto histórico necessário;
- governança de regra precisa maker-checker, vigência, backtest e rollback explícitos;
- feedback de analista precisa identidade, autorização e ownership no novo backend.

## Princípios da migração

- Uma mudança de persistência por vez, com migration compatível.
- Dual-write apenas via padrão controlado; evitar dois commits remotos.
- Preferir dual-publish/shadow consumer para comparar saídas.
- Compatibilidade vem antes da remoção.
- Renomear pacotes/repositório depois de estabilizar semântica, não no mesmo PR.
- Cada fase possui feature flag, métricas e condição objetiva de saída.

## Fase 0 — baseline e proteção

Entregas:

- inventário de endpoints, consumidores e tabelas atuais;
- testes de caracterização para keyword, contextual screening, rule engine e alert;
- fixture anonimizada de transações e resultados atuais;
- métricas de throughput, latência, erros e alertas gerados;
- ADR que referencia as novas fronteiras;
- contrato `v1` e exemplos dourados.

Condição de saída: suíte reproduz o comportamento atual e consumidores conhecidos estão mapeados.

## Fase 1 — identidade e snapshot da avaliação

Entregas:

- adicionar `evaluation_id`, `purpose`, `input_event_id/version`, `ruleset_version`, `risk_profile_version`, correlação/causação;
- nova chave de idempotência;
- congelar snapshot/hash e explicação usada;
- manter leitura dos registros antigos com campos ausentes sinalizados como `LEGACY`;
- endpoints de consulta novos em paralelo aos existentes.

Estratégia de banco:

1. adicionar colunas nullable e tabelas novas;
2. aplicação passa a escrever novo formato;
3. backfill somente do que puder ser inferido com segurança;
4. tornar constraints obrigatórias apenas para novos registros/versões;
5. nunca fabricar versão histórica inexistente.

Condição de saída: toda nova execução live possui contexto e identificadores completos.

## Fase 2 — fatos tri-state

Entregas:

- novo `FactResult` e `ExpressionResult`;
- adapters existentes retornam qualidade explícita;
- política de `INDETERMINATE` por regra/contexto;
- dashboard de fatos `UNKNOWN`, `STALE` e `ERROR`;
- testes de regressão comparando decisão velha/nova.

Rollout:

- rodar avaliador novo em shadow inicialmente;
- classificar divergências em correção de bug, mudança de política ou problema de dado;
- ativar por rule category/percentual;
- manter rollback para avaliador legado durante janela limitada.

Condição de saída: nenhuma ausência é convertida silenciosamente em falso.

## Fase 3 — outbox e eventos externos

Entregas:

- tabela/publisher de outbox;
- envelope comum e schemas versionados;
- `TransactionEvaluationCompleted`, `TransactionSignalDetected` e `ManualReviewRequested`;
- inbox nos consumidores de teste;
- DLQ/quarentena, replay e runbook;
- métricas de idade/lag da outbox.

Teste crítico: matar o processo depois do commit de negócio e antes do publish; ao reiniciar, o evento deve sair uma vez ou ser reentregue sem duplicar efeito.

Condição de saída: todo sinal live pode ser rastreado do evento de entrada ao evento publicado.

## Fase 4 — projeção local de risco

Entregas:

- consumidor de `CustomerRiskProfileUpdated.v1` com inbox;
- história temporal e projeção atual;
- adapter de `CustomerRiskPort` apontando para repositório local;
- flags `risk-source=rest|projection|compare`;
- comparação entre resposta REST e projeção;
- métricas de lag/gap/staleness e reconciliador.

Rollout:

1. consumir sem usar;
2. comparar em shadow;
3. usar projeção para parte do tráfego;
4. tornar projeção padrão;
5. remover chamada REST depois de uma janela estável e de confirmar ausência de consumidores ocultos.

Condição de saída: indisponibilidade do backend de clientes não altera a disponibilidade do hot path.

## Fase 5 — mover a fila humana

Entregas:

- novo backend consome `ManualReviewRequested` e devolve correlação do caso;
- dual-run entre `Alert` local e caso novo;
- relatório de equivalência/duplicidade;
- redirecionamento de links para o Workbench;
- flag que torna o caso novo a única superfície gravável;
- `AlertController` e decisão local marcados como deprecated.

Não migrar apenas o status. Para casos abertos necessários, migrar evento de origem, explicação, decisões/atores e anexos conforme mapeamento aprovado. Manter o legado somente leitura pelo período de retenção ou arquivá-lo de forma consultável.

Condição de saída: nenhum analista registra decisão no motor transacional e todos os sinais relevantes têm caso/projeção quando a política exige.

## Fase 6 — governança de regras

Entregas:

- separar definition/configuration/ruleset/activation;
- workflow maker-checker e RBAC;
- vigência agendada;
- backtest assíncrono e comparação de impacto;
- rollback auditado;
- evento de ativação;
- UI administrativa pelo Workbench/BFF ou rota autorizada.

Condição de saída: nenhuma regra chega a `ACTIVE` por edição direta de banco/API sem aprovação registrada.

## Fase 7 — limpeza e renomeação opcional

- remover flags e código legado após telemetria confirmar uso zero;
- remover REST de risco e workflow `alert` gravável;
- consolidar migrations/documentação;
- renomear artefato/repositório para `pld-transaction-screening` somente se o time quiser refletir o escopo ampliado;
- preservar redirects, coordenadas Maven e dashboards durante a transição.

## Matriz de compatibilidade

| Superfície | Durante migração | Estado alvo |
|---|---|---|
| endpoints atuais de screening | mantidos e observados | manter se ainda forem contratos úteis; versionar |
| endpoint de alertas | leitura/escrita sob flag | somente legado/read-only, depois remover |
| decisão humana local | dual-run controlado | removida |
| REST de customer risk | compare/fallback | removido do hot path |
| Spring application events | comunicação interna | permanecem internos |
| eventos de integração | novos em shadow | contrato oficial |
| idempotência antiga | leitura compatível | substituída para novos registros |

## Checklist de cada release

- [ ] migration testada em base representativa e reversão lógica descrita;
- [ ] schema de evento compatível e contrato aprovado;
- [ ] feature flag com owner/data de remoção;
- [ ] dashboards e alertas antes da ativação;
- [ ] comparação shadow sem PII em logs;
- [ ] idempotência testada com duplicidade e retry;
- [ ] replay não produz efeitos operacionais;
- [ ] documentação/API atualizada;
- [ ] segurança/autorização verificadas;
- [ ] condição de saída da fase medida.

## Critério de conclusão da migração

A migração termina quando:

- o motor avalia transações sem dependência síncrona do novo backend;
- toda execução nova é reproduzível e versionada;
- sinais chegam ao caso unificado sem duplicidade;
- analistas usam exclusivamente o Workbench para decisões;
- regras possuem aprovação, vigência, backtest e rollback;
- superfícies legadas não têm uso observado e foram retiradas com plano de retenção.

