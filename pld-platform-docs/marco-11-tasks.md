# Marco 11 — Onboarding simulado, revalidação e relações

Status: não iniciado

## Objetivo

Fechar o ciclo de vida completo da análise PLD: onboarding automático por evento (simulado), revalidação periódica/event-driven, grafo de relações entre partes, e as telas correspondentes no Workbench. Após este marco, o protótipo cobre todos os fluxos documentados de ponta a ponta.

## Hipóteses

- Um publisher mock de eventos de sistema mestre valida o consumo sem depender de integração real.
- Revalidação é um novo `AnalysisCycle` disparado por política (tempo ou evento), reutilizando a infra de evidências do Marco 9.
- O grafo de relações entre PF/PJ é essencial para o analista entender beneficiários finais e exposição.

## Decisões do marco

- [ ] Eventos de entrada (`CustomerOnboardingStarted.v1`, `CustomerDataChanged.v1`, `PartyRelationshipChanged.v1`) são publicados por um script/adapter mock.
- [ ] O consumer no `pld-customer-analysis` cria/atualiza Party e dispara AnalysisCycle de onboarding.
- [ ] Revalidação periódica: scheduler que identifica parties cuja última revisão expirou conforme política.
- [ ] Revalidação event-driven: mudança de risco ou sinal transacional pode disparar novo ciclo.
- [ ] Relações são modeladas como edges tipados entre Party nodes.
- [ ] O frontend exibe relações como lista navegável (grafo visual é opcional/futuro).

## Fatias de entrega

### M11.1 — Publisher mock de eventos de sistema mestre

- [ ] Script/CLI que publica N eventos simulados para SQS:
  - `CustomerOnboardingStarted.v1` — cria Party com snapshot inicial.
  - `CustomerDataChanged.v1` — altera snapshot (endereço, renda, etc.).
  - `PartyRelationshipChanged.v1` — adiciona/remove relação entre parties.
- [ ] Payloads seguem schemas v1 existentes em `pld-platform-docs/schemas/v1/`.
- [ ] Dados gerados deterministicamente: nomes, CPFs fake (formato válido), endereços brasileiros.
- [ ] Pode ser executado como `./gradlew seedOnboarding` ou script Bun.

### M11.2 — Consumer de onboarding no customer-analysis

- [ ] Consumer SQS para `CustomerOnboardingStarted.v1`:
  - Cria Party se não existe (deduplicação por externalId + sourceSystem).
  - Persiste snapshot inicial.
  - Abre `AnalysisCycle` com trigger `ONBOARDING`.
  - Registra na timeline: "Onboarding iniciado".
- [ ] Consumer para `CustomerDataChanged.v1`:
  - Cria nova versão do snapshot (não sobrescreve anterior).
  - Avalia se mudança material dispara revalidação (ex: mudança de país, PEP).
  - Registra na timeline: "Dados atualizados — {campos alterados}".
- [ ] Consumer para `PartyRelationshipChanged.v1`:
  - Cria/atualiza/remove relação entre parties.
  - Registra na timeline: "Relação {tipo} com {partyId} {adicionada|removida}".
- [ ] Inbox com deduplicação por eventId.

### M11.3 — Modelo de relações entre parties

- [ ] Tabela `party_relationship`: id, fromPartyId, toPartyId, type, participationPercentage, startDate, endDate, sourceSystem, sourceEventId, createdAt.
- [ ] Tipos: SHAREHOLDER, LEGAL_REPRESENTATIVE, DIRECTOR, ULTIMATE_BENEFICIAL_OWNER, ATTORNEY, EMPLOYEE, SUPPLIER, PARTNER.
- [ ] API: `GET /v1/parties/:partyId/relationships` — retorna relações diretas (from e to).
- [ ] Navegação: cada relação inclui link para a party relacionada.
- [ ] Migration Flyway para tabela.

### M11.4 — Revalidação periódica

- [ ] Tabela `revalidation_policy`: riskLevel → reviewIntervalDays.
  - LOW: 365 dias, MEDIUM: 180 dias, HIGH: 90 dias.
- [ ] Scheduler (cron diário): identifica parties com `lastReviewCompletedAt + intervalDays < now()`.
- [ ] Para cada party elegível:
  - Abre novo `AnalysisCycle` com trigger `PERIODIC_REVIEW`.
  - Executa evidence policy do Marco 9 (adapters simulados).
  - Abre Case se evidência indica mudança material ou indisponibilidade.
- [ ] Coalescência: se já existe ciclo aberto, não cria duplicata.
- [ ] Timeline: "Revisão periódica iniciada — política v{n}".

### M11.5 — Revalidação event-driven

- [ ] Gatilhos configuráveis:
  - Mudança de riskLevel (evento `CustomerRiskProfileUpdated`).
  - Sinal transacional com severidade HIGH.
  - Mudança de dados cadastrais material (país, PEP).
- [ ] Cada gatilho pode disparar novo AnalysisCycle com trigger `EVENT_DRIVEN`.
- [ ] Coalescência com revalidação periódica: se ciclo existe, adiciona gatilho ao ciclo existente.
- [ ] Registro do gatilho: eventId, eventType, reason.

