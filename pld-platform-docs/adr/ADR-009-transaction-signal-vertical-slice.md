# ADR-009 — fatia vertical do sinal transacional

- Status: aceita
- Data: 2026-07-21

## Contexto

O protótipo já consome `TransactionSignalDetected.v1`, mas os casos são demonstrados por cenários de desenvolvimento. O `pld-transaction-screening` ainda usa eventos Spring locais e não possui publicação durável. Avançar para dossiê e COAF sobre esse caminho simulado deixaria a principal fronteira entre serviços sem validação real.

## Decisões

1. O próximo incremento recupera parte do desacoplamento transacional originalmente planejado para o Marco 2 antes do Marco 6 nominal de dossiê e COAF.
2. A primeira fatia publica somente `TransactionSignalDetected.v1`, após uma decisão `GENERATE_ALERT`.
3. A decisão e a outbox são persistidas na mesma transação. Um relay separado publica o envelope v1 diretamente em SQS Standard.
4. Somente avaliações cujo `customerId` já seja um `partyId` no formato `pty_<ULID>` são elegíveis. Entradas legadas continuam funcionando e não emitem contrato inválido.
5. O workflow de `Alert` legado permanece ativo em dual-run. Sua retirada exige comparação posterior e não pertence a esta fatia.
6. Para keyword screening, a integração exploratória usa severidade `HIGH`, rota `DERIVED_TO_ANALYST` e a versão da configuração como versão da regra.

## Consequências

- O caminho outbox, SQS, inbox e caso pode ser validado sem seed.
- A API atual permanece compatível para identificadores legados.
- Eventos de avaliação, pedido explícito de revisão, fatos tri-state e projeção local de risco continuam pendentes.
- A política provisória de severidade precisa ser substituída por taxonomia aprovada antes de produção.
- Dossiê e COAF passam para um marco posterior, quando houver histórico transacional realmente integrado.
