# Marco 6 — integração transacional real

Status: concluído e validado

## Objetivo

Conectar o fluxo real de decisão do `pld-transaction-screening` ao `pld-customer-analysis` por outbox e SQS. Uma decisão `GENERATE_ALERT` elegível deve publicar `TransactionSignalDetected.v1` e criar exatamente um caso correlacionado, sem endpoint de cenário.

## Decisões do marco

- [x] Implementar uma fatia vertical somente com `TransactionSignalDetected.v1`.
- [x] Publicar somente depois de uma decisão `GENERATE_ALERT`.
- [x] Exigir `customerId` no formato `pty_<ULID>` para emissão externa.
- [x] Preservar APIs atuais e o `Alert` legado em dual-run.
- [x] Usar SQS Standard, outbox e entrega at-least-once.
- [x] Publicar diretamente o envelope v1 como body da mensagem.
- [x] Usar a versão da configuração ativa como `ruleVersion` nesta fatia.
- [x] Mapear keyword screening para severidade `HIGH` e rota `DERIVED_TO_ANALYST` como política exploratória.

## Entregas

- [x] Propagar correlação HTTP até decisão e evento externo.
- [x] Persistir `evaluationId`, `partyId`, correlação e causação na execução.
- [x] Criar outbox transacional no motor.
- [x] Gerar envelope `TransactionSignalDetected.v1` válido.
- [x] Publicar outbox no SQS com retry e identidade estável.
- [x] Preservar criação do `Alert` legado.
- [x] Endurecer validação e vocabulários do consumidor.
- [x] Permitir consumo shadow sem abertura de caso.
- [x] Configurar fila principal e DLQ no ambiente local.
- [x] Testar duplicidade, retry e recuperação da outbox.
- [x] Validar o fluxo completo sem seed no Workbench.

## Critérios de aceite

- [x] `GENERATE_ALERT` elegível cria decisão e outbox atomicamente.
- [x] `IGNORE` e identificadores legados não geram evento externo.
- [x] O evento produzido valida contra `TransactionSignalDetected.schema.json`.
- [x] Falha temporária de publicação não perde o evento.
- [x] Retry preserva `eventId`, `signalId` e `evaluationId`.
- [x] Reentrega não duplica inbox, timeline, fonte ou caso.
- [x] A correlação é rastreável da API até o caso.
- [x] O `Alert` legado continua sendo criado.
- [x] Uma chamada real ao motor cria exatamente um caso consultável pelo Workbench.

## Fora deste marco

- `TransactionEvaluationCompleted.v1` e `ManualReviewRequested.v1`.
- Ingestão de `TransactionOccurred.v1`.
- Fatos tri-state e projeção local de risco.
- Retirada da consulta REST de risco.
- Desativação do `Alert` legado.
- Migração ou tradução de identificadores legados.
- Onboarding, fontes reais, dossiê e COAF.
