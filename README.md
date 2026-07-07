# Rule Platform — PLD Screening & Decision Engine

Plataforma de regras para **Prevenção à Lavagem de Dinheiro (PLD)** que detecta condições suspeitas em transações PIX e decide quando gerar alertas para analistas de compliance.

## Quick Start

```bash
# Pré-requisitos: JDK 21, Docker

# Subir banco
docker compose -f docker/docker-compose.yml up postgres -d

# Rodar aplicação
./gradlew bootRun

# Testes
./gradlew test
```

- Aplicação: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html

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
