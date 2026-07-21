# Marco 1 — tasks

Status: em andamento  
Escopo: fundação vertical de `pld-customer-analysis`, conforme ADR-008.

## Concluído

- [x] Scaffold Spring Boot/Kotlin de `pld-customer-analysis` com Gradle, Java 21, Flyway e PostgreSQL.
- [x] Banco separado no `docker-compose.yml` do monorepo.
- [x] Migration inicial com `party`, `party_snapshot`, `analysis_cycle`, `timeline_entry`, `outbox_event` e `inbox_event`.
- [x] API mínima para criar e consultar `Party`.
- [x] API mínima para abrir `AnalysisCycle`.
- [x] Timeline mínima para `PARTY_CREATED` e `ANALYSIS_CYCLE_CREATED`.
- [x] Publicação transacional em outbox para `PartyCreated` e `AnalysisCycleCreated`.
- [x] Inbox idempotente por `(consumerName, eventId)`.
- [x] Dreno básico da outbox com porta `OutboxPublisher` e marcação de eventos publicados.
- [x] Adapter SQS/LocalStack para `OutboxPublisher`.
- [x] Scheduler/configuração para drenar a outbox fora do caminho síncrono da API.
- [x] Testes de integração com PostgreSQL/Testcontainers cobrindo Party, AnalysisCycle, timeline, outbox e inbox.
- [x] Segurança dev via headers (`X-Actor-Id`, `X-Actor-Role`, `X-Correlation-Id`) aplicada às APIs de escrita.
- [x] Primeiro consumidor externo: `TransactionSignalDetected.v1` registrado idempotentemente na timeline.

## Próximo corte

- [ ] Health/OpenAPI/métricas mínimos do novo backend.

## Fora do Marco 1

- Caso humano completo, fila de trabalho e atribuição.
- Decisão de relacionamento/suspeição.
- Dossiê regulatório e comunicação ao COAF.
- Frontend React.
