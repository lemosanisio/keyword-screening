# `pld-workbench` — arquitetura de informação e telas

## Navegação principal

```text
Fila
Pesquisa
Clientes e empresas
Dossiês
Comunicações COAF        (somente autorizado)
Administração            (somente autorizado)
  ├─ Políticas de cliente
  ├─ Regras transacionais
  ├─ Fontes e cobertura
  └─ Auditoria operacional
```

Onboarding, revisão e alerta transacional não aparecem como produtos separados. São origens/filtros da mesma fila e contexto da mesma parte.

## Rotas propostas

| Rota | Tela | Observação |
|---|---|---|
| `/queue` | Fila de trabalho | filtros no query string |
| `/search` | Pesquisa global | termos sensíveis não ficam na URL; usar token de busca/estado de navegação quando necessário |
| `/parties/:partyId` | Visão 360 | default `overview` |
| `/parties/:partyId/identity` | Identidade e relações | PF/PJ |
| `/parties/:partyId/evidence` | Evidências e fontes | matriz de cobertura |
| `/parties/:partyId/media-legal` | Mídia, processos e listas | findings e resolução |
| `/parties/:partyId/transactions` | Sinais transacionais | projeção local do BFF |
| `/parties/:partyId/analyses` | Ciclos e decisões | atual + histórico |
| `/parties/:partyId/timeline` | Timeline | deep links por activity ID |
| `/cases/:caseId` | Workspace do caso | tela operacional principal |
| `/decisions/:decisionId/review` | Aprovação | rota protegida |
| `/dossiers/:dossierId` | Dossiê | manifesto, versões e exportação |
| `/coaf/:communicationId` | Comunicação COAF | rota/telemetria protegidas |
| `/admin/customer-policies` | Política de análise | versões e vigência |
| `/admin/transaction-rules` | Regras transacionais | fachada para autoridade do motor |
| `/admin/sources` | Fontes | cobertura/saúde, sem segredo |
| `/admin/audit` | Auditoria | acesso controlado |

## Cabeçalho contextual da parte

Presente em visão 360 e caso:

- nome/razão social e tipo PF/PJ;
- identificadores mascarados;
- account status confirmado;
- risco atual, versão e `asOf`;
- ciclo/caso atual;
- badges de PEP/lista/país apenas quando autorizados e explicáveis;
- última análise e próxima revisão;
- link para casos relacionados;
- indicador de sincronização/freshness.

O cabeçalho evita que o analista tome decisão olhando para a pessoa errada. Em caso de homônimo, atributos de desambiguação ficam visíveis.

## Tela 1 — fila de trabalho

### Objetivo

Escolher o próximo trabalho e enxergar ownership sem abrir cada item.

### Regiões

1. Resumo: abertos, não atribuídos, meus casos, pendentes de aprovação e com prazo regulatório autorizado.
2. Filtros salvos: status, origem, tipo de Party, motivo, prioridade, responsável, período e risco.
3. Tabela/virtual list.
4. Preview lateral opcional com resumo e casos relacionados.

### Colunas padrão

```text
Prioridade explicada
Parte
PF/PJ
Origem
Motivo principal
Status
Responsável
Idade / prazo
Última atualização
```

### Ações

Assumir, atribuir autorizado, abrir, adicionar a filtro salvo. Ação em lote não inclui decisão.

## Tela 2 — pesquisa global

### Objetivo

Localizar trabalho existente antes de criar outro e navegar por múltiplas chaves.

### Resultado

Cards agrupados de parte, conta, caso, processo e dossiê. Cada item mostra por que houve match (`CPF exato`, `alias aproximado`, `caseId`, etc.) e sinais de homônimo. Comunicação COAF não aparece para usuários sem capability.

## Tela 3 — visão 360

### Aba Resumo

- cards de identidade, conta, risco e revisão;
- findings prioritários com fonte/freshness;
- relações principais;
- análises/casos recentes;
- transações/sinais relevantes;
- ações rápidas permitidas.

### Aba Identidade e relações