### M11.6 — Frontend: Revalidação (FE-FR-017)

- [ ] Seção no workspace: "Revisão / Revalidação".
- [ ] Exibir:
  - Última revisão: data, resultado, política aplicada.
  - Próxima revisão: data estimada, motivo, evidências a expirar.
  - Gatilhos coalescidos desde última revisão.
- [ ] Diff entre ciclos: comparação lado a lado (evidências, fatos, decisões).
- [ ] Botão "Iniciar revisão manual" com motivo (evita duplicata com ciclo ativo).
- [ ] API: `GET /v1/parties/:partyId/review-status`, `POST /v1/parties/:partyId/review`.

### M11.7 — Frontend: Relações PF/PJ (FE-FR-006)

- [ ] Nova seção no workspace: "Relações".
- [ ] Lista navegável de relações:
  - Tipo (badge), participação (%), vigência, fonte.
  - Link para a party relacionada (abre em nova tab ou drill-down).
- [ ] Filtro por tipo de relação.
- [ ] Indicação de beneficiário final (UBO) destacada.
- [ ] Fallback acessível: lista/tabela em vez de grafo visual complexo.

### M11.8 — Frontend: Shell expandido (FE-FR-002 completo)

- [ ] Indicador de pendências do usuário (casos atribuídos não iniciados).
- [ ] Atalhos recentes (últimos 5 casos visitados).
- [ ] Badge de ambiente fora de produção.
- [ ] Ajuda contextual (link para docs/guia).
- [ ] Navegação para admin de regras (/admin/rules — Marco 8) e dossiê.

### M11.9 — Governance: maker-checker para ativação de regras

- [ ] Ativação de configuração de regra requer segunda aprovação por APPROVER.
- [ ] Workflow: RULE_ADMIN propõe → APPROVER aprova → configuração ativada.
- [ ] Rejeição volta para draft com motivo.
- [ ] Tela de admin (Marco 8) expandida com estado "pending approval" e ações de approve/reject.
- [ ] Registro na timeline/audit.

## Cenários BDD

```gherkin
Scenario: onboarding por evento cria party e ciclo
  Given um evento CustomerOnboardingStarted.v1 para CPF 123.456.789-00
  When o consumer processa o evento
  Then Party é criada com partyType=PERSON e snapshot v1
  And AnalysisCycle é aberto com trigger=ONBOARDING
  And timeline registra "Onboarding iniciado"

Scenario: mudança de dados material dispara revalidação
  Given um Party existente com country=BR
  When CustomerDataChanged.v1 altera country para PA (Panamá)
  Then um novo AnalysisCycle é aberto com trigger=EVENT_DRIVEN
  And reason inclui "COUNTRY_CHANGE"

Scenario: revalidação periódica para party de alto risco
  Given um Party com riskLevel=HIGH e lastReviewCompletedAt há 91 dias
  When o scheduler de revalidação executa
  Then um novo AnalysisCycle é aberto com trigger=PERIODIC_REVIEW
  And evidence policy HIGH é aplicada (4 adapters executados)

Scenario: coalescência evita ciclo duplicado
  Given um AnalysisCycle aberto para party pty_ABC
  When um segundo gatilho (sinal transacional) ocorre
  Then nenhum novo ciclo é criado
  And o gatilho é anexado ao ciclo existente

Scenario: relações exibidas no Workbench
  Given Party pty_ABC com relação SHAREHOLDER(30%) para pty_DEF
  When o analista abre a seção "Relações"
  Then a lista mostra: pty_DEF, tipo=SHAREHOLDER, participação=30%
  And o link navega para o workspace de pty_DEF

Scenario: maker-checker na ativação de regra
  Given um RULE_ADMIN que propõe ativação de uma configuração
  When o APPROVER rejeita com motivo "threshold muito baixo"
  Then a configuração volta para draft
  And o motivo é registrado
  And o RULE_ADMIN pode ajustar e re-submeter
```

## Critérios de aceite

- [ ] Eventos mock de onboarding criam parties e ciclos automaticamente.
- [ ] Revalidação periódica identifica parties elegíveis e abre ciclos sem duplicata.
- [ ] Relações entre parties são persistidas e navegáveis no frontend.
- [ ] Diff entre ciclos mostra mudanças de evidência/fatos entre revisões.
- [ ] Maker-checker impede ativação unilateral de regra.
- [ ] Shell expandido com pendências, recentes e navegação completa.
- [ ] Todos os fluxos documentados nos requisitos estão cobertos (com simulação).
- [ ] Testes unitários, integração e Playwright permanecem verdes.

## Fora deste marco (futuro — produção)

- Eventos reais de sistema mestre (EXT-2).
- Adapters reais de evidência (EXT-4).
- COAF real (EXT-5).
- RBAC via provedor corporativo (EXT-6).
- Google Maps/Street View (FE-FR-010) — depende de avaliação de privacidade (EXT-8).
- Performance em volume real.
- Multi-tenancy.
