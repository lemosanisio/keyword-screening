# `pld-customer-analysis` — requisitos

## Missão

Ser o sistema de registro da análise PLD centrada na pessoa/empresa e o backend da experiência unificada dos analistas. Ele agrega e interpreta evidências, aplica políticas, deriva exceções, administra casos e decisões, mantém revisão contínua e produz dossiê/comunicação regulatória.

“Agregador de dados” é apenas uma capacidade deste serviço. Seu valor está em transformar fontes heterogêneas em uma análise temporal, explicável e acionável.

## Escopo

Inclui:

- `Party` PF/PJ, relações e versões cadastrais;
- KYC e extensão do mesmo modelo para KYE/KYS/KYP;
- catálogo/execução de fontes, evidências, fatos e observações;
- identidade, aliases e matches de nome;
- mídia negativa, processos, listas/sanções, mandados, países e outros sinais;
- ciclos de onboarding, revisão periódica/event-driven e alerta transacional;
- assessment, deriva e política de decisão;
- fila única, caso, colaboração e prevenção de retrabalho;
- decisões de relacionamento e suspeição;
- solicitação/aplicação rastreada de aprovar, rejeitar, restringir, suspender ou encerrar conta;
- projeção de sinais/avaliações do motor transacional;
- dossiê, timeline e atendimento a requisição regulatória;
- workflow e submissão da comunicação ao COAF;
- APIs BFF para o frontend.

Não inclui:

- cadastro mestre do cliente ou contabilidade da conta;
- execução de regras para cada transação em alta vazão;
- armazenamento/cópia de imagens do Google Street View;
- scraping de fonte sem contrato/base aprovada;
- decisão autônoma final por LLM.

## Atores e permissões

Não há necessidade de mesas ou SLAs distintos. A mesma equipe pode trabalhar em todos os casos, com permissões por ação.

| Papel | Capacidades típicas |
|---|---|
| `ANALYST` | pesquisar, assumir caso, consultar evidência, registrar observação, pedir informação, propor decisão |
| `APPROVER` | revisar ação sujeita a maker-checker, aprovar/rejeitar para correção |
| `RULE_ADMIN` | criar políticas e parâmetros; não ativar sozinho quando segregação for exigida |
| `AUDITOR` | consulta histórica e exportação autorizada, sem alterar análise |
| `SYSTEM` | coleta, avaliação, timers, projeções e decisões automáticas permitidas |

Permissões devem ser granulares: visualizar comunicação COAF, enviar, exportar dossiê, aplicar decisão de conta, gerir fonte e reabrir/retificar.

## Requisitos funcionais

### CA-FR-001 — parte comum PF/PJ

O serviço deve manter `Party` com `partyType = PERSON | ORGANIZATION`, IDs opacos e snapshots cadastrais versionados.

Fatos comuns:

- nomes oficiais e aliases;
- identificadores com tipo, emissor, país e validade;
- endereços e geolocalização;
- país de residência, constituição, operação e exposição;
- status e origem cadastral;
- contas/relacionamentos externos por referência.

Fatos de PF podem incluir nascimento, nacionalidade, ocupação, renda, escolaridade quando necessária/aprovada, PEP e capacidade de representação. Fatos de PJ podem incluir constituição, atividade/CNAE, faturamento, sede, natureza jurídica, sócios, administradores e beneficiários finais.

Cada campo registra fonte e validade; a aplicação não presume que o snapshot mais novo era conhecido em um ciclo antigo.

### CA-FR-002 — relações e programas de diligência

Modelar relações entre `Party` com tipo e vigência, incluindo:

```text
SHAREHOLDER
LEGAL_REPRESENTATIVE
DIRECTOR
ULTIMATE_BENEFICIAL_OWNER
ATTORNEY
EMPLOYEE
SUPPLIER
PARTNER
OTHER
```

Um `DueDiligenceProgram` indica contexto (`KYC`, `KYE`, `KYS`, `KYP`) sem criar um backend novo para cada sigla. O MVP prioriza KYC; terminologia e requisitos dos demais programas são configuráveis e validados com o negócio.

### CA-FR-003 — ingestão e resolução de duplicidade

- Consumir cadastro/alterações de sistemas mestres com inbox.
- Conciliar aliases e identificadores sem fazer merge irreversível automático por match fraco.
- Sugerir potenciais duplicatas com score/explicação.
- Merge/split autorizado preserva IDs anteriores, atores, motivo e redirecionamento histórico.
- Pesquisar por identificador exato, nome, alias, conta, caso, processo e referências, conforme permissão.