- snapshot atual/histórico;
- identificadores e aliases;
- lista/grafo de relações;
- beneficiários finais;
- endereços, mapa e Street View;
- comparação de nomes e distâncias.

### Aba Evidências

- matriz requisito × fonte;
- fatos normalizados;
- execuções e tentativas;
- validade/expiração;
- conflitos;
- acesso ao documento/referência.

### Aba Mídia e jurídico

- clusters de notícia;
- findings de mídia;
- processos e movimentos relevantes;
- sanções, listas, mandados e PEP;
- resolução de match/identidade.

### Aba Transações

- sinais por período/tipo/severidade;
- resumo de padrão;
- avaliação/regra/fatos;
- ações técnicas confirmadas;
- vínculo com casos.

### Aba Análises

- ciclos em ordem;
- diff entre ciclos;
- assessments, deriva e decisões;
- conta versus suspeição;
- dossiês/comunicações permitidos.

### Aba Timeline

Timeline regulatória completa, filtrável e deep-linkable.

## Tela 4 — workspace do caso

Esta é a tela principal de trabalho. Em desktop, usar três regiões redimensionáveis sem exigir que todas fiquem abertas:

| Região | Conteúdo |
|---|---|
| Contexto | parte, ciclo, motivos, risco, casos relacionados e checklist |
| Investigação | abas de evidência, mídia/jurídico, transações, relações e timeline |
| Ação | tarefas, notas e painel de decisão, recolhível |

### Barra superior

- `caseId`, status, prioridade e origem;
- responsável/watchers;
- versão/última atualização;
- ações assumir, atribuir, aguardar, concluir conforme capability;
- alerta de edição concorrente/sincronização.

### Motivos e pendências

Mostrar reason codes de deriva/findings, requisitos faltantes e ações resolutivas. “Fonte indisponível” deve levar a retry/status técnico; “sem resultados” apresenta consulta válida.

### Checklist

Itens podem vir da política ou ser criados pelo analista. Cada item registra conclusão/dispensa e ator. Completar checklist não emite decisão automaticamente.

### Notas e anexos

Rascunho separado de comentário publicado. Anexo passa por validação e mostra classificação/retenção. Não permitir colar segredo/token.

## Painel de decisão

### Etapa 1 — relacionamento

Selecionar decisão contextual (onboarding ou ongoing), reason codes, condições e ação esperada.

### Etapa 2 — suspeição

Selecionar decisão independente e, se `KEEP_MONITORING`, definir condição/tempo. `COMMUNICATE_TO_COAF` informa que criará draft restrito, sem expor conteúdo a usuário não autorizado.

### Etapa 3 — fundamentação

Selecionar findings/evidências consideradas e escrever narrativa. A UI sinaliza evidência expirada/conflitante e requisitos obrigatórios.

### Etapa 4 — impacto e aprovação

Preview vindo do backend mostra:

- transições propostas;
- comando de conta que será solicitado;
- necessidade de segundo aprovador;
- próxima revisão;
- criação de comunicação COAF quando autorizada.

### Etapa 5 — resultado

Após submissão, apresentar status separados:

```text
Decisão registrada
Aprovação pendente/concluída
Ação de conta solicitada/aplicada/falhou
Comunicação criada (somente autorizado)
Caso encerrado ou pendente
```

## Tela 5 — aprovação

Layout de comparação:

- proposta e versão;
- autor e horário;
- fatos/evidências selecionados;
- política e requisitos;
- diff de cadastro/evidência/decisão após proposta;
- impacto;
- aprovar ou devolver para correção.

Se algo material mudou, backend invalida ou exige confirmação/reavaliação; frontend não permite “aprovar mesmo assim” sem contrato explícito.

## Tela 6 — dossiê

### Cabeçalho

Versão, `asOf`, ciclo/caso, status, gerador, hash e classificação.

### Índice

1. identificação e relações;
2. motivação/escopo;
3. evidências e fontes;
4. fatos/findings;
5. análise transacional;
6. decisões/ações;
7. timeline;
8. comunicação/recibo quando permitido;
9. manifesto técnico de integridade.

