# ADR-002 — eventos duráveis e auditoria de domínio

- Status: aceita para planejamento
- Data: 2026-07-20

## Contexto

O produto precisa explicar, anos depois, quando um cliente foi analisado, por quem, com quais dados, sob qual política e por que uma decisão foi tomada. Ao mesmo tempo, os backends precisam trocar sinais e perfil de risco sem banco compartilhado e sem perder eventos.

Logs, traces e Spring application events atuais resolvem problemas operacionais locais, mas não constituem uma trilha regulatória durável nem garantem entrega entre deploys.

## Decisão

1. Cada backend mantém registros de domínio versionados/append-only para evidências, assessments, decisões, execuções e comunicações.
2. Mudanças destinadas a outros deploys são gravadas em transactional outbox no mesmo commit do aggregate.
3. Consumidores implementam inbox/idempotência e aceitam entrega at-least-once.
4. Eventos usam envelope comum com `eventId`, versão, ator, correlação, causação, subject IDs e classificação.
5. Projeções locais são reconstruíveis e reconciliáveis.
6. Timeline regulatória é uma projeção de atividades de domínio; observabilidade técnica permanece separada.
7. Correção histórica cria versão sucessora, compensação ou retificação; não edita o passado.

## Camadas de registro

| Camada | Finalidade | Exemplo | Retenção/acesso |
|---|---|---|---|
| estado de domínio | operar invariantes atuais | caso aberto, versão atual do perfil | política do objeto |
| história regulatória | provar fatos/decisões | assessment v3, decisão, manifesto | retenção regulatória e acesso restrito |
| evento de integração | propagar fato entre serviços | `TransactionSignalDetected.v1` | suficiente para replay/reconciliação |
| timeline | navegação humana consolidada | “decisão aprovada” | reconstruível e filtrada por RBAC |
| log/trace/métrica | diagnosticar operação | timeout no adapter | retenção operacional, sem PII |

Uma camada não substitui a outra.

## Semântica de entrega

- O publicador pode enviar o mesmo evento mais de uma vez.
- O consumidor decide efeito uma vez por `(consumerName, eventId)`.
- A ordem global não é garantida; versões por entidade impedem regressão.
- Evento fora de ordem pode ser guardado, aplicado temporalmente ou reconciliado segundo contrato.
- Replay tem finalidade explícita e não repete efeito externo por padrão.
- DLQ é quarentena operacional, não destino final silencioso.

## Integridade de decisão

Toda decisão material deve referenciar:

```text
decisionId
decisionVersion
partyId / accountId
analysisCycleId / caseId
actorId / actorRole
decidedAt
policyId / policyVersion
input snapshot/finding/evidence IDs and versions
structured reason codes
narrative
route and approvals
correlationId / causationId
supersedesDecisionId?
```

Hash/manifesto é usado para artefatos fechados. Não usar blockchain como requisito: integridade, acesso, versionamento e backups atendem ao problema sem infraestrutura adicional, salvo exigência futura comprovada.

## Consequências

Positivas:

- nenhuma janela entre commit e publish perde sinal;
- reentrega/replay são seguros;
- é possível explicar estado histórico e divergência entre serviços;
- timeline útil sem misturar logs técnicos;
- dossiê é composto de versões estáveis.

Custos:

- tabelas/workers de outbox/inbox;
- governança de schemas e compatibilidade;
- projeções eventualmente consistentes;
- armazenamento adicional;
- ferramentas de reconciliação e replay.

## Alternativas rejeitadas

- **Publicar diretamente depois do commit:** há janela de perda em crash.
- **Banco compartilhado:** remove ownership e acopla deploy/migration.
- **Exactly-once como promessa fim a fim:** não elimina necessidade de idempotência nos efeitos externos.
- **Logs como auditoria:** são mutáveis, ruidosos, têm público/retenção inadequados e não preservam semântica de domínio.
- **Event sourcing integral desde o início:** complexidade maior que a necessária; história append-only e eventos seletivos atendem ao requisito atual.

