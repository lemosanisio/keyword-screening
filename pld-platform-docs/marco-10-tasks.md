# Marco 10 — Dossiê interno e comunicação COAF simulada

Status: não iniciado

## Objetivo

Implementar a geração de dossiê interno a partir de evidências e decisões do caso, e o workflow completo de comunicação ao COAF com adapter simulado. O analista deve conseguir produzir, revisar, aprovar e "enviar" uma comunicação regulatória dentro do Workbench.

## Hipóteses

- O dossiê é um artefato derivado (não editável diretamente) — ele reflete o estado do caso num instante.
- Comunicação ao COAF é um workflow com aprovação e pode falhar/retificar.
- Um adapter mock que aceita/rejeita/gera pendência valida o workflow completo sem canal real.

## Decisões do marco

- [ ] Dossiê é gerado assincronamente; o analista solicita e acompanha status.
- [ ] O dossiê tem versão, hash, `asOf` (instante de referência) e manifesto (índice do conteúdo incluído).
- [ ] Comunicação COAF é um aggregate com status machine: DRAFT → PENDING_REVIEW → APPROVED → SUBMITTED → ACKNOWLEDGED / REJECTED / RETIFIED.
- [ ] O adapter mock simula: 80% acknowledged, 10% rejected (dados incompletos), 10% pendência (timeout).
- [ ] Somente usuários com permissão `COAF_SUBMIT` visualizam a área de comunicação.
- [ ] A UI não revela existência de comunicação a perfis sem permissão (FE-FR-019).

## Fatias de entrega

### M10.1 — Modelo de dossiê

- [ ] Criar aggregate `Dossier` no `pld-customer-analysis` com: dossierId, caseId, partyId, version, status (GENERATING, READY, FAILED), asOf, generatedAt, manifestHash.
- [ ] Manifesto: lista de seções incluídas (party_summary, risk_history, evidence_matrix, decisions, timeline, signals).
- [ ] Cada seção referencia os IDs e versões dos objetos incluídos.
- [ ] Geração é um job que coleta dados do caso e produz o dossiê.
- [ ] Migration Flyway para tabela `dossier` e `dossier_section`.
- [ ] Geração detecta lacunas (evidence pendente, decisão ausente) e inclui no manifesto como `gaps`.

### M10.2 — API de dossiê

- [ ] `POST /v1/cases/:caseId/dossier` — solicita geração (assíncrono, retorna 202 + dossierId).
- [ ] `GET /v1/cases/:caseId/dossier/:dossierId` — retorna status, manifesto e gaps.
- [ ] `GET /v1/cases/:caseId/dossier/:dossierId/content` — retorna conteúdo estruturado completo (JSON).
- [ ] Comparação: `GET /v1/cases/:caseId/dossier/diff?from=:v1&to=:v2` — diff entre versões.
- [ ] Registro na timeline: "Dossiê v{n} gerado".

### M10.3 — Modelo de comunicação COAF

- [ ] Aggregate `CoafCommunication` com: communicationId, caseId, partyId, dossierId, status, version.
- [ ] Status machine: DRAFT → PENDING_REVIEW → APPROVED → SUBMITTED → ACKNOWLEDGED | REJECTED.
- [ ] RETIFIED: nova comunicação referenciando a anterior.
- [ ] Campos estruturados: tipo de operação, valor, data, envolvidos, narrativa, enquadramento legal.
- [ ] Prazo regulatório (timer): dias úteis desde o gatilho (decisão de suspeição).
- [ ] Migration Flyway para `coaf_communication` e `coaf_communication_event`.

### M10.4 — Workflow de comunicação

- [ ] `POST /v1/cases/:caseId/coaf` — cria draft a partir do dossiê.
- [ ] `PATCH /v1/cases/:caseId/coaf/:id/submit-for-review` — submete para aprovação.
- [ ] `PATCH /v1/cases/:caseId/coaf/:id/approve` — aprova (requer APPROVER + permissão COAF_SUBMIT).
- [ ] `PATCH /v1/cases/:caseId/coaf/:id/submit` — envia ao adapter.
- [ ] `POST /v1/cases/:caseId/coaf/:id/rectify` — cria retificação.
- [ ] Cada transição registrada na timeline.
- [ ] Evento publicado: `CoafCommunicationStatusChanged.v1`.

