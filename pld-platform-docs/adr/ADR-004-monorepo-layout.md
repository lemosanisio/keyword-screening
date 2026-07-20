# ADR-004 — Repositório único (monorepo) para as três aplicações

Status: Aceito — 2026-07-20

## Contexto

A versão inicial deste pacote (`agent-handoff.md`, seção "Layout de trabalho") recomendava quatro repositórios Git independentes (`keyword-screening`, `pld-customer-analysis`, `pld-workbench`, `pld-platform-docs`) e orientava não iniciar a implementação como monorepo.

A decisão de engenharia do time é o oposto: a plataforma PLD será versionada em **um único repositório Git**. O serviço transacional existente já foi movido para `pld-transaction-screening/` com histórico preservado (`git mv`, registrado como rename).

## Decisão

Um único repositório Git contendo:

```text
keyword-screening/                # monorepo da plataforma PLD
├── pld-transaction-screening/    # backend transacional (serviço existente)
├── pld-customer-analysis/        # novo backend
├── pld-workbench/                # novo frontend React (criado no Marco 4)
└── pld-platform-docs/            # este pacote, fonte dos contratos compartilhados
```

Esta ADR **substitui** a seção "Layout de trabalho" original do handoff. As demais decisões (ADR-001, ADR-002, ADR-003) permanecem inalteradas.

## Consequências

- **Monorepo não é monólito.** As fronteiras de ADR-001 seguem valendo: deploy, banco de dados e ownership independentes por aplicação; nenhum serviço lê tabelas do outro; integração por eventos duráveis.
- Contratos compartilhados (`shared/`, `adr/`) são versionados junto ao código, em `pld-platform-docs/`. Cada aplicação registra qual versão dos contratos consome.
- Mudança de schema compartilhado continua exigindo PR próprio, aprovado pelos donos dos dois backends, com nova versão de schema e período de convivência.
- Cada aplicação mantém build próprio e independente (wrapper/toolchain). Não criar build raiz compartilhado sem necessidade real e medida.
- CI/CD deve ter pipelines independentes por pasta, preservando deploy separado por aplicação.
- PRs seguem a forma recomendada do handoff: verticais, pequenos, sem combinar renomeação ampla com mudança de semântica.