### CA-FR-004 — ciclo de análise

Criar `AnalysisCycle` dos tipos definidos no glossário e aplicar a máquina de estados compartilhada.

Critérios:

- um ciclo referencia snapshot cadastral e política efetiva;
- múltiplos ciclos podem existir, mas a política evita trabalho duplicado para o mesmo gatilho;
- ciclo anterior permanece imutável;
- toda transição possui reason code, ator e timestamp;
- encerramento verifica requisitos obrigatórios e pendências.

### CA-FR-005 — catálogo e plano de fontes

Manter `SourceDefinition` versionada com:

- finalidade, owner e classificação;
- adapter/configuração e autenticação por referência secreta;
- tipos de Party/países cobertos;
- fatos/findings produzidos;
- validade/freshness;
- timeout, retry, rate limit e contingência;
- termos/licença, retenção e base/aprovação interna;
- política para resposta vazia, parcial e indisponível.

Ao iniciar um ciclo, um `EvidenceCollectionPlan` registra quais fontes/requisitos serão executados. Alterar o catálogo depois não muda o plano histórico.

### CA-FR-006 — execução de fontes e evidência

Cada execução deve terminar em:

```text
SUCCESS_WITH_DATA
SUCCESS_NO_RESULTS
PARTIAL
CONFLICT
UNAVAILABLE
ERROR
EXPIRED
```

Persistir request fingerprint, instante, adapter/version, status, metadados de resposta, evidências criadas e erro sanitizado. Credenciais e payloads desnecessários não são persistidos.

Evidência deve possuir fonte, observação temporal, período de validade, conteúdo/reference, hash de integridade, classificação e relação com fatos/findings. Retentativa cria nova execução; não sobrescreve a anterior.

### CA-FR-007 — agregação e extração de notícias

O pipeline de mídia deve:

1. buscar/receber itens de fornecedores aprovados;
2. normalizar URL, veículo, publicação, título, idioma e data;
3. deduplicar cópias/sindicação sem perder proveniência;
4. resolver entidades/aliases;
5. extrair alegação, papel da parte, local, datas, tema/crime alegado e estágio;
6. classificar relevância/confiança e gerar resumo com referências;
7. apresentar matéria e contexto para revisão quando necessário.

Requisitos de segurança:

- notícia é conteúdo não confiável;
- não executar instruções contidas no texto;
- distinguir alegação, investigação, processo, absolvição, condenação e desfecho;
- nunca concluir culpabilidade somente por mídia;
- respeitar licença e limite de reprodução, preferindo referência/trechos permitidos.

Amazon Bedrock pode implementar portas de extração/classificação, mas o domínio permanece agnóstico ao provedor e registra modelo/versão/template/confiança.

### CA-FR-008 — processos judiciais

- Descoberta e enriquecimento de processo são capacidades diferentes.
- Associação a `Party` exige `IdentityResolution` com atributos de desambiguação.
- Modelar número, tribunal, classe, assuntos, movimentos, partes quando legalmente disponíveis, estágio e decisões relevantes.
- `LegalCaseFinding` diferencia menção, parte, polo, investigação/processo, decisão, condenação, absolvição/arquivamento e situação desconhecida.
- Guardar fonte/data e não transformar ausência em certidão negativa sem base.
- DataJud público pode enriquecer processo conhecido; não presumir busca pública por CPF/nome onde o contrato não oferece esses campos.

### CA-FR-009 — sanções, mandados, PEP e países

- Cada lista possui autoridade, jurisdição, versão/data e cobertura.
- Match guarda forma do nome, alias, score, atributos comparados e divergências.
- Match potencial é finding; match confirmado é decisão de resolução de identidade auditada.
- Países são avaliados por listas/políticas versionadas e pelo tipo de exposição (residência, operação, contraparte, nacionalidade etc.).
- Mudança de lista pode abrir `EVENT_DRIVEN_REVIEW` para partes afetadas.

### CA-FR-010 — nomes, aliases e identificações exibidas

O serviço deve comparar nome oficial, nome social, nomes anteriores, aliases, transliterações e `CardDescriptor` sem confundir descritor com identidade civil.

Todo `NameMatch` registra:

