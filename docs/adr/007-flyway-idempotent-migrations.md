# ADR-007: Flyway Migrations Idempotentes

## Status

Aceita

## Contexto

Em ambientes onde Flyway pode ser executado múltiplas vezes (desenvolvimento local, CI/CD com rollback manual, ou cenários de recovery), migrations que falham na segunda execução causam bloqueio do deploy.

## Decisão

Todas as migrations do Decision Engine e Alert Context utilizam statements idempotentes:

### DDL

```sql
CREATE TABLE IF NOT EXISTS ...
CREATE INDEX IF NOT EXISTS ...
CREATE UNIQUE INDEX IF NOT EXISTS ...
```

### DML (seed data)

```sql
INSERT INTO ... VALUES (...)
ON CONFLICT (unique_column) DO NOTHING;
```

### Versionamento

| Arquivo | Conteúdo |
|---------|----------|
| V6__create_decision_context_tables.sql | Tabelas do Decision Context (IF NOT EXISTS) |
| V7__create_alert_context_tables.sql | Tabela alert (IF NOT EXISTS) |
| V8__seed_decision_context_data.sql | Seed data MVP (ON CONFLICT DO NOTHING) |

## Consequências

- Migrations podem ser re-executadas sem erro
- Facilita development workflow (drop + recreate sem cleanup manual)
- Flyway checksum validation ainda protege contra alterações não intencionais
- Ligeiro overhead de verificação IF NOT EXISTS (negligível)
