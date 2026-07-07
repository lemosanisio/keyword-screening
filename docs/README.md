# Documentação — Rule Platform PLD

Toda a documentação do projeto está consolidada neste diretório.

## Índice

| Documento | Público-alvo | Descrição |
|-----------|--------------|-----------|
| [Visão Geral](overview.md) | Todos | Arquitetura, stack, bounded contexts, fluxo principal |
| [Conceitos](concepts.md) | Todos | Entity, Fact, Rule, Expression, Decision, Alert |
| [Guia do Analista](user-guide.md) | Analista PLD | Como configurar regras, dry-run, ativar, gerenciar alertas |
| [Guia de Engenharia](technical-guide.md) | Engenharia | Como expandir catálogo (Entity, Fact, Rule, Resolver) |
| [API Reference](api-reference.md) | Todos | Endpoints, exemplos, códigos de erro |
| [Desenvolvimento](development.md) | Engenharia | Setup local, comandos, testes, cobertura |
| [ADRs](adr/) | Engenharia | Decisões arquiteturais |

## Ferramentas alternativas de documentação

Para equipes maiores ou quando houver necessidade de documentação com busca, versioning e deploy automático:

| Ferramenta | Quando usar | Prós | Contras |
|------------|-------------|------|---------|
| **[Docusaurus](https://docusaurus.io)** | Docs com múltiplas versões, i18n, busca | React-based, versioning built-in, search Algolia | Necessita Node.js, deploy separado |
| **[MkDocs + Material](https://squidfunk.github.io/mkdocs-material/)** | Docs técnicos rápidos | Python, Markdown puro, busca offline, tabs nativas | Menos extensível que Docusaurus |
| **[Backstage TechDocs](https://backstage.io/docs/features/techdocs/)** | Já usa Backstage como portal | Integrado ao catálogo de serviços | Depende de Backstage |
| **[Swagger UI / Redoc](https://redocly.com)** | API-first documentation | Já configurado neste projeto (localhost:8080/swagger-ui) | Apenas API, sem docs conceituais |

Para este projeto (time pequeno, mono-repo), **Markdown no repositório** é a melhor opção:
- Zero dependências adicionais
- Renderização nativa no GitHub/GitLab
- Versionado junto com o código
- Fácil de manter com IA (steering files)