- strings originais e normalizadas;
- algoritmo/modelo e versão;
- score e limiar aplicável;
- explicação (tokens, ordem, transliteração, data/outros atributos);
- candidatos e decisão humana quando houver;
- fonte do alias.

Match baixo/conflitante deriva; não rejeita automaticamente por si só.

### CA-FR-011 — endereços, distância e Street View

- Normalizar e geocodificar endereço por adaptador aprovado.
- Calcular distância entre sede e endereços de sócios/representantes com método e versão registrados; distância é finding contextual, não fraude por si só.
- Expor ao frontend coordenadas/endereço permitido para Google Maps.
- Registrar visualização de Street View como `Observation` com `panoId`, `viewedAt`, ator, endereço consultado e nota estruturada.
- Não armazenar screenshot/imagem por padrão nem usar imagem em modelo sem autorização contratual específica.
- Exibir data/cobertura disponível e aviso de que a imagem pode não representar o estado atual.

### CA-FR-012 — assessment e política

O `Assessment` deve avaliar fatos/findings contra `PolicyVersion` e retornar:

- requisitos satisfeitos e não satisfeitos;
- risco/recomendação explicável;
- rota (`AUTOMATIC`, `DERIVED_TO_ANALYST`, `MANDATORY_SECOND_APPROVAL`, `TECHNICAL_RETRY`);
- motivos de deriva;
- ações sugeridas para completar análise;
- decisões automáticas permitidas, quando configuradas.

Política ativa é imutável e possui vigência/aprovação. Reprocessar com política nova cria assessment novo, ligado ao anterior.

### CA-FR-013 — deriva e pendência técnica

- Deriva exige reason code, evidências/requisitos faltantes e prioridade explicada.
- Fonte indisponível pode gerar `TECHNICAL_PENDING` com timer/retry antes de derivar, conforme política.
- O analista pode retentar fonte, solicitar informação ou dispensar requisito apenas com permissão e justificativa.
- Resolver uma deriva dispara novo assessment; não edita o resultado anterior.

### CA-FR-014 — caso e fila única

`Case` deve suportar:

- origens onboarding, revalidação, transação, regulatória ou manual;
- agrupamento de findings/ciclos conforme política;
- prioridade, status, responsável, watchers e tags controladas;
- assumir, atribuir, devolver à fila, comentar, anexar, solicitar informação, pausar por dependência e concluir;
- visão de outros casos/ciclos da mesma parte para evitar retrabalho;
- detecção de potencial duplicata antes da criação;
- controle de concorrência para evitar sobrescrita.

Estados sugeridos:

```text
OPEN
ASSIGNED
IN_ANALYSIS
WAITING_INFORMATION
WAITING_TECHNICAL
PENDING_APPROVAL
DECIDED
CLOSED
CANCELLED_AS_DUPLICATE
```

### CA-FR-015 — decisão de relacionamento

Registrar os valores definidos no glossário para onboarding e ongoing.

Critérios:

- decisão automática somente quando política aprovada permitir e evidências forem suficientes;
- rejeição, restrição, suspensão e encerramento exigem reason codes/narrativa e podem requerer segundo aprovador;
- emitir `AccountDecisionIssued.v1` ou comando idempotente a `AccountDecisionPort`;
- guardar separadamente solicitação, tentativa e confirmação real do sistema de contas;
- falha de integração deixa pendência operacional visível, sem fingir aplicação.

### CA-FR-016 — decisão de suspeição

Registrar `NO_SUSPICION`, `KEEP_MONITORING`, `COMMUNICATE_TO_COAF` ou `INCONCLUSIVE` independentemente da decisão de conta.

- `COMMUNICATE_TO_COAF` cria uma comunicação draft exatamente uma vez.
- `KEEP_MONITORING` define gatilho/tempo ou motivo, não apenas texto livre.
- `NO_SUSPICION` exige explicação frente aos findings investigados.
- Retificação cria decisão sucessora; não altera a original.

### CA-FR-017 — aprovação e rejeição de decisão

- Política define quando maker-checker é obrigatório.
- Aprovador vê evidências e diferenças desde a proposta.
- Não pode aprovar a própria proposta quando segregação estiver ativa.
- Rejeição para correção preserva comentários e versão proposta.
- Aprovação concorrente é protegida por version/ETag.

### CA-FR-018 — revalidação contínua

