# Requirements Document

## Introduction

O **Keyword Screening** implementa uma regra de screening responsável por analisar a descrição de transações PIX e identificar a presença de palavras ou expressões restritas previamente cadastradas pela área de Compliance. O objetivo é detectar possíveis indícios de terrorismo, lavagem de dinheiro (AML), fraude, crime financeiro, sanções e outras categorias definidas pelo negócio.

A solução utiliza um cache em memória dos termos restritos para garantir alta performance (< 10ms), suporta idempotência por transação e opera de forma resiliente mesmo quando o banco de dados estiver indisponível após a carga inicial.

## Glossary

- **KeywordScreeningService**: Serviço principal responsável por orquestrar a execução da regra de Keyword Screening.
- **TextNormalizer**: Componente de domínio responsável pela normalização de texto (minúsculas, remoção de acentos, remoção de caracteres especiais, compactação de espaços).
- **KeywordMatcher**: Componente de domínio responsável por identificar termos restritos na descrição normalizada.
- **IdempotencyService**: Serviço responsável por verificar e garantir que uma transação não seja processada mais de uma vez pela mesma regra.
- **RestrictedTermsCache**: Componente responsável por manter os termos restritos ativos em memória.
- **RestrictedTermsScheduler**: Componente responsável por recarregar periodicamente os termos restritos do banco de dados para o cache.
- **RuleExecution**: Aggregate root que representa uma execução persistida da regra de Keyword Screening para uma transação específica.
- **RestrictedTerm**: Entidade que representa um termo restrito cadastrado pela área de Compliance, contendo o termo normalizado e sua categoria.
- **MatchResult**: Value object que representa um termo restrito identificado durante a análise de uma descrição.
- **ScreeningResult**: Value object que representa o resultado consolidado da execução da regra.
- **Idempotency_Key**: Chave composta no formato `KEYWORD_SCREENING:{transactionId}` utilizada para identificar unicamente uma execução da regra por transação.
- **Normalized_Description**: Descrição da transação após aplicação das regras de normalização (minúsculas, sem acentos, sem caracteres especiais, espaços compactados).
- **Category**: Classificação de risco associada a um termo restrito (ex: TERRORISM, AML, FRAUD, FINANCIAL_CRIME, SANCTIONS).

---

## Requirements

### Requirement 1: Avaliação de Descrição PIX por Termos Restritos

**User Story:** Como analista de Compliance, quero que o sistema analise automaticamente a descrição de transações PIX em busca de termos restritos, para que possíveis indícios de atividades ilícitas sejam detectados em tempo real.

#### Acceptance Criteria

1. WHEN uma requisição de avaliação é recebida com `transactionId` e `description` válidos, THE KeywordScreeningService SHALL normalizar a descrição e verificar a presença de termos restritos ativos no cache.
2. WHEN pelo menos um termo restrito ativo é encontrado na Normalized_Description, THE KeywordScreeningService SHALL retornar uma resposta com `matched=true` e a lista de todos os MatchResults identificados, incluindo todos os termos encontrados independentemente da quantidade.
3. WHEN nenhum termo restrito ativo é encontrado na Normalized_Description, THE KeywordScreeningService SHALL retornar uma resposta com `matched=false` e lista de matches vazia.
4. WHEN o campo `ruleCode` pode ser determinado, THE KeywordScreeningService SHALL incluir o campo `ruleCode` com valor `"KEYWORD_SCREENING"` na resposta de avaliação; IF o campo `ruleCode` não puder ser incluído, THEN THE KeywordScreeningService SHALL retornar a resposta parcial sem o campo `ruleCode`.

---

### Requirement 2: Normalização de Texto

**User Story:** Como desenvolvedor do sistema de screening, quero que a descrição da transação seja normalizada antes da análise, para que diferenças de formatação não impeçam a detecção de termos restritos.

#### Acceptance Criteria

1. WHEN uma descrição é submetida para normalização, THE TextNormalizer SHALL converter todos os caracteres para minúsculas.
2. WHEN uma descrição contém caracteres acentuados, THE TextNormalizer SHALL remover os acentos e substituir pelos caracteres base equivalentes.
3. WHEN uma descrição contém caracteres especiais (pontuação, símbolos), THE TextNormalizer SHALL removê-los da Normalized_Description.
4. WHEN uma descrição contém múltiplos espaços consecutivos, THE TextNormalizer SHALL compactá-los em um único espaço.
5. THE TextNormalizer SHALL aplicar todas as regras de normalização na seguinte ordem: conversão para minúsculas, remoção de acentos, remoção de caracteres especiais, compactação de espaços.
6. FOR ALL descrições válidas, normalizar e então verificar contra termos já normalizados SHALL produzir o mesmo resultado independentemente de variações de caixa, acentuação ou caracteres especiais na entrada (propriedade de round-trip da normalização).

---

### Requirement 3: Busca de Termos Restritos no Cache

**User Story:** Como analista de Compliance, quero que a busca por termos restritos seja feita exclusivamente em memória, para que o tempo de resposta seja inferior a 10ms mesmo sob alta carga.

