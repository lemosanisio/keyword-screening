# Rule Platform — PLD Screening & Decision Engine

Plataforma de regras para **Prevenção à Lavagem de Dinheiro (PLD)** que detecta condições suspeitas em transações PIX e decide quando gerar alertas para analistas de compliance.

## Quick Start

```bash
# Pré-requisitos: JDK 21, Docker

# Subir banco (na raiz do monorepo)
docker compose up postgres -d

# Rodar aplicação (dentro do serviço)
cd pld-transaction-screening
./gradlew bootRun

# Testes
./gradlew test
```

- Aplicação: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html

## Estrutura

Monorepo da plataforma PLD:

| Caminho | Descrição |
|---------|-----------|
| `pld-transaction-screening/` | API Kotlin/Spring — screening de transações PIX |
| `pld-customer-analysis/` | Novo backend de análise de clientes (requisitos em `pld-customer-analysis/docs/`) |
| `pld-workbench/` | Frontend React (a partir do Marco 4 do plano de expansão) |
| `pld-platform-docs/` | Arquitetura, requisitos e contratos compartilhados da plataforma |
| `docker-compose.yml` | Infraestrutura compartilhada (Postgres) |
| `docs/` | Documentação da plataforma |

## Documentação

Toda a documentação está em [`docs/`](docs/README.md):

| Documento | Público-alvo | Descrição |
|-----------|--------------|-----------|
| [Visão Geral](docs/overview.md) | Todos | Arquitetura, stack, bounded contexts |
| [Conceitos](docs/concepts.md) | Todos | Entity, Fact, Rule, Expression, Decision, Alert |
| [Guia do Analista](docs/user-guide.md) | Analista PLD | Configurar regras, dry-run, alertas |
| [Guia de Engenharia](docs/technical-guide.md) | Engenharia | Expandir catálogo (Entity, Fact, Resolver) |
| [API Reference](docs/api-reference.md) | Todos | Endpoints, erros |
| [Desenvolvimento](docs/development.md) | Engenharia | Setup, comandos, testes, cobertura |
| [ADRs](docs/adr/) | Engenharia | Decisões arquiteturais |
