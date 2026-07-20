# Handoff para o agente local

Este documento transforma a arquitetura em uma sequência executável sem misturar os três produtos.

## Layout de trabalho

Um único repositório Git (monorepo) contendo as três aplicações e este pacote — ver [ADR-004](adr/ADR-004-monorepo-layout.md), que substitui a versão original desta seção:

```text
keyword-screening/                # monorepo da plataforma PLD
├── pld-transaction-screening/    # backend transacional (serviço existente)
├── pld-customer-analysis/        # novo backend
├── pld-workbench/                # novo frontend React (criado no Marco 4)
└── pld-platform-docs/            # este pacote, fonte dos contratos compartilhados
```

Monorepo não é monólito: as fronteiras de ADR-001 permanecem (deploy, banco e ownership independentes por aplicação; nenhum serviço lê tabelas do outro). Cada aplicação mantém build próprio — não introduzir build raiz compartilhado sem necessidade real.

## Onde colocar cada documentação

| Origem neste pacote | Destino |
|---|---|
| `transaction-screening/*.md` | `pld-transaction-screening/docs/pld-expansion/` |
| `customer-analysis/*.md` | `pld-customer-analysis/docs/` |
| `frontend/*.md` | manter em `pld-platform-docs/frontend/` até o Marco 4; depois `pld-workbench/docs/` |
| `shared/*.md`, `adr/*.md`, `README.md`, `agent-handoff.md` | manter em `pld-platform-docs/` na raiz do monorepo; cada aplicação registra a versão dos contratos que consome |

Não manter cópias manuais divergentes dos contratos compartilhados: a fonte é `pld-platform-docs/` no monorepo. Registrar em cada aplicação um arquivo pequeno com a versão consumida.

## Decisões que já podem ser tratadas como fechadas

- O produto é uma interface única, mas possui dois backends especializados.
- A fila humana e todas as decisões de analista ficam em `pld-customer-analysis`.
- `keyword-screening` é o motor transacional e não consulta o backend de clientes a cada transação.
- `pld-customer-analysis` funciona como BFF do frontend para as telas integradas.
- PF e PJ compartilham o mesmo fluxo e a abstração `Party`.
- Deriva é insuficiência de evidência sob uma política, não uma classificação de suspeição.
- Decisão de conta/relacionamento e decisão sobre comunicação ao COAF são independentes.
- Persistência, deploy e ownership de código são independentes por backend.
- Eventos publicados são at-least-once; consumidores têm de ser idempotentes.
- Dossiê interno e comunicação ao COAF são artefatos relacionados, mas distintos.

## Decisões técnicas locais que não bloqueiam o desenho

O agente pode escolher versões suportadas e padrões internos do time para:

- nome definitivo dos novos repositórios;
- broker e schema registry;
- provedor de identidade e mapeamento de grupos;
- biblioteca de componentes React;
- armazenamento de documentos/evidências;
- ferramenta de feature flag;
- ferramenta de observabilidade.

Registrar cada escolha material em ADR. Não introduzir acoplamento de domínio para compensar uma escolha de infraestrutura.

## Sequência de trabalho

### Marco 0 — contrato e segurança de mudança

1. Adicionar estes documentos ao workspace.
2. Criar catálogo versionado de schemas dos eventos `v1` descritos em `shared/integration-contracts.md`.
3. Definir convenções de IDs, UTC, classificação de dados e correlação.
4. Criar testes de contrato publicador/consumidor antes de ligar os serviços.
5. Mapear as APIs atuais do `keyword-screening` que precisam permanecer compatíveis durante a migração.

Saída: contratos testáveis, sem alteração de comportamento em produção.

### Marco 1 — esqueleto do novo backend

1. Criar `pld-customer-analysis` com Kotlin/JVM e Spring Boot, seguindo os padrões aprovados no time.
2. Criar módulos de `party`, `analysis`, `case-management`, `timeline`, `integration` e `identity-access`.
3. Implementar outbox, inbox, auditoria de domínio e migrations desde o primeiro incremento.
4. Expor uma API mínima para criar/consultar `Party`, abrir `AnalysisCycle` e ler timeline.
5. Publicar OpenAPI e health/metrics/traces.

Saída: fatia vertical autenticada, auditável e implantável.

### Marco 2 — desacoplamento do motor transacional

