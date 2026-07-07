# ADR-006: Bounded Contexts na Mesma JVM (Mono-deployment)

## Status

Aceita

## Contexto

O MVP da Rule Platform introduz 3 bounded contexts: Screening (existente), Decision (novo), e Alert (novo). Deployar cada um como microsserviço aumentaria complexidade operacional sem benefício claro no MVP.

## Decisão

Todos os bounded contexts compartilham o mesmo deployment Spring Boot (mesma JVM), mas mantêm **separação lógica completa**:

```
br.com.screening/    # Contexto existente (MF09)
br.com.decision/     # Decision Engine (novo)
br.com.alert/        # Alert Context (novo)
br.com.shared/       # Apenas interfaces compartilhadas (DomainEvent, DomainException)
```

### Regras de isolamento

- Cada contexto possui suas próprias tabelas (sem FKs cross-context)
- Comunicação exclusivamente por eventos de domínio (Spring Application Events)
- Cada contexto tem seu próprio `@ControllerAdvice` scoped
- Shared domain contém apenas interfaces marker (DomainEvent, DomainException, DomainEventPublisher)

### Evolução planejada

| Fase | Topology |
|------|----------|
| MVP | Mono-deployment (3 contexts, 1 JVM) |
| Fase 2 | Alert Context extraído como microsserviço |
| Fase 3 | Decision Context como serviço independente |

## Consequências

- Simplicidade operacional (1 deployment, 1 banco)
- Comunicação sem latência de rede
- Risco de acoplamento acidental (mitigado por code review + package boundaries)
- Extração futura facilitada pela separação por pacotes e comunicação por eventos