Antes de `READY`, mostrar lacunas/erros. Depois, conteúdo é somente leitura; correção gera nova versão.

## Tela 7 — comunicação COAF

Área com shell visual distinto e classificação de sigilo.

Seções:

- vínculo à decisão/dossiê;
- dados estruturados;
- operações/pessoas relacionadas;
- narrativa;
- validações;
- revisão/aprovação;
- prazo;
- tentativas de envio e recibo;
- retificações.

Não incluir conteúdo em breadcrumbs, analytics, notificações push ou mensagens de erro externas. Voltar à tela anterior não deve deixar preview sensível em cache compartilhado.

## Tela 8 — administração de regras/políticas

### Lista

Código, versão, domínio, status, vigência, autor, aprovador e impacto da última simulação.

### Editor de draft

- formulário/schema, não JSON livre por padrão;
- validação e exemplos;
- diff contra versão ativa;
- razão da mudança/ticket.

### Impacto

- job/progresso;
- dataset/janela e versão;
- comparação de matches, rotas e segmentos;
- casos amostrais autorizados;
- limitações do teste.

### Aprovação/ativação

Ações específicas, nunca um único botão “salvar e ativar”. Mostrar vigência e rollback.

## Componentes compartilhados

| Componente | Propósito |
|---|---|
| `PartyHeader` | identidade contextual e freshness |
| `SourceStatusBadge` | estados de fonte com cor + ícone + texto |
| `EvidenceReference` | proveniência, validade e abertura autorizada |
| `PolicyRequirementMatrix` | cobertura e lacunas |
| `FindingCard` | sinal, confiança e vínculos |
| `NameMatchComparison` | strings/atributos/score explicados |
| `DecisionExplanation` | regra/política, fatos e versões |
| `RegulatoryTimeline` | eventos com filtros e links |
| `RelationshipView` | tabela/grafo acessível |
| `AsyncJobStatus` | progresso, falha, retry/cancelamento |
| `SensitiveActionDialog` | consequência, reason e confirmação |
| `SyncStateIndicator` | `asOf`, lag e atualização |

## Modelos de leitura esperados do BFF

Evitar dezenas de chamadas por tela. Exemplos:

```text
WorkQueuePage
  items[] + facets + pageInfo + asOf

PartyOverviewView
  header + risk + relationshipsSummary + findingSummary
  currentCycles + recentCases + transactionSummary + freshness

CaseWorkspaceView
  case + partyHeader + cycle + derivationReasons
  requirements + findings + transactionSignals
  relatedCases + tasks + comments + availableActions + version

DecisionPreview
  validationErrors + warnings + requiredApproval
  proposedTransitions + accountAction + reviewSchedule

DossierView
  metadata + sections + manifest + availableActions
```

Sub-recursos pesados continuam paginados. O BFF retorna `availableActions` derivadas de autorização/estado para evitar duplicar lógica de habilitação, mas valida novamente cada comando.

## Estado no cliente

- Server state em cache com chaves por ID/versão e invalidação por comando/evento/polling aprovado.
- Estado de navegação em URL quando não sensível.
- Rascunhos sensíveis preferencialmente salvos no backend; se local, memória/session storage criptograficamente apropriada conforme padrão e descarte ao logout.
- Nunca persistir dossiê, narrativa COAF ou payload de evidência em localStorage.
- Formulários guardam `objectVersion` e tratam conflito.

## Ordem de construção das telas

1. Shell + autenticação + capabilities.
2. Fila + pesquisa.
3. Party header + visão 360 mínima.
4. Workspace de caso + timeline.
5. Transações e explicação.
6. Decisão + aprovação + confirmação de ação.
7. Matriz de evidências e fontes.
8. Identidade/relações/mídia/jurídico/Maps.
9. Revalidação e diff de ciclos.
10. Dossiê.
11. COAF.
12. Administração de políticas/regras.

Cada incremento deve formar uma fatia vertical real; evitar implementar todas as telas com mocks antes dos fluxos de comando/auditoria.

