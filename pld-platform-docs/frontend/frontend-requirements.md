# `pld-workbench` — requisitos do frontend React

## Missão

Oferecer uma única superfície de trabalho para que analistas entendam o cliente, investiguem sinais de qualquer origem, tomem decisões, comprovem o raciocínio e produzam artefatos regulatórios sem alternar entre sistemas ou repetir análise.

## Stack e fronteira

- React + TypeScript em modo estrito.
- Escolher framework/build tool conforme padrão corporativo; React é o requisito, Next/Vite não está decidido aqui.
- Consumir APIs do `pld-customer-analysis`/BFF para o fluxo operacional.
- Administração transacional pode ser roteada pelo gateway/BFF, mas regras continuam pertencendo ao motor.
- Não acessar banco, broker ou APIs internas diretamente.
- Google Maps/Street View pode ser carregado no browser pela API oficial aprovada.
- Regra, autorização e transição de estado são validadas no backend; UI apenas antecipa feedback.

## Princípios de experiência

1. **Uma fila, várias origens.** Onboarding, revisão e transação são filtros/contextos, não mesas separadas.
2. **Contexto antes da ação.** Cabeçalho persistente identifica parte, conta, risco, ciclo/caso e freshness.
3. **Estado desconhecido é visível.** Ausência de resultado, parcialidade, conflito, erro e expiração têm apresentações diferentes.
4. **Explicação progressiva.** Mostrar conclusão/resumo primeiro e permitir chegar a fato, evidência e execução original.
5. **Trabalho anterior é encontrável.** Casos/ciclos correlatos aparecem antes de criar nota ou investigação duplicada.
6. **Decisões separadas.** Conta/relacionamento e suspeição/COAF ficam em campos e etapas distintas.
7. **Ação de alto impacto é deliberada.** Consequência, justificativa e necessidade de aprovação são explícitas.
8. **História não muda silenciosamente.** Correção cria nova versão/retificação; a UI mostra diferenças.

## Requisitos funcionais

### FE-FR-001 — autenticação, sessão e autorização

- Redirecionar para SSO corporativo e encerrar sessão conforme política.
- Obter permissões efetivas do backend e proteger rotas/ações.
- Exibir estado de sessão expirada sem perder rascunho local permitido.
- Não inferir permissão por papel hardcoded; usar capabilities retornadas.
- Nunca guardar token em armazenamento inseguro quando houver alternativa corporativa.

### FE-FR-002 — shell global

O shell contém:

- pesquisa global;
- acesso à fila;
- atalhos recentes permitidos;
- indicador de pendências do usuário;
- navegação para dossiês/COAF/administração segundo permissão;
- ajuda contextual e versão do produto;
- aviso de ambiente fora de produção.

Não exibir conteúdo sensível em notificações genéricas ou título da página.

### FE-FR-003 — fila única

A fila deve oferecer:

- contadores por status/origem/motivo de deriva;
- busca e filtros combináveis;
- ordenação por prioridade, idade, prazo e última atualização;
- colunas configuráveis e preferências do usuário;
- badges de onboarding, periodic review, event-driven e transaction alert;
- responsável, status, pendência, risco e tempo desde evento;
- indicação de caso potencialmente duplicado/relacionado;
- atualização incremental sem saltar linha enquanto o usuário interage;
- assumir/atribuir em lote somente onde a política permitir.

A prioridade deve ter tooltip/explicação; não ser um score misterioso.

### FE-FR-004 — pesquisa global

Pesquisar por identificador permitido, nome/alias, conta, caso, processo e referências. Resultados:

- agrupados por tipo;
- mascarados conforme permissão;
- com motivo do match e destaque seguro;
- sem revelar que existe comunicação COAF a usuários sem acesso;
- com aviso para fuzzy match e possíveis homônimos.

### FE-FR-005 — visão 360 da parte

Exibir:

- identidade PF/PJ e snapshot/data de referência;
- contas/status confirmados;
- risco atual e evolução;
- relações e beneficiários;
- endereços/mapa;
- evidências, mídia, processos, listas e aliases;
- transações/sinais relevantes;
- ciclos/casos/decisões;
- timeline.

Cada card mostra fonte, data/freshness e estado. Deve ser possível alternar entre “atual” e uma data/ciclo histórico.