- Agendar próxima revisão por risco, política e tipo de evidência.
- Reagir a eventos relevantes e expiração de fatos.
- Coalescer gatilhos próximos sem perdê-los.
- Evitar coleta integral quando evidência ainda válida for suficiente.
- Exibir cobertura, atraso de scheduler, próxima revisão e motivo.
- Um ciclo novo compara alterações com o anterior e mantém ambos.

### CA-FR-019 — projeção transacional

Consumir eventos do motor com inbox e manter view local:

- avaliações/sinais por parte, conta e caso;
- regras acionadas e explicação histórica;
- transação/snapshot mínimo permitido;
- status de ação técnica;
- correlação com `ManualReviewRequested`.

Deduplicar pedido, agrupar conforme política e abrir/atualizar caso. Não reexecutar a regra transacional no novo backend.

### CA-FR-020 — timeline e visibilidade

- Uma timeline unifica eventos cadastrais, fontes, assessments, casos, transações, decisões, conta, dossiê e COAF.
- Cada item respeita RBAC e possui link para objeto original.
- Pesquisa mostra análise/caso existente antes de permitir criar outro.
- Comentários e decisões exibem autor, instante e alterações; não permitem edição silenciosa.
- Auditor consegue consultar estado histórico sem conceder permissão operacional.

### CA-FR-021 — dossiê regulatório

- Gerar por ciclo/caso/data de referência.
- Incluir índice e referências verificáveis às evidências.
- Suportar `DRAFT`, `GENERATING`, `READY`, `FAILED`, `SUPERSEDED`.
- Snapshot `READY` é imutável, versionado, hash-assinado ou com integridade equivalente.
- Regeneração após correção cria versão nova e explica diferença.
- Exportação (ex.: PDF/JSON) registra solicitante, finalidade e arquivo exato.

### CA-FR-022 — comunicação ao COAF

Implementar estados do glossário e:

- criar a partir de decisão explícita;
- preencher dados estruturados a partir do dossiê sem acoplá-los ao layout interno;
- permitir narrativa, validação, revisão, aprovação e correção;
- calcular prazo configurado e alertar risco de vencimento;
- enviar via `CoafSubmissionPort` manual assistido, lote ou webservice, conforme canal homologado;
- persistir versão do payload, tentativas, resposta/recibo e reconciliação;
- permitir retificação ligada à comunicação original;
- aplicar controles de sigilo e não tipping-off.

### CA-FR-023 — BFF e modelos de leitura

Expor APIs orientadas às telas do Workbench para:

- fila e contadores;
- pesquisa global;
- resumo 360 de `Party`;
- detalhe de ciclo/caso;
- matriz de evidência/requisitos;
- timeline;
- transações e sinais;
- decisões/aprovações;
- dossiê/COAF;
- administração permitida.

Read models são locais e eventualmente consistentes. A resposta inclui `asOf` e estado de sincronização relevante; o endpoint não faz fan-out síncrono para o motor transacional.

### CA-FR-024 — atendimento regulatório

- Permitir localizar uma parte, período, ciclo, caso, decisão e comunicação.
- Exportar pacote autorizado contendo dossiê e manifesto de evidências.
- Registrar finalidade, solicitante e conteúdo exportado.
- A busca histórica usa versões vigentes na época, não somente estado atual.

## Regras de negócio obrigatórias

1. Nenhuma decisão existe sem ciclo, política e inputs referenciados.
2. Evidência e decisão histórica não são editadas in-place.
3. Fonte vazia, parcial, indisponível e com erro são estados diferentes.
4. Deriva não implica suspeição.
5. Decisão de conta não determina decisão COAF, nem o contrário.
6. Processo, mídia ou name match não equivale sozinho a culpa/identidade confirmada.
7. Aplicar decisão em conta é efeito externo confirmado separadamente.
8. Um evento duplicado não cria novo ciclo/caso/comunicação.
9. Todo caso mostra trabalho anterior relacionado antes de nova análise.
10. LLM produz dados auxiliares com proveniência, nunca a decisão final autônoma.

## MVP recomendado

Primeira fatia com valor:

- Party PF/PJ + snapshot;
- ciclo e timeline;
- consumo de sinais transacionais;
- fila/caso único;
- análise, decisão humana e confirmação de ação;
- BFF da fila, visão 360 e caso;
- outbox/inbox, auditoria e RBAC.

Depois: fontes/enriquecimento, onboarding automático, revalidação, decisão automática, dossiê e COAF. Essa ordem aposenta cedo a fragmentação de telas sem bloquear a evolução mais complexa de fontes.

