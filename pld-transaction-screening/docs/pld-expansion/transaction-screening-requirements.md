# `pld-transaction-screening` — requisitos

Repositório de partida: `anderson-bastos/keyword-screening`.  
Estratégia: evolução incremental no mesmo repositório, preservando compatibilidade até a migração dos consumidores.

## Missão

Avaliar transações em alta vazão contra regras versionadas e contexto local, registrar uma explicação histórica completa, executar ações técnicas autorizadas e publicar sinais para investigação. O serviço não administra o trabalho humano nem a ficha completa do cliente.

## Escopo

Inclui:

- ingestão idempotente de eventos de transação;
- screening de termos e classificação contextual;
- construção do contexto e resolução de fatos locais;
- projeção local dos fatos de risco de cliente necessários às regras;
- catálogo, configuração, teste, aprovação, vigência e rollback de regras;
- avaliação determinística e explicável;
- dry-run, backtest e comparação de impacto;
- execução controlada de ações transacionais quando autorizadas;
- publicação de avaliações, sinais e pedidos de revisão;
- consulta histórica da execução.

Não inclui:

- cadastro mestre ou enriquecimento completo de PF/PJ;
- busca de mídia, processos, mandados, endereço ou Street View;
- fila, atribuição e decisão humana;
- decisão final de relacionamento/conta;
- dossiê consolidado e comunicação ao COAF;
- UI própria para o analista operacional no estado alvo.

## Requisitos funcionais

### TS-FR-001 — ingestão de transação

O serviço deve aceitar `TransactionOccurred.v1` por mensageria e, durante a convivência, manter os endpoints atuais necessários.

Critérios:

- validar schema sem logar payload sensível;
- persistir referência/snapshot mínimo da entrada antes ou junto da avaliação;
- usar `eventId` para deduplicar entrega;
- permitir reavaliação intencional sob nova regra por `evaluationPurpose`, sem confundi-la com duplicidade;
- rejeitar/quarentenar mensagem inválida com reason code e correlação.

### TS-FR-002 — identidade da avaliação

Cada execução possui `evaluationId` estável. Avaliações `LIVE` usam a chave natural `(transactionId, transactionVersion, rulesetVersion, purpose)`; execuções não-LIVE usam `(evaluationRequestId, purpose)`, de modo que novo request ID cria reexecução intencional e retry preserva a mesma avaliação.

Finalidades mínimas:

```text
LIVE
REPLAY
BACKTEST
DRY_RUN
INVESTIGATION
```

Somente `LIVE` pode produzir efeito operacional por padrão. Outros modos persistem resultado em escopo próprio e nunca abrem caso ou executam ação sem opt-in explícito.

### TS-FR-003 — snapshot explicável

Para cada execução o serviço deve registrar:

- transação e versão consideradas;
- instante de negócio e de processamento;
- versão do catálogo/configuração/política;
- versão da projeção de risco;
- fatos resolvidos, qualidade, fonte e referência temporal;
- fatos requeridos mas ausentes, stale ou com erro;
- regras candidatas, avaliadas e acionadas;
- resultados intermediários relevantes;
- decisão técnica, action, explicação e duração;
- correlação, causação e origem.

Consultar uma execução histórica não deve reexecutar a regra atual para produzir a explicação.

### TS-FR-004 — resolução de fatos com qualidade

`FactValue` deve representar `PRESENT`, `UNKNOWN`, `STALE` e `ERROR`.

- Comparação com valor que não esteja `PRESENT` não retorna implicitamente `false`.
- A expressão produz um resultado tri-state: `TRUE`, `FALSE` ou `INDETERMINATE`.
- A política da regra define como tratar `INDETERMINATE`: ignorar justificadamente, pedir retry, derivar ou falhar fechado quando legal e tecnicamente autorizado.
- Toda decisão guarda a lista de fatos indeterminados.

### TS-FR-005 — projeção local de risco

O serviço deve consumir `CustomerRiskProfileUpdated.v1` e manter uma projeção por `partyId`/`accountId`.

Critérios:

- aplicar versões em ordem e ignorar duplicatas;
- não regredir para uma versão mais antiga;
- escolher o perfil efetivo no instante da transação;
- marcar perfil inexistente ou expirado como qualidade explícita;
- expor lag, última versão e falhas como métricas;
- não chamar `pld-customer-analysis` sincronamente durante a avaliação.

### TS-FR-006 — screening de termos

O screening existente deve:

- normalizar texto com versão do algoritmo;
- registrar termos/regras e versões acionadas;
- preservar posição/trecho mínimo permitido para explicação;
- diferenciar match exato, normalizado, aproximado e contextual;
- permitir categorias e action/routing por configuração;
- evitar que um match de palavra isolado seja apresentado como conclusão de suspeição.

### TS-FR-007 — classificação contextual/IA

Quando houver classificador LLM/estatístico:

- a chamada é adaptador opcional, com timeout, retry seguro e fallback;
- entrada externa é tratada como dado não confiável;
- saída aceita apenas schema fechado e é normalizada/validada;
- modelo, versão, template, parâmetros, confiança e referência da resposta são auditados;
- baixa confiança, resposta inválida ou indisponibilidade produz rota explícita;
- o classificador não decide por conta própria rejeição de conta ou comunicação ao COAF.