### FE-FR-006 — relações PF/PJ

- Grafo ou lista navegável de relações com tipo, participação, vigência e fonte.
- Evitar visual congestionado; lista/tabela é fallback acessível.
- Calcular/mostrar distância de endereço somente quando o backend enviar valor, método e contexto.
- Navegar para uma parte relacionada sem perder retorno ao caso.

### FE-FR-007 — matriz de evidências

Para cada requisito de política mostrar:

- obrigatório/opcional;
- fonte(s) planejadas e executadas;
- status exato da execução;
- fatos/resultados e validade;
- conflitos e evidências vinculadas;
- ação disponível: visualizar, retentar, pedir informação, dispensar autorizadamente.

Legenda visual e textual deve distinguir:

```text
com dados | sem resultados | parcial | conflito | indisponível | erro | expirada
```

Não depender apenas de cor.

### FE-FR-008 — mídia e processos

Mídia:

- agrupar duplicatas;
- mostrar veículo, data, entidade resolvida, resumo, alegações/estágio, relevância/confiança e fonte;
- destacar texto gerado por IA como extração auxiliar;
- permitir confirmar, rejeitar vínculo ou marcar inconclusivo com motivo.

Processos:

- mostrar número, tribunal, classe, assuntos, movimentos e papel atribuído;
- distinguir menção, processo, decisão, condenação, absolvição/arquivamento e desconhecido;
- mostrar evidência usada para associar o processo à parte.

### FE-FR-009 — nomes e aliases

- Comparar lado a lado valor cadastral e candidato.
- Exibir normalização, score, algoritmo/versão e atributos de desambiguação.
- Identificar origem: nome social, nome anterior, apelido, transliteração ou card descriptor.
- Permitir decisão humana de match/no-match/inconclusivo com justificativa.
- Não usar linguagem visual que apresente score como certeza.

### FE-FR-010 — Maps e Street View

- Incorporar mapa e `StreetViewPanorama` no painel de endereço usando SDK oficial e chave restrita.
- Preservar controles/atribuições exigidos.
- Não capturar, fazer upload ou enviar imagem ao backend.
- Exibir data/cobertura informada pelo provider e aviso de limitação.
- Registrar apenas uma observação voluntária do analista com campos estruturados; o backend recebe `panoId`, endereço/reference e texto, não imagem.
- Falha/ausência de panorama não vira finding negativo.

### FE-FR-011 — workspace do caso

O workspace deve reunir sem navegação destrutiva:

- resumo da parte e contexto do ciclo;
- motivos de abertura/deriva e requisitos pendentes;
- findings, evidências e transações;
- checklist/tarefas;
- notas, comentários e anexos;
- histórico e casos relacionados;
- painel de decisão.

A URL é deep-linkable. Abas/filtros relevantes ficam no query string para retorno e compartilhamento autorizado.

### FE-FR-012 — colaboração

- assumir, atribuir, adicionar watcher, comentar e mencionar conforme política;
- mostrar quem está editando/versão atual quando possível;
- detectar `409/412` e oferecer recarregar/comparar, nunca sobrescrever;
- autosave apenas de rascunho identificado como não submetido;
- comentários publicados são imutáveis; correção é novo comentário/referência.

### FE-FR-013 — decisão

Formulário possui seções independentes:

1. decisão de relacionamento/conta;
2. decisão de suspeição;
3. justificativa e evidências selecionadas;
4. condições/tarefas/revisão;
5. impacto e aprovação exigida.

Antes de enviar:

- validar requisitos e campos no cliente para conveniência;
- pedir ao backend preview/validation quando disponível;
- mostrar ação operacional esperada e deixar claro que aplicação será confirmada depois;
- exigir confirmação com texto específico para rejeitar, suspender ou encerrar, conforme UX aprovada;
- enviar `Idempotency-Key` e versão do caso.

Após envio, mostrar `DECISION_ISSUED`, `ACTION_REQUESTED` e `ACTION_APPLIED/FAILED` separadamente.

### FE-FR-014 — aprovação

- Tela/drawer mostra proposta, autor, política, evidências e diff desde a proposta.
- Aprovador pode aprovar ou rejeitar para correção com motivo.
- UI impede autoaprovação conhecida, mas backend é autoridade.
- Atualização concorrente força nova revisão.

