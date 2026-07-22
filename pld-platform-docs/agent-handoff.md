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

Esta sequência reflete os incrementos efetivamente executados. Ela substitui o roadmap nominal inicial quando houver divergência com os arquivos `marco-*-tasks.md` e ADRs posteriores.

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

### Marco 2 — caso transacional mínimo

1. Criar o aggregate `Case` e sua fila humana no backend de clientes.
2. Consumir sinais transacionais com inbox idempotente.
3. Abrir e consultar casos sem duplicidade.
4. Projetar origem, sinal e contexto mínimo para a fila.

Saída: sinal transacional recebido e transformado em caso humano consultável.

### Marco 3 — colaboração e decisão humana

1. Registrar comentários e anotações no caso com timeline.
2. Implementar decisões mínimas de suspeição e relacionamento.
3. Publicar os eventos v1 dessas decisões.
4. Exigir revisão humana secundária para decisões sensíveis.

Saída: colaboração e decisões humanas mínimas, auditáveis e separadas por finalidade.

### Marco 4 — primeira versão do React

1. Implementar shell, autenticação, autorização, fila única e pesquisa.
2. Implementar visão 360, tela de análise e timeline.
3. Tratar carregamento, ausência real de resultado, parcialidade, indisponibilidade e conflito como estados visuais diferentes.
4. Fazer o frontend consumir somente APIs do BFF, exceto integrações browser-to-provider aprovadas como Google Maps/Street View.

Saída: fluxo completo de um caso transacional até decisão, com trilha de auditoria.

### Marco 5 — evidências simuladas e prontidão decisória

1. Simular execução de fontes preservando proveniência, qualidade e tentativas.
2. Exibir matriz de evidências e prontidão decisória no Workbench.
3. Implementar retry e conclusão explícita do ciclo.
4. Validar decisões e segunda aprovação sobre evidências persistidas.

Saída: fluxo humano auditável com estados de evidência e prontidão explícitos.

### Marco 6 — integração transacional real

1. Persistir screening, decisão e outbox na mesma transação no motor transacional.
2. Publicar `TransactionSignalDetected.v1` via SQS com entrega at-least-once.
3. Consumir com inbox idempotente e abrir caso sem duplicidade.
4. Preservar o workflow de `Alert` legado durante a convivência.

Saída: caminho real `DecisionExecution → outbox → SQS → inbox → caso` validado ponta a ponta.

### Marco 7 — avaliação transacional reproduzível

1. Introduzir o aggregate imutável `TransactionEvaluation`, snapshot canônico e facts tri-state.
2. Congelar ruleset e explicação usados em cada avaliação.
3. Publicar `TransactionEvaluationCompleted.v2`, sinais v1 e `ManualReviewRequested.v2` via outbox.
4. Projetar pedidos e sinais tolerando duplicidade e reorder antes de trocar o gatilho do caso.

Saída: avaliação histórica consultável sem reexecutar regras atuais e cutover humano protegido por dual-run.

### Marco 8 — projeção local de risco, cutover v2 e administração de regras

1. Consumir `CustomerRiskProfileUpdated.v1` (publisher mock) e manter projeção local no motor.
2. Cutover SHADOW → MANUAL_REVIEW_LIVE — v2 como único gatilho de caso.
3. Entregar tela de administração de regras no Workbench (catálogo, dry-run, ativação).
4. Pesquisa global básica no shell.

Saída: motor transacional desacoplado do REST de risco, Alert legado desativado, analista autônomo para configurar regras.

### Marco 9 — adapters de evidência simulados e visão 360

1. Implementar 4 adapters mock (bureau, sanções, processos, mídia) com latência/falha realista.
2. Policy de evidência por risco (não mais cenários hardcoded).
3. Visão 360 expandida (risco, segmentos, contas, endereço).
4. Telas de mídia/processos e nomes/aliases.

Saída: evidências com proveniência real (dados simulados), prontidão decisória baseada em política, visão 360 funcional.

### Marco 10 — dossiê interno e comunicação COAF simulada

1. Geração de dossiê a partir do caso (assíncrona, versionada, com manifesto e gaps).
2. Workflow COAF completo: draft → aprovação → envio (adapter mock) → protocolo/rejeição.
3. Telas de dossiê e área COAF protegida por permissão.

Saída: ciclo regulatório completo simulado — do sinal à comunicação ao regulador.

### Marco 11 — onboarding simulado, revalidação e relações

1. Publisher mock de eventos mestre (onboarding, data change, relationships).
2. Consumer cria parties e ciclos automaticamente.
3. Revalidação periódica e event-driven com coalescência.
4. Telas de relações PF/PJ, revalidação/diff entre ciclos, shell expandido.
5. Maker-checker para ativação de regras.

Saída: protótipo cobre todos os fluxos documentados de ponta a ponta com simulação.

### Pós Marco 11 — produção

Substituir simulações por integrações reais, conforme decisões externas (EXT-2 a EXT-8). Cada integração real é um incremento independente que substitui um adapter mock pela implementação final.

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
