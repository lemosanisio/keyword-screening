# ADR-007 — plataforma de eventos: AWS SQS, LocalStack em dev

- Status: aceita
- Data: 2026-07-20

## Contexto

ADR-002 e os contratos exigem broker durável, entrega at-least-once, outbox/inbox e replay controlado. A empresa já opera **AWS SQS** para mensageria. O ambiente de desenvolvimento precisa de uma simulação local fiel.

Foi considerado RabbitMQ como simulador local. Rejeitado: RabbitMQ fala AMQP, com semântica e SDK diferentes da API SQS — o adapter de produção (SQS) nunca seria exercitado em dev, e manteríamos dois adapters de messaging sem necessidade.

## Decisão

1. **AWS SQS** é o broker da plataforma em todos os ambientes reais.
2. **LocalStack (community)** emula SQS em dev e testes: um serviço a mais no `docker-compose.yml`, e o código usa o **mesmo AWS SDK v2** apontando para o endpoint local. Sem código específico de dev.
3. Testes de integração usam LocalStack via Testcontainers, seguindo o padrão já existente no `pld-transaction-screening`.
4. Acesso ao broker ocorre atrás de portas de messaging de cada backend (`integration` module); o domínio não conhece SQS.
5. Outbox/inbox permanecem como tabelas de cada backend (ADR-002); o relay publica no SQS, consumidores deduplicam por `(consumerName, eventId)`.
6. **SQS standard vs FIFO fica por fila**, decidido no design do outbox de cada serviço (ordenacao por `partyId` via `MessageGroupId` onde necessário — ver INT-6 em `open-decisions.md`).
7. Credenciais em dev são fake/fixas do LocalStack; segredos reais nunca no repositório (NFR-02).

## Consequências

- Um único adapter de messaging por backend, testado localmente com a mesma API de produção.
- `docker-compose.yml` da raiz passa a ter `localstack` ao lado do `postgres`.
- Payloads grandes continuam fora do broker (NFR-05): eventos carregam referência e metadados.
- Custo operacional novo: gerenciar filas, DLQs e permissões IAM na AWS; runbooks de backlog/DLQ (NFR-04) devem cobrir SQS.