### FE-FR-015 — transações e explicação de regras

- Lista paginada de sinais/transações relevantes, não extrato ilimitado.
- Mostrar regra/código/versão, fatos usados, indeterminados e explicação da execução original.
- Permitir navegar de sinal a avaliação e caso.
- Não recalcular explicação no browser.
- Diferenciar resultado da regra, sinal publicado, ação solicitada e ação aplicada.

### FE-FR-016 — timeline

- Filtrar por categoria, período e ator.
- Mostrar instante do fato e do registro quando diferirem.
- Expandir item para IDs/versões/evidências autorizados.
- Agrupar ruído técnico sem ocultar transição regulatória.
- Permitir copiar link interno sem colocar PII na URL.

### FE-FR-017 — revalidação

- Exibir última/próxima revisão, motivo, política e evidências a expirar.
- Comparar ciclo atual e anterior em diff semântico.
- Mostrar gatilhos coalescidos.
- Permitir iniciar revisão manual com motivo, evitando duplicata.

### FE-FR-018 — dossiê

- Solicitar geração assíncrona e acompanhar status.
- Visualizar índice/manifesto e lacunas antes de fechar.
- Comparar versões regeneradas.
- Baixar/exportar somente com permissão, confirmação de finalidade e marcação de confidencialidade.
- Exibir hash, versão, `asOf` e instante de geração.

### FE-FR-019 — comunicação ao COAF

- Área separada e protegida para draft, validação, revisão, aprovação, envio, recibo e retificação.
- Campos estruturados e narrativa com indicação clara do que será enviado.
- Relógio de prazo e alertas apenas para usuários autorizados.
- Preview não expõe conteúdo em logs/client analytics.
- Falha de envio mostra tentativa e ação segura; botão não cria comunicação duplicada.
- A UI não revela existência/status a perfis não autorizados.

### FE-FR-020 — administração de política e regra

Usuários autorizados podem:

- consultar versões/vigência;
- criar/editar draft;
- executar dry-run/backtest;
- comparar impacto;
- submeter/aprovar/agendar/rollback;
- auditar alterações.

As telas devem indicar qual backend é autoridade sem expor essa complexidade ao usuário. A UI nunca ativa regra por update genérico.

### FE-FR-021 — estados de rede e consistência

Cada tela trata:

- loading inicial e atualização discreta;
- empty state legítimo;
- erro recuperável;
- acesso negado;
- read model atrasado (`asOf`, syncing);
- objeto alterado por outro usuário;
- job assíncrono em andamento/falhou;
- sessão expirada.

Evitar optimistic update para decisão regulatória ou ação de conta; aguardar confirmação do comando.

## Requisitos não funcionais do frontend

- WCAG 2.2 AA como alvo, teclado completo e foco previsível.
- Responsivo para desktop corporativo; análise densa prioriza telas largas, mas fluxos críticos não quebram em viewport menor suportado.
- Internacionalização preparada; primeira língua pt-BR; datas/moedas formatadas sem perder UTC/valor exato.
- Content Security Policy e dependências auditadas; nenhuma PII em analytics, error reporting ou localStorage.
- Code splitting por rota e virtualização/paginação em listas grandes.
- Design system e tokens compartilhados; estados sem depender apenas de cor.
- Testes unitários de lógica de apresentação, integração de componentes, contratos mocks gerados de OpenAPI e E2E dos fluxos críticos.
- Feature flags vindas do backend/configuração, com fallback seguro.

## Critérios de aceite do primeiro release

- O analista encontra um sinal transacional na fila, abre o caso, entende regra/evidência e registra decisão sem usar outra tela.
- Outro analista vê imediatamente o ownership e o histórico, sem refazer o trabalho.
- Fonte sem resultado e fonte indisponível aparecem de forma inequivocamente diferente.
- Decisão de conta e suspeição podem divergir e são exibidas separadamente.
- Toda ação mostra ator/horário e pode ser localizada na timeline.
- Usuário sem permissão COAF não deduz por UI, rota ou erro se existe comunicação.
- Conflito entre duas edições não perde dados silenciosamente.