#### Acceptance Criteria

1. WHEN a busca por termos restritos é executada, THE KeywordMatcher SHALL consultar exclusivamente o RestrictedTermsCache, sem realizar consultas ao banco de dados durante a avaliação, independentemente do tempo de resposta.
2. WHEN a busca é realizada, THE KeywordMatcher SHALL comparar a Normalized_Description com os termos normalizados armazenados no cache.
3. WHEN um termo restrito normalizado está contido na Normalized_Description, THE KeywordMatcher SHALL incluir o termo e sua Category no resultado.
4. WHILE o RestrictedTermsCache está carregado, THE KeywordScreeningService SHALL processar requisições de avaliação em tempo médio inferior a 10ms para descrições de até 140 caracteres.
5. WHEN termos inativos (`active=false`) são encontrados no cache, THE KeywordMatcher SHALL ignorá-los silenciosamente e continuar o processamento considerando apenas os termos ativos.

---

### Requirement 4: Gerenciamento do Cache de Termos Restritos

**User Story:** Como operador do sistema, quero que os termos restritos sejam mantidos em memória e atualizados periodicamente, para que novas inclusões ou desativações de termos sejam refletidas sem reinicialização da aplicação.

#### Acceptance Criteria

1. WHEN a aplicação é inicializada, THE RestrictedTermsCache SHALL carregar todos os termos restritos com `active=true` do banco de dados e armazená-los normalizados em memória.
2. WHEN o carregamento inicial é concluído, THE RestrictedTermsCache SHALL manter os termos normalizados em memória para consulta pelo KeywordMatcher.
3. WHEN o intervalo de atualização de 5 minutos é atingido, THE RestrictedTermsScheduler SHALL recarregar todos os termos restritos ativos do banco de dados e substituir o conteúdo do cache.
4. WHILE o banco de dados está indisponível após o carregamento inicial bem-sucedido, THE RestrictedTermsCache SHALL continuar servindo os termos previamente carregados em memória.
5. IF o banco de dados estiver indisponível durante a atualização periódica do cache, THEN THE RestrictedTermsScheduler SHALL manter o conteúdo atual do cache sem alteração e registrar o erro.
6. WHEN um termo restrito é armazenado no cache, THE RestrictedTermsCache SHALL armazenar o termo já na forma normalizada pelo TextNormalizer.

---

### Requirement 5: Idempotência de Execução

**User Story:** Como analista de Compliance, quero que a mesma transação não seja processada mais de uma vez pela regra de Keyword Screening, para que os resultados sejam consistentes e o processamento duplicado seja evitado.

#### Acceptance Criteria

1. WHEN uma requisição de avaliação é recebida, THE IdempotencyService SHALL verificar a existência de um RuleExecution com a Idempotency_Key `KEYWORD_SCREENING:{transactionId}` antes de executar a regra.
2. WHEN um RuleExecution existente é encontrado para a Idempotency_Key, THE IdempotencyService SHALL retornar o resultado previamente persistido sem executar a regra novamente, independentemente do tempo decorrido desde a primeira execução.
3. WHEN nenhum RuleExecution existente é encontrado para a Idempotency_Key, THE KeywordScreeningService SHALL executar a regra, persistir o RuleExecution com o resultado e retornar a resposta.
4. WHEN o resultado de uma avaliação é persistido, THE KeywordScreeningService SHALL armazenar o ScreeningResult completo associado à Idempotency_Key no banco de dados.
5. WHEN duas requisições concorrentes chegam com o mesmo `transactionId` e uma condição de corrida ocorre, THE IdempotencyService SHALL permitir que ambas as execuções prossigam e SHALL aplicar lógica de limpeza para tratar duplicatas resultantes.
6. THE RuleExecution SHALL ser identificado unicamente pela combinação de `transaction_id` e `rule_code` no banco de dados.

---

### Requirement 6: API de Avaliação

**User Story:** Como sistema consumidor, quero uma API REST para submeter transações PIX para avaliação da regra de Keyword Screening, para que o resultado do screening seja obtido de forma síncrona.

#### Acceptance Criteria

1. THE KeywordScreeningService SHALL expor o endpoint `POST /v1/rules/keyword-screening/evaluate` para recebimento de requisições de avaliação.
2. WHEN uma requisição válida é recebida no endpoint, THE KeywordScreeningService SHALL retornar uma resposta HTTP 200 com o ScreeningResult no corpo.
3. WHEN o campo `transactionId` está ausente, vazio ou contém apenas espaços em branco na requisição, THE KeywordScreeningService SHALL retornar HTTP 400 com mensagem de erro descritiva.
4. WHEN o campo `description` está ausente ou contém uma string vazia na requisição, THE KeywordScreeningService SHALL retornar HTTP 400 com mensagem de erro descritiva.
5. THE KeywordScreeningService SHALL aceitar requisições com `description` de até 140 caracteres para garantia do SLA de performance.
