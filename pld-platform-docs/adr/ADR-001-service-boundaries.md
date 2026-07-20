# ADR-001 — um produto, dois backends e um frontend

- Status: aceita para planejamento
- Data: 2026-07-20
- Decisores: Produto, Engenharia e PLD/Compliance a confirmar na revisão

## Contexto

O negócio precisa operar onboarding, revisão contínua, alertas transacionais, decisões e relatórios em um só lugar. Hoje há pouca integração e trabalho repetido. O repositório `keyword-screening` já possui screening, regras, decisão e alertas transacionais.

Foi considerada a possibilidade de colocar todas as capacidades em um monólito para simplificar integração e, eventualmente, melhorar performance/escala.

Os workloads, porém, são diferentes:

- avaliação transacional é de alta vazão, curta duração e sensível a latência;
- coleta de fontes, análise humana, dossiê e COAF são workflows longos, ricos em estado, documentos, timers e intervenções;
- deploy/escala de um não deve colocar o outro em risco;
- “um lugar” para o usuário não exige “um processo” no backend.

## Decisão

Construir um produto com três aplicações independentes:

1. Evoluir `keyword-screening` para o motor `pld-transaction-screening`.
2. Criar `pld-customer-analysis` como backend de análise centrada no cliente, workflow humano, decisões e regulatório.
3. Criar `pld-workbench` em React como interface única.

Cada backend será internamente um monólito modular, terá banco e deploy próprios e se integrará por eventos duráveis. O frontend usa `pld-customer-analysis` como BFF/projeção para as telas unificadas.

O motor transacional consome uma projeção local do risco e não chama o backend de clientes por transação.

## Ownership resumido

| Capacidade | Dono |
|---|---|
| regra/avaliação transacional e execução técnica | transaction screening |
| perfil consolidado, evidências e política do cliente | customer analysis |
| caso, deriva e decisão humana | customer analysis |
| decisão de relacionamento/suspeição | customer analysis |
| dossiê e COAF | customer analysis |
| experiência do analista | workbench |

## Consequências positivas

- experiência integrada sem frontend distribuindo lógica;
- escala e disponibilidade independentes do hot path transacional;
- transações continuam processando quando o workflow estiver indisponível;
- caso/dossiê podem usar consistência local e modelo relacional rico;
- ownership evita duas filas e decisões conflitantes;
- extração futura de módulo continua possível se houver motivo medido.

## Custos e riscos

- consistência eventual nas telas;
- necessidade de broker, schema governance, outbox/inbox e reconciliação;
- operação de três deploys;
- projeções duplicam um subconjunto controlado de dados;
- debugging exige correlação entre serviços.

Mitigações estão em `shared/integration-contracts.md` e `shared/non-functional-requirements.md`.

## Alternativas rejeitadas

### Um único monólito para tudo

Rejeitado porque acopla escala/deploy/falha de avaliação transacional a workflows longos e regulatórios. A possível redução de chamadas remotas não compensa: o desenho escolhido remove chamadas do hot path com projeção local.

### Microserviço por capacidade/fonte

Rejeitado neste estágio por introduzir transações distribuídas e custo operacional sem ownership/equipe/escala que justifique a fragmentação.

### Frontend consultando todos os serviços

Rejeitado porque espalha composição, autorização e tratamento de consistência para o browser e torna o dossiê/timeline difíceis de reproduzir.

## Gatilhos para revisar a decisão

- módulo interno exige escala muito diferente e mensurada;
- ownership passa a equipes autônomas com roadmap/deploy independente;
- requisito de isolamento regulatório/segurança não pode ser atendido por módulo;
- banco/workload causa contenção comprovada apesar de otimização;
- fronteira de evento está madura o suficiente para extração sem duplicar invariantes.

