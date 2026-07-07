# Product Overview

Sistema de screening de transações PIX para detecção de atividades ilícitas.

## Propósito

Identificar termos restritos em descrições de transações financeiras (PIX), detectando possíveis indícios de:
- Terrorismo
- Lavagem de dinheiro (AML)
- Fraude
- Crime financeiro
- Sanções

## Módulos

### Keyword Screening (MF09)
Primeira camada de detecção: busca por termos restritos pré-cadastrados na descrição da transação. Resposta binária (matched/not matched) com lista de termos encontrados.

### Contextual Screening
Segunda camada: usa LLM para análise contextual, reduzindo falsos positivos do Keyword Screening. Integra com API externa (coaf-analyzer) para decisão sobre comunicação ao COAF.

### Decision Engine (Rule Platform)
Plataforma de decisão para regras de PLD. Desacopla a lógica de geração de alertas da execução dos algoritmos de screening. Suporta múltiplas regras, múltiplos contextos e múltiplas fontes de fatos. Inclui:
- Catálogo de regras e configurações editáveis pelo analista
- Registry de entidades e fatos tipados
- Context Builder com Fact Resolvers especializados
- Expression Evaluator para condições e grupos lógicos
- Persistência de execuções com explicação estruturada (explainability)
- Dry-Run para teste de configurações antes da publicação

### Alert
Bounded context downstream: geração de alertas pós-decisão, com ciclo de vida (status) e consulta.

## Domínio

- **Transação**: identificada por `transactionId`, contém `description` (máx. 140 chars)
- **Termos Restritos**: palavras/expressões cadastradas por categoria, normalizadas, com flag de ativo/inativo
- **Categorias**: TERRORISM, AML, FRAUD, FINANCIAL_CRIME, SANCTIONS
- **Idempotência**: mesma transação + regra sempre retorna o resultado já persistido
- **Decisão do Analista**: após screening contextual, analista pode aprovar ou rejeitar resultado

## Idioma

Código em inglês, documentação e mensagens de validação em português brasileiro.
