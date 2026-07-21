# PLD Workbench — pacote de arquitetura e requisitos

Status: baseline de planejamento `v0.1`  
Escopo: análise de PLD do onboarding à revisão contínua, investigação transacional, decisão sobre relacionamento, dossiê regulatório e comunicação ao COAF.

Este pacote define um produto único para o analista, implementado em três aplicações independentes que vivem em um único repositório Git (monorepo, ver [ADR-004](adr/ADR-004-monorepo-layout.md)). Ele não transforma as três aplicações em um monólito nem distribui a mesma regra de negócio entre serviços.

## Resultado esperado

O analista deve conseguir, em uma única interface:

- entender quem é a pessoa física ou jurídica e quais relações importam;
- consultar fatos, fontes, evidências, divergências e indisponibilidades;
- acompanhar sinais de onboarding, revalidação e transações;
- receber casos derivados quando a automação não tiver evidência suficiente;
- registrar uma decisão explicável sobre conta/relacionamento e, separadamente, sobre suspeição;
- produzir o dossiê interno e a comunicação ao COAF quando aplicável;
- reconstruir depois o que ocorreu, quando, por quem, sob qual política e com quais evidências.

## Aplicações e fronteiras

| Aplicação | Situação | Responsabilidade exclusiva | Não deve possuir |
|---|---|---|---|
| `pld-transaction-screening/` | Serviço existente, expandido no monorepo | ingestão e análise de transações, regras transacionais, explicação da execução, sinais e projeção local de risco do cliente | fila humana, dossiê do cliente, decisão de conta, comunicação ao COAF |
| `pld-customer-analysis/` | Novo backend | visão consolidada do cliente, evidências, análises, deriva, casos, revalidação, decisões, dossiê, COAF, timeline e BFF do frontend | execução de regras de alta vazão para cada transação |
| `pld-workbench/` | Novo frontend React (Marco 4) | experiência única dos analistas e administradores, consumindo modelos de leitura do segundo backend | regras regulatórias, decisão automática ou integração direta com bancos de outros serviços |

O MCP pode ser acrescentado depois como outro adaptador dos casos de uso. Ele não é o núcleo do produto nem a API usada pelo frontend.

## Decisões estruturais

1. **Uma experiência, três aplicações.** O frontend esconde a distribuição, mas os backends continuam isolados e implantáveis separadamente.
2. **Um banco por backend.** Nenhum serviço lê tabelas do outro.
3. **Integração por eventos duráveis.** Cada publicador usa outbox; cada consumidor usa inbox/idempotência.
4. **Sem chamada remota no caminho crítico de cada transação.** O motor transacional mantém uma projeção local do perfil de risco.
5. **Workflow humano em um único lugar.** Casos, atribuições e decisões pertencem a `pld-customer-analysis`, inclusive os originados por transações.
6. **PF e PJ no mesmo fluxo.** O modelo comum é `Party`, com fatos e relações específicos por tipo.
7. **Decisão de relacionamento e decisão de suspeição são dimensões diferentes.** Encerrar uma conta não implica automaticamente comunicar ao COAF, e comunicar não define sozinho o estado da conta.
8. **Deriva não significa suspeita nem erro.** Significa que a política vigente não dispõe de evidência suficiente para concluir automaticamente.
9. **Auditoria é um requisito de domínio.** Logs técnicos ajudam a operar o sistema; não substituem a trilha regulatória imutável.
10. **O frontend não toma decisões.** Ele coleta intenção, evidencia consequências e envia comandos autenticados ao backend.

## Mapa dos documentos

### Compartilhados

- [Glossário de domínio](shared/domain-glossary.md)
- [Fluxos ponta a ponta](shared/end-to-end-flows.md)
- [Contratos de integração](shared/integration-contracts.md)
- [Requisitos não funcionais](shared/non-functional-requirements.md)
- [Base regulatória e controles](shared/regulatory-basis.md)

### Serviço transacional existente

- [Requisitos](../pld-transaction-screening/docs/pld-expansion/transaction-screening-requirements.md)
- [Arquitetura alvo](../pld-transaction-screening/docs/pld-expansion/transaction-screening-architecture.md)
- [Plano de migração](../pld-transaction-screening/docs/pld-expansion/transaction-screening-migration-plan.md)

### Novo backend

- [Requisitos](../pld-customer-analysis/docs/customer-analysis-requirements.md)
- [Arquitetura alvo](../pld-customer-analysis/docs/customer-analysis-architecture.md)

### Frontend React

- [Requisitos](frontend/frontend-requirements.md)
- [Arquitetura de informação](frontend/frontend-information-architecture.md)

### Decisões e execução

- [ADR-001 — fronteiras dos serviços](adr/ADR-001-service-boundaries.md)
- [ADR-002 — auditoria e eventos](adr/ADR-002-audit-and-eventing.md)
- [ADR-003 — decisão e deriva](adr/ADR-003-decision-and-derivation-model.md)
- [ADR-004 — monorepo](adr/ADR-004-monorepo-layout.md)
- [ADR-005 — convenções de integração](adr/ADR-005-integration-conventions.md)
- [ADR-006 — estratégia de identidade e acesso](adr/ADR-006-identity-access-strategy.md)
- [ADR-007 — plataforma de eventos](adr/ADR-007-eventing-platform.md)
- [ADR-008 — escopo do Marco 1 e ordenação de filas](adr/ADR-008-marco-1-scope-and-queue-ordering.md)
- [Decisões pendentes](open-decisions.md)
- [Marco 0 — tasks](marco-0-tasks.md)
- [Handoff para o agente local](agent-handoff.md)

## Ordem recomendada de implementação

1. Congelar o glossário, identificadores, envelope e eventos `v1`.
2. Preparar a fundação de `pld-customer-analysis`: `Party`, `AnalysisCycle`, timeline, outbox/inbox e segurança.
3. Evoluir `keyword-screening` para publicar sinais e consumir a projeção de risco, ainda preservando suas APIs atuais.
4. Entregar no novo backend o caso transacional unificado e o modelo de leitura usado pelo frontend.
5. Construir o frontend começando por fila, visão 360, análise e timeline.
6. Acrescentar evidências/enriquecimentos, revalidação e decisão de relacionamento.
7. Entregar dossiê e workflow de comunicação ao COAF.
8. Migrar a fila humana do serviço transacional e retirar os contratos legados depois de um período de convivência medido.

## Regra de governança da documentação

- Este pacote é a fonte da arquitetura entre as aplicações do monorepo.
- Cada aplicação mantém perto do código apenas seus requisitos, sua arquitetura e links para a versão dos contratos compartilhados.
- Mudança incompatível de evento exige nova versão de schema e período de convivência.
- Mudança na semântica de decisão exige aprovação de Produto/PLD/Compliance e uma versão de política rastreável.
- A base regulatória deve ser validada por Compliance/Jurídico antes de produção; estes documentos traduzem necessidades de produto e engenharia, não constituem parecer jurídico.
