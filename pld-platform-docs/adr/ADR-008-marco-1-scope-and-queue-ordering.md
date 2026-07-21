# ADR-008 — escopo do Marco 1 e ordenação de filas

- Status: aceita
- Data: 2026-07-21

## Contexto

O documento de requisitos chama de "MVP recomendado" uma fatia que inclui caso, decisão humana e consumo de sinais transacionais. O handoff, porém, define o Marco 1 como esqueleto do novo backend: `Party`, `AnalysisCycle`, timeline, outbox/inbox, segurança e APIs mínimas.

Também havia decisão em aberto sobre SQS standard vs FIFO. A plataforma já decidiu usar AWS SQS e LocalStack (ADR-007), mas ainda não havia regra para filas que exigem ordenação por chave.

## Decisões

1. **Marco 1 segue o handoff, não o MVP completo.** A primeira fatia implementável é fundação vertical: `Party` manual, abertura de `AnalysisCycle`, timeline mínima, outbox/inbox base, health/OpenAPI/métricas. Caso/fila completa, decisão de conta/suspeição, consumo transacional, dossiê e COAF ficam para marcos posteriores.
2. **SQS Standard é o padrão.** Consumidores devem ser idempotentes, tolerar duplicidade e lidar com fora de ordem conforme ADR-002 e os contratos v1.
3. **SQS FIFO é exceção por fila.** Só usar FIFO quando houver requisito local de ordenação mensurado, por exemplo ordenação por `partyId` via `MessageGroupId`. A decisão é por fila, não global.

## Consequências

- O scaffold de `pld-customer-analysis` pode nascer pequeno e implantável, sem carregar o workflow humano inteiro.
- O primeiro código pode focar em API mínima, migrations, auditoria/timeline e base de integração.
- Não introduzimos custo operacional de FIFO antes de existir caso de uso que precise dele.
- INT-2 e INT-6 deixam de ser pendências em `open-decisions.md`.
