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
Segunda camada (em desenvolvimento): usa LLM para análise contextual, reduzindo falsos positivos do Keyword Screening. Integra com API externa (coaf-analyzer) para decisão sobre comunicação ao COAF.

## Domínio

- **Transação**: identificada por `transactionId`, contém `description` (máx. 140 chars)
- **Termos Restritos**: palavras/expressões cadastradas por categoria, normalizadas, com flag de ativo/inativo
- **Categorias**: TERRORISM, AML, FRAUD, FINANCIAL_CRIME, SANCTIONS
- **Idempotência**: mesma transação + regra sempre retorna o resultado já persistido
- **Decisão do Analista**: após screening contextual, analista pode aprovar ou rejeitar resultado

## Idioma

Código em inglês, documentação e mensagens de validação em português brasileiro.