### TS-FR-008 — catálogo e configuração de regras

Separar:

- `RuleDefinition`: identidade e semântica aprovada da regra;
- `RuleConfiguration`: parâmetros/versionamento por contexto;
- `RuleSet`: conjunto efetivo usado em uma avaliação;
- `Activation`: aprovação, janela de vigência e rollout.

Estados mínimos:

```text
DRAFT → IN_REVIEW → APPROVED → SCHEDULED → ACTIVE → RETIRED
```

Uma versão rejeitada retorna a `DRAFT`; uma versão ativa é imutável. Alteração cria nova versão.

### TS-FR-009 — maker-checker e vigência

- Autor e aprovador devem ser atores distintos quando a política exigir.
- Ativação guarda aprovadores, justificativa, ticket/referência e comparação de impacto.
- Deve haver `effectiveFrom` e `effectiveUntil` opcionais.
- Somente uma configuração incompatível pode estar efetiva por escopo no mesmo instante.
- Rollback ativa uma versão anterior por nova decisão auditada; não apaga histórico.

### TS-FR-010 — dry-run e backtest

- Dry-run avalia amostra/entrada sem efeito operacional.
- Backtest executa dataset histórico como job assíncrono, com versão congelada e progresso.
- Comparação mostra volume de matches, mudanças de rota, distribuição por segmento e diferenças contra a versão vigente.
- Dados de teste respeitam minimização e acesso.
- Cancelamento não deixa versão parcialmente marcada como aprovada.

### TS-FR-011 — decisão e execução técnica

Separar `DecisionResult` de `DecisionExecution`.

- Resultado diz o que a regra concluiu/recomendou.
- Execução registra solicitação ao sistema que aplica ação e seu resultado real.
- Estados mínimos de execução: `REQUESTED`, `APPLIED`, `FAILED`, `REVERSED`, `NOT_APPLICABLE`.
- Reenvio usa `commandId`; timeout não é assumido como falha definitiva sem reconciliação.
- Ações de alto impacto precisam de política explícita e não devem nascer de classificador opaco isolado.

### TS-FR-012 — publicação de eventos

Usar outbox transacional para:

- `TransactionEvaluationCompleted.v2` para avaliações reproduzíveis; v1 permanece congelado durante a convivência;
- `TransactionSignalDetected.v1`;
- `ManualReviewRequested.v2` para novos pedidos; v1 permanece congelado durante a convivência;
- `TransactionDecisionExecutionCompleted.v1`;
- `RuleConfigurationActivated.v1`.

Eventos seguem `shared/integration-contracts.md` e não carregam PII desnecessária.

### TS-FR-013 — revisão humana externa

Quando uma nova avaliação reproduzível exigir humano, publicar `ManualReviewRequested.v2`. O estado alvo não cria nem atualiza um `Alert` humano local.

Durante a migração:

- o módulo `Alert` atual pode operar em shadow/compatibility mode;
- deve ser possível comparar o alerta legado com o caso criado no novo backend;
- um único modo `LEGACY`, `SHADOW` ou `MANUAL_REVIEW_LIVE` define qual gatilho pode criar/alterar caso; nunca há dois gatilhos ativos;
- feedback humano retorna por contrato explícito quando necessário à calibração, sem transferir ownership do caso.

### TS-FR-014 — consulta e auditoria

APIs devem permitir:

- buscar execução por `evaluationId` e `transactionId`;
- ver snapshot, regras, fatos, indeterminados e explicação;
- consultar configuração e vigência histórica;
- acompanhar jobs de dry-run/backtest;
- auditar ativação/rollback;
- filtrar sem expor payload sensível a papéis não autorizados.

### TS-FR-015 — replay e reconciliação

- Replay é comando administrativo autorizado com finalidade e intervalo explícitos.
- Modo padrão não repete efeitos externos.
- O serviço reconcilia outbox não publicada, projeção de risco e execuções com status incerto.
- Toda ação de replay registra solicitante, filtro, versão e contagens.

## Regras de domínio obrigatórias

1. Uma configuração já usada em `LIVE` não é editável.
2. A explicação aponta para os valores efetivamente usados, não para valores atuais.
3. `SUCCESS_NO_RESULTS`, `UNKNOWN` e `ERROR` nunca são equivalentes.
4. Reentrega de evento não cria outra avaliação live nem outro sinal.
5. Reavaliação autorizada cria execução distinta e ligada à anterior.
6. Um sinal não é decisão de suspeição.
7. Uma ação solicitada não é considerada aplicada sem confirmação.
8. A falta do perfil de risco não dispara consulta síncrona ao outro backend.

## Critérios de aceite do serviço alvo

- Uma transação pode ser processada com o backend de clientes temporariamente fora do ar, usando a última projeção válida ou rota explícita de ausência.
- É possível explicar uma avaliação antiga após regras e perfil terem mudado.
- A mesma mensagem entregue dez vezes causa um único efeito live.
- Um fato ausente produz comportamento configurável e visível, nunca `false` silencioso.
- A criação de caso ocorre no novo backend e pode ser correlacionada ao sinal original.
- A ativação de regra exige o fluxo aprovado e mostra impacto antes/depois.
- Replay/backtest não executa ação operacional por acidente.
