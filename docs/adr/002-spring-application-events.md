# ADR-002: Spring Application Events para Comunicação Inter-Contexto

## Status

Aceita

## Contexto

Os bounded contexts (Screening, Decision, Alert) precisam se comunicar sem acoplamento direto. O sistema opera como mono-deployment (mesma JVM) no MVP.

## Decisão

Utilizamos Spring Application Events para comunicação entre bounded contexts:

- **DetectionEvent** — Screening Context → Decision Context
- **DecisionMadeEvent** — Decision Context → Alert Context

### Padrões adotados

1. **DomainEventPublisher** como port abstraction (nunca `ApplicationEventPublisher` direto no domínio)
2. **@TransactionalEventListener(phase = AFTER_COMMIT)** para listeners que dependem de dados persistidos
3. **@EventListener** para listeners que não dependem de transação do publisher

### Evolução planejada

| Fase | Mecanismo |
|------|-----------|
| MVP | Spring Application Events (intra-JVM) |
| Fase 2 | Transactional Outbox pattern |
| Fase 3 | AWS SQS (inter-serviço) |

## Consequências

- Baixo acoplamento: contextos reagem a eventos sem dependência mútua
- Publicação síncrona no MVP (simplifica debugging)
- Preparado para migração assíncrona sem alterar o domínio
- Listeners devem ser idempotentes (eventos podem ser reprocessados)