1. Introduzir `evaluationId` e envelope comum.
2. Persistir snapshot de entrada, versão de regra/política, fatos usados e fatos ausentes.
3. Introduzir valor de fato com `PRESENT`, `UNKNOWN`, `STALE` e `ERROR`.
4. Criar projeção local de risco atualizada por `CustomerRiskProfileUpdated.v1`.
5. Publicar `TransactionEvaluationCompleted.v1`, `TransactionSignalDetected.v1` e `ManualReviewRequested.v1` via outbox.
6. Manter o workflow de `Alert` antigo temporariamente, marcado como legado.

Saída: transações avaliadas sem dependência síncrona do novo backend.

### Marco 3 — caso transacional no backend de clientes

1. Consumir eventos transacionais com inbox.
2. Agrupar sinais conforme política e abrir/atualizar caso sem duplicidade.
3. Projetar transações, regras acionadas e explicações na visão do cliente.
4. Implementar atribuição, comentário, pedido de informação e decisões humanas.
5. Emitir `CaseStatusChanged.v1` e, quando aplicável, comandos/feedback explicitamente contratados.

Saída: o analista já não precisa operar a fila humana no motor.

### Marco 4 — primeira versão do React

1. Implementar shell, autenticação, autorização, fila única e pesquisa.
2. Implementar visão 360, tela de análise e timeline.
3. Tratar carregamento, ausência real de resultado, parcialidade, indisponibilidade e conflito como estados visuais diferentes.
4. Fazer o frontend consumir somente APIs do BFF, exceto integrações browser-to-provider aprovadas como Google Maps/Street View.

Saída: fluxo completo de um caso transacional até decisão, com trilha de auditoria.

### Marco 5 — onboarding, evidências e revalidação

1. Ingerir dados cadastrais e relações PF/PJ.
2. Conectar fontes por adaptadores, preservando proveniência e status de execução.
3. Implementar assessment, deriva, decisão de relacionamento e agendamento de revalidação.
4. Integrar Street View apenas como observação de analista e conforme políticas do Google.

Saída: análise ponta a ponta de onboarding e revisão contínua.

### Marco 6 — dossiê e COAF

1. Gerar snapshot versionado do dossiê a partir de fatos e decisões já persistidos.
2. Criar comunicação COAF separada, com revisão, aprovação, envio por porta e comprovante.
3. Aplicar controles de acesso, segredo e não comunicação ao cliente.
4. Testar retificação, falha de envio, reprocessamento e reconstrução histórica.

Saída: processo regulatório rastreável de ponta a ponta.

### Marco 7 — retirada do legado

1. Comparar por período os casos gerados nos dois caminhos.
2. Migrar links/bookmarks e permissões do backoffice anterior.
3. Bloquear novas decisões humanas no módulo `Alert` legado.
4. Remover consumidores/APIs apenas após confirmar que não há dependências.

Saída: uma fila humana e nenhuma duplicidade de trabalho.

## Forma recomendada dos pull requests

- Um PR de fundação por aplicação.
- PRs verticais pequenos, cada um com migration, domínio, API/evento, observabilidade e testes.
- Mudanças de schema compartilhado em PR próprio, aprovadas pelos donos dos dois backends.
- Não combinar renomeação ampla do código existente com mudança de semântica no mesmo PR.

## Definition of Done comum

Uma capacidade só está pronta quando:

- possui critérios de aceitação automatizados;
- registra ator, instante, motivo, versão de política e IDs de evidência quando houver decisão;
- propaga `correlationId` e `causationId`;
- não perde evento entre commit de negócio e publicação;
- suporta repetição do mesmo evento/comando sem efeito duplicado;
- diferencia resultado vazio de fonte indisponível;
- aplica autorização no backend;
- mascara PII em logs e métricas;
- documenta contrato e migration/rollback;
- aparece na timeline regulatória quando altera estado relevante;
- tem runbook para falha operacional relevante.

## Antiobjetivos

- Não criar um “serviço agregador” que apenas repasse chamadas síncronas a várias fontes.
- Não permitir que o frontend componha decisões consultando diretamente múltiplos backends.
- Não replicar toda a ficha cadastral nos eventos transacionais.
- Não permitir edição retroativa de evidência ou decisão; corrigir com nova versão/retificação.
- Não tratar resposta vazia, timeout e erro como `false`.
- Não usar LLM como fonte de verdade ou decisor autônomo.
- Não condicionar a arquitetura a definições de mesa ou SLA que não existem no processo atual.
