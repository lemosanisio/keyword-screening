# API Reference

Contrato formal: [OpenAPI Spec](../pld-transaction-screening/src/main/resources/static/openapi/openapi.yaml)
Swagger UI: http://localhost:8080/swagger-ui/index.html

## Screening Context

| Método | Path | Descrição |
|--------|------|-----------|
| POST | `/v1/rules/keyword-screening/evaluate` | Avalia transação para keywords restritas |
| POST | `/v1/rules/contextual-screening/evaluate` | Análise contextual via LLM |
| POST | `/v1/rules/contextual-screening/decisions` | Registra decisão do analista |

## Decision Context

| Método | Path | Descrição |
|--------|------|-----------|
| GET | `/v1/decision/rules` | Listar definições de regras |
| GET | `/v1/decision/rules/{code}` | Buscar regra por código |
| GET | `/v1/decision/facts` | Listar fatos disponíveis |
| GET | `/v1/decision/entities` | Listar entidades |
| POST | `/v1/decision/rules/{code}/configurations` | Criar configuração |
| GET | `/v1/decision/rule-configurations/{id}` | Buscar configuração |
| PUT | `/v1/decision/rule-configurations/{id}` | Atualizar configuração |
| POST | `/v1/decision/rule-configurations/{id}/activate` | Ativar configuração |
| POST | `/v1/decision/rule-configurations/{id}/deactivate` | Desativar configuração |
| GET | `/v1/decision/rule-configurations/{id}/versions` | Histórico de versões |
| POST | `/v1/decision/rule-configurations/{id}/dry-run` | Simulação |
| GET | `/v1/decision/executions` | Consultar decisões |
| GET | `/v1/decision/executions/{id}` | Buscar decisão por ID |

## Alert Context

| Método | Path | Descrição |
|--------|------|-----------|
| GET | `/v1/alerts` | Consultar alertas (por transactionId ou ruleId) |
| GET | `/v1/alerts/{id}` | Buscar alerta por ID |
| PATCH | `/v1/alerts/{id}/status` | Atualizar status do alerta |

## Códigos de Erro

| HTTP | Código | Significado |
|------|--------|-------------|
| 400 | Bad Request | Campos obrigatórios ausentes ou formato inválido |
| 404 | Not Found | Recurso não encontrado |
| 409 | Conflict | Já existe configuração ativa para a mesma regra |
| 422 | Unprocessable Entity | Validação de domínio falhou |
| 500 | Internal Server Error | Erro inesperado (com correlationId) |
