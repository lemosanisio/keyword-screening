# ADR-005 — convenções de integração v1

- Status: aceita
- Data: 2026-07-20

## Contexto

O Marco 0 do handoff exige congelar glossário, identificadores, envelope e eventos `v1`. Os contratos em `shared/integration-contracts.md` eram uma proposta lógica, com serialização e convenções em aberto.

## Decisões

1. **Serialização JSON.** Schema definido em JSON Schema (draft 2020-12). Avro/Protobuf e schema registry externo só entram se surgir necessidade real e medida.
2. **Catálogo de schemas no monorepo.** Schemas vivem versionados em `pld-platform-docs/schemas/v1/`, com fixtures sem PII. CI dos dois backends valida compatibilidade e exemplos dourados. Mudança incompatível cria nova versão de schema com período de convivência (regra já existente nos contratos).
3. **IDs opacos.** ULID com prefixo tipado por agregado: `pty_` (Party), `acy_` (AnalysisCycle), `cse_` (Case), `evl_` (TransactionEvaluation), `sig_` (TransactionSignal), `evd_` (Evidence), `dsr_` (Dossier), `coaf_` (CoafCommunication), `rsk_` (RiskProfile). Demais agregados seguem o mesmo padrão de 3-4 letras. CPF/CNPJ, nome e endereço nunca são chaves.
4. **Classificação de dados.** Três níveis para `dataClassification`: `INTERNAL` (padrão), `CONFIDENTIAL` (PII de clientes, evidências), `RESTRICTED` (comunicações COAF, dados sob sigilo regulatório). Nada sem classificação é publicado.
5. **Single-tenant.** A plataforma atende uma única instituição. `tenantId` foi removido do envelope. Se um dia existir segundo tenant, entra como mudança de schema v2.

## Consequências

- O envelope comum dos contratos fica congelado sem `tenantId` (editado nesta mesma decisão).
- Fixtures e testes de contrato referenciam schemas por caminho dentro do monorepo; não há dependência de serviço de registry.
- Consumidores continuam obrigados a aceitar campos desconhecidos (já previsto nos contratos), o que mantém evolução aditiva barata.