### M10.5 — Adapter mock COAF

- [ ] Implementa interface `CoafSubmissionPort` com `submit(communication): SubmissionResult`.
- [ ] `SubmissionResult`: ACKNOWLEDGED (protocolo gerado), REJECTED (motivo), PENDING (timeout simulado).
- [ ] Latência simulada: 1-3s.
- [ ] Protocolo gerado: formato `COAF-{ano}-{sequencial}`.
- [ ] Resultado persiste na communication e dispara evento.
- [ ] Em caso de PENDING, um scheduler re-tenta após intervalo configurável.

### M10.6 — Frontend: Tela de dossiê (FE-FR-018)

- [ ] Seção no workspace do caso: "Dossiê".
- [ ] Botão "Gerar dossiê" → status de geração (spinner/polling).
- [ ] Visualização do manifesto: seções incluídas, gaps destacados em amarelo.
- [ ] Botão "Visualizar conteúdo" → modal/drawer com JSON formatado/humanizado.
- [ ] Comparação entre versões (diff visual lado a lado).
- [ ] Indicadores: hash, versão, asOf, generatedAt.
- [ ] Download/exportação com confirmação de finalidade e marcação de confidencialidade.

### M10.7 — Frontend: Área COAF (FE-FR-019)

- [ ] Nova seção no workspace, visível APENAS para roles com permissão COAF.
- [ ] Tela de draft: campos estruturados pré-preenchidos a partir do dossiê.
- [ ] Narrativa editável com indicação clara do que será enviado.
- [ ] Botões de workflow: submeter para revisão → aprovar → enviar.
- [ ] Status visual da comunicação com timeline de eventos.
- [ ] Relógio de prazo regulatório (countdown em dias úteis).
- [ ] Recibo de protocolo após envio bem-sucedido.
- [ ] Retificação: botão que cria nova versão referenciando a anterior.
- [ ] Tratamento de falha: mostra tentativa, erro e ação segura (retry sem duplicar).

## Cenários BDD

```gherkin
Scenario: geração de dossiê coleta dados do caso
  Given um caso DECIDED com evidências SATISFIED e decisão de suspeição
  When o analista solicita geração de dossiê
  Then o dossiê é gerado com status READY
  And o manifesto inclui party_summary, evidence_matrix, decisions, timeline
  And gaps está vazio

Scenario: dossiê identifica lacunas
  Given um caso com requirement LEGAL_PROCEEDINGS pendente
  When o dossiê é gerado
  Then gaps contém "LEGAL_PROCEEDINGS: PENDING"
  And o dossiê tem status READY (lacunas não bloqueiam geração)

Scenario: comunicação COAF workflow completo
  Given um dossiê READY para o caso
  When o analista cria draft de comunicação
  And um APPROVER aprova a comunicação
  And a comunicação é submetida ao adapter
  Then o adapter mock retorna ACKNOWLEDGED com protocolo COAF-2026-001
  And status final é ACKNOWLEDGED
  And timeline registra todas as transições

Scenario: comunicação COAF rejeitada pelo adapter
  Given uma comunicação aprovada
  When o adapter mock retorna REJECTED com motivo "campos incompletos"
  Then status é REJECTED
  And o analista pode retificar criando nova versão

Scenario: permissão COAF oculta a seção
  Given um ANALYST sem permissão COAF_SUBMIT
  When abre o caso no Workbench
  Then a seção COAF não é renderizada
  And nenhuma rota /coaf é acessível
```

## Critérios de aceite

- [ ] Dossiê gerado contém todas as seções relevantes com referências versionadas.
- [ ] Comunicação COAF percorre a status machine completa (DRAFT → ACKNOWLEDGED).
- [ ] Adapter mock simula sucesso/rejeição/timeout realisticamente.
- [ ] Retificação cria nova comunicação vinculada à anterior.
- [ ] Prazo regulatório é visível e contabiliza apenas dias úteis (hardcoded sem calendário real).
- [ ] Permissão COAF efetivamente oculta a seção para roles sem acesso.
- [ ] Testes unitários, integração e Playwright verdes.

## Fora deste marco

- Canal COAF real (EXT-5).
- Calendário de feriados para prazo (INT-4).
- Exportação PDF do dossiê.
- Validação dos 9 itens regulatórios da Circular 3.978 (EXT-6).
