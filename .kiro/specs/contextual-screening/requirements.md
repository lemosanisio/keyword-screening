# Requirements Document

## Introduction

O **Contextual Screening** é uma camada secundária de triagem responsável por reduzir os falsos positivos gerados pelo Keyword Screening. Após a detecção de um ou mais termos monitorados na descrição de uma transação, o Contextual Screening avalia semanticamente o contexto em que o termo foi empregado, utilizando decisões históricas de analistas (via RAG — Retrieval Augmented Generation) e um modelo de linguagem (LLM) compatível com OpenAPI.

O serviço classifica a transação em três categorias — **FALSE_POSITIVE**, **SUSPICIOUS** ou **UNCERTAIN** — e determina a rota de encaminhamento: fechamento automático ou encaminhamento para revisão manual do analista. O feedback dos analistas é persistido e reutilizado em avaliações futuras, criando um ciclo contínuo de aprendizado sem necessidade de retreinamento do modelo.

O Contextual Screening **não substitui** a investigação humana para casos suspeitos ou inconclusivos.

---

## Glossary

- **Contextual_Screening**: Sistema responsável pela classificação contextual de transações que sofreram match de palavras-chave no Keyword Screening.
- **Keyword_Screening**: Sistema pré-existente que realiza correspondência exata de termos em descrições de transações PIX.
- **LLM_Classifier**: Adaptador que integra o sistema com um provedor LLM compatível com OpenAPI para realizar a classificação contextual.
- **Historical_Decision_Retriever**: Componente responsável por recuperar decisões históricas de analistas associadas à palavra-chave detectada.
- **Prompt_Builder**: Componente responsável por construir o prompt contextualizado para envio ao LLM.
- **Audit_Repository**: Porta de persistência responsável por armazenar todos os dados de auditoria de cada avaliação.
- **Analyst_Feedback_Repository**: Porta de persistência responsável por armazenar o feedback dos analistas após revisão manual.
- **Evaluate_Contextual_Screening_Use_Case**: Caso de uso principal que orquestra o fluxo de avaliação contextual.
- **Register_Analyst_Decision_Use_Case**: Caso de uso que registra a decisão final do analista e a disponibiliza para retrieval futuro.
- **ContextualScreeningRequest**: Objeto de entrada contendo `transactionId`, `ruleId`, `description` e `matchedKeyword`.
- **ContextualScreeningResult**: Objeto de saída contendo `classification`, `confidence`, `reason` e `requiresAnalystReview`.
- **HistoricalDecision**: Registro de uma decisão passada de analista contendo `keyword`, `description`, `analystDecision` e `createdAt`.
- **Classification**: Enumeração com os valores `FALSE_POSITIVE`, `SUSPICIOUS` e `UNCERTAIN`.
- **RoutingDecision**: Decisão de encaminhamento derivada da classificação e da pontuação de confiança.
- **Confidence_Score**: Pontuação de confiança entre 0.00 e 1.00 fornecida pelo LLM junto à classificação.
- **Auto_Close_Threshold**: Limiar configurável de confiança acima do qual transações classificadas como `FALSE_POSITIVE` podem ser encerradas automaticamente. Valor padrão: 0.95.

---

## Requirements

---

### Requirement 1: Ativação pelo Keyword Screening

**User Story:** Como sistema de compliance, quero que o Contextual Screening seja acionado exclusivamente quando o Keyword Screening detectar ao menos uma correspondência de termo, para que avaliações contextuais desnecessárias não sejam executadas.

#### Acceptance Criteria

1. WHEN o Keyword_Screening produz ao menos uma correspondência de termo para uma transação, THE Contextual_Screening SHALL iniciar a avaliação contextual para essa transação.
2. WHEN o Keyword_Screening não produz nenhuma correspondência de termo para uma transação, THE Contextual_Screening SHALL permanecer inativo para essa transação.
3. THE Contextual_Screening SHALL operar de forma independente da lógica interna de avaliação do Keyword_Screening, sem acessar ou modificar o estado desse sistema.
4. WHEN o Contextual_Screening recebe uma `ContextualScreeningRequest` com `transactionId` ausente, vazio ou contendo apenas espaços, THE Contextual_Screening SHALL retornar um erro de validação com descrição do campo inválido, sem verificar se o `transactionId` corresponde a uma transação existente no banco de dados.
5. WHEN o Contextual_Screening recebe uma `ContextualScreeningRequest` com `description` ausente ou vazia, THE Contextual_Screening SHALL retornar um erro de validação com descrição do campo inválido.
6. WHEN o Contextual_Screening recebe uma `ContextualScreeningRequest` com `matchedKeyword` ausente ou vazio, THE Contextual_Screening SHALL retornar um erro de validação com descrição do campo inválido.

---

### Requirement 2: Recuperação de Decisões Históricas

**User Story:** Como analista de compliance, quero que o sistema utilize decisões históricas de revisões anteriores ao classificar uma transação, para que o contexto acumulado de avaliações passadas melhore a precisão da classificação.

#### Acceptance Criteria

1. WHEN o Contextual_Screening inicia a avaliação de uma transação, THE Historical_Decision_Retriever SHALL recuperar as decisões históricas de analistas associadas à `matchedKeyword` informada na requisição.
2. THE Historical_Decision_Retriever SHALL retornar as decisões históricas em ordem decrescente de `createdAt`, priorizando as decisões mais recentes.
3. WHERE nenhuma decisão histórica estiver disponível para a `matchedKeyword`, THE Historical_Decision_Retriever SHALL retornar uma lista vazia sem lançar exceção.
4. THE Historical_Decision_Retriever SHALL recuperar apenas decisões históricas cujo campo `keyword` seja igual à `matchedKeyword` informada.
5. THE Contextual_Screening SHALL prosseguir com a avaliação mesmo quando nenhuma decisão histórica for encontrada, utilizando apenas a descrição da transação e as instruções de classificação no prompt.

---

### Requirement 3: Construção do Prompt

**User Story:** Como sistema de compliance, quero que o prompt enviado ao LLM contenha todas as informações relevantes para a classificação, para que o modelo produza resultados precisos e justificados.

#### Acceptance Criteria

1. THE Prompt_Builder SHALL incluir no prompt a `description` da transação somente após todos os componentes do prompt estarem prontos (matchedKeyword e decisões históricas recuperadas), garantindo que o prompt seja construído de forma completa e coerente.
2. THE Prompt_Builder SHALL incluir no prompt a `matchedKeyword` recebida na `ContextualScreeningRequest`.
3. THE Prompt_Builder SHALL incluir no prompt as decisões históricas recuperadas pelo `Historical_Decision_Retriever`, formatadas como exemplos de few-shot learning.
4. THE Prompt_Builder SHALL incluir no prompt instruções explícitas de classificação que delimitem os valores válidos de resposta: `FALSE_POSITIVE`, `SUSPICIOUS` e `UNCERTAIN`.
5. THE Prompt_Builder SHALL incluir no prompt instruções para que o LLM forneça uma justificativa legível por humanos e uma pontuação de confiança entre 0.00 e 1.00.
6. WHEN nenhuma decisão histórica estiver disponível, THE Prompt_Builder SHALL construir o prompt sem a seção de exemplos de few-shot learning, mantendo obrigatoriamente as seções de descrição da transação, matchedKeyword, instruções de classificação e instruções de justificativa e pontuação de confiança.

---

### Requirement 4: Invocação do LLM

**User Story:** Como sistema de compliance, quero que o LLM seja invocado através de uma interface isolada, para que o provedor de LLM possa ser substituído no futuro sem impacto na lógica de domínio.

#### Acceptance Criteria

1. THE LLM_Classifier SHALL invocar o provedor LLM utilizando a URL base configurável `http://localhost:8080/api/coaf/analisar`, encapsulando a chamada atrás da porta `LlmClassifier`.
2. THE LLM_Classifier SHALL enviar o prompt construído pelo `Prompt_Builder` como o campo `texto` do corpo da requisição ao provedor LLM.
3. THE LLM_Classifier SHALL interpretar a resposta do provedor LLM e extrair os campos `classification`, `confidence` e `reason` para compor o `ContextualScreeningResult`; respostas do LLM que indiquem incerteza semântica mas sejam tecnicamente válidas SHALL ser interpretadas normalmente pelo fluxo de classificação (Requisito 5), sem tratamento especial neste critério.
4. IF o provedor LLM retornar uma resposta de erro HTTP, timeout ou uma resposta com estrutura JSON inválida, THEN THE LLM_Classifier SHALL retornar uma classificação `UNCERTAIN` com `confidence` 0.00 e `reason` descritivo do erro técnico ocorrido.
5. IF o provedor LLM não responder dentro do tempo máximo configurado, THEN THE LLM_Classifier SHALL encerrar a chamada e retornar uma classificação `UNCERTAIN` com `confidence` 0.00.
6. THE LLM_Classifier SHALL persistir a requisição e a resposta bruta do LLM no `Audit_Repository` antes de retornar o resultado ao caso de uso.
7. THE Contextual_Screening SHALL suportar a substituição do provedor LLM por meio de alteração exclusiva nos adaptadores, sem modificação de classes de domínio ou casos de uso.

---

### Requirement 5: Classificação da Transação

**User Story:** Como analista de compliance, quero que o sistema produza uma classificação clara e padronizada para cada transação avaliada, para que a triagem e o encaminhamento sejam consistentes e auditáveis.

#### Acceptance Criteria

1. THE Contextual_Screening SHALL classificar toda transação avaliada em exatamente um dos três valores: `FALSE_POSITIVE`, `SUSPICIOUS` ou `UNCERTAIN`.
2. THE Contextual_Screening SHALL retornar uma pontuação de confiança (`Confidence_Score`) entre 0.00 e 1.00 para toda classificação produzida; IF o provedor LLM retornar um valor fora desse intervalo, THE Contextual_Screening SHALL permitir que o valor transite temporariamente antes de aplicar a normalização definida no critério 5.
3. THE Contextual_Screening SHALL retornar uma justificativa legível por humanos (`reason`) para toda classificação produzida.
4. IF o provedor LLM retornar um valor de classificação fora do conjunto `{FALSE_POSITIVE, SUSPICIOUS, UNCERTAIN}`, THEN THE Contextual_Screening SHALL substituir a classificação por `UNCERTAIN`.
5. IF o provedor LLM retornar uma pontuação de confiança fora do intervalo [0.00, 1.00], THEN THE Contextual_Screening SHALL normalizar o valor para o limite mais próximo (0.00 se menor que 0.00, ou 1.00 se maior que 1.00) antes de incluí-lo no `ContextualScreeningResult`.

---

### Requirement 6: Decisão de Encaminhamento (Routing)

**User Story:** Como sistema de compliance, quero que a decisão de encaminhamento seja determinada automaticamente a partir da classificação e da confiança, para que transações sejam roteadas corretamente sem intervenção manual desnecessária.

#### Acceptance Criteria

1. WHEN a classificação da transação for `SUSPICIOUS`, THE Contextual_Screening SHALL definir `requiresAnalystReview` como `true` no `ContextualScreeningResult`, sem exceção e independentemente de qualquer configuração.
2. WHEN a classificação da transação for `UNCERTAIN`, THE Contextual_Screening SHALL definir `requiresAnalystReview` como `true` no `ContextualScreeningResult`.
3. WHEN a classificação da transação for `FALSE_POSITIVE` e o `Confidence_Score` for maior ou igual ao `Auto_Close_Threshold`, THE Contextual_Screening SHALL definir `requiresAnalystReview` como `false` no `ContextualScreeningResult`.
4. WHEN a classificação da transação for `FALSE_POSITIVE` e o `Confidence_Score` for menor que o `Auto_Close_Threshold`, THE Contextual_Screening SHALL definir `requiresAnalystReview` como `true` no `ContextualScreeningResult`.
5. THE Contextual_Screening SHALL utilizar 0.95 como valor padrão do `Auto_Close_Threshold` quando nenhum valor estiver configurado.
6. WHERE o `Auto_Close_Threshold` estiver configurado com um valor entre 0.00 e 1.00, THE Contextual_Screening SHALL utilizar o valor configurado como limiar para encerramento automático.

---

### Requirement 7: Persistência para Auditoria

**User Story:** Como analista de compliance, quero que todos os dados relevantes de cada avaliação sejam persistidos de forma íntegra e imutável, para que as decisões do sistema sejam rastreáveis, explicáveis e auditáveis por órgãos reguladores.

#### Acceptance Criteria

1. WHEN o Contextual_Screening conclui a avaliação de uma transação, THE Audit_Repository SHALL persistir um registro contendo: `transactionId`, `ruleId`, `keyword`, `prompt`, `modelResponse`, `llmClassification`, `llmConfidence`, `finalClassification`, `analystDecision` (inicialmente nulo) e `createdAt`.
2. THE Audit_Repository SHALL garantir que cada registro de auditoria seja associado de forma única ao par (`transactionId`, `ruleId`).
3. THE Contextual_Screening SHALL persistir o registro de auditoria independentemente do valor da classificação produzida.
4. THE Contextual_Screening SHALL persistir o registro de auditoria mesmo quando a classificação resultante for `UNCERTAIN` por falha do LLM, registrando a resposta bruta do erro como `modelResponse`.
5. THE Audit_Repository SHALL registrar o `createdAt` com a data e hora UTC no momento da persistência.

---

### Requirement 8: Registro de Feedback do Analista

**User Story:** Como analista de compliance, quero registrar minha decisão final após revisar uma transação, para que ela seja incorporada às decisões históricas e melhore a precisão de avaliações futuras.

#### Acceptance Criteria

1. WHEN um analista submete sua decisão final para uma transação previamente avaliada, THE Register_Analyst_Decision_Use_Case SHALL persistir a decisão como um `HistoricalDecision` contendo `keyword`, `description`, `analystDecision` e `createdAt`.
2. WHEN um `HistoricalDecision` é persistido com sucesso, THE Analyst_Feedback_Repository SHALL torná-lo disponível para recuperação pelo `Historical_Decision_Retriever` em avaliações futuras.
3. THE Audit_Repository SHALL atualizar o campo `analystDecision` do registro de auditoria correspondente ao par (`transactionId`, `ruleId`) com a decisão do analista, independentemente do sucesso ou falha da persistência do `HistoricalDecision` associado.
4. WHEN o analista submete uma decisão para um `transactionId` inexistente no `Audit_Repository`, THE Register_Analyst_Decision_Use_Case SHALL retornar um erro descritivo indicando que a transação não foi encontrada.
5. IF o analista submeter uma decisão com valor fora do conjunto `{FALSE_POSITIVE, SUSPICIOUS, UNCERTAIN}`, THEN THE Register_Analyst_Decision_Use_Case SHALL retornar um erro de validação com descrição dos valores permitidos.

---

### Requirement 9: Idempotência

**User Story:** Como sistema de compliance, quero que avaliações repetidas para a mesma transação retornem o mesmo resultado sem duplicar processamento ou registros, para garantir consistência em cenários de retentativa.

#### Acceptance Criteria

1. WHEN o Contextual_Screening recebe uma `ContextualScreeningRequest` cujo `transactionId` já possui um registro de auditoria persistido no `Audit_Repository`, THE Contextual_Screening SHALL retornar o resultado previamente persistido sem invocar novamente o `LLM_Classifier`.
2. THE Contextual_Screening SHALL produzir resultados idênticos para duas chamadas consecutivas com o mesmo par (`transactionId`, `ruleId`).
3. THE Audit_Repository SHALL conter exatamente um registro de auditoria por par (`transactionId`, `ruleId`), mesmo em cenário de chamadas concorrentes.

---

### Requirement 10: Resiliência e Fallback

**User Story:** Como sistema de compliance, quero que falhas no serviço de LLM não bloqueiem o processamento de transações, para que o fluxo de compliance continue operando mesmo em cenários de degradação de serviços externos.

#### Acceptance Criteria

1. IF o provedor LLM retornar erro de comunicação ou timeout, THEN THE Contextual_Screening SHALL retornar um `ContextualScreeningResult` com `classification` igual a `UNCERTAIN`, `confidence` igual a 0.00 e `requiresAnalystReview` igual a `true`.
2. THE Contextual_Screening SHALL processar e retornar o resultado de fallback ao chamador sem propagar a exceção do LLM.
3. IF o `Historical_Decision_Retriever` falhar ao acessar o repositório de decisões históricas, THEN THE Contextual_Screening SHALL prosseguir com a avaliação utilizando uma lista vazia de decisões históricas; esta falha é tratada de forma independente de falhas do LLM — se ambos falharem simultaneamente, cada falha é tratada pelo seu respectivo mecanismo de fallback.
4. THE Contextual_Screening SHALL registrar no `Audit_Repository` o erro ocorrido durante a invocação do LLM como parte do campo `modelResponse`.

---

### Requirement 11: Performance

**User Story:** Como sistema de compliance, quero que a avaliação contextual seja concluída dentro de um tempo aceitável, para que a experiência do analista e a cadência de processamento de transações não sejam prejudicadas.

#### Acceptance Criteria

1. WHILE o serviço LLM estiver respondendo dentro dos limites normais de operação, THE Contextual_Screening SHALL concluir a avaliação e retornar o resultado em no máximo 3 segundos para 95% das requisições (P95 <= 3s).
2. THE Contextual_Screening SHALL definir um timeout máximo configurável para a chamada ao provedor LLM, utilizando 30 segundos como valor padrão.

---

### Requirement 12: Propriedades de Corretude para Property-Based Testing

**User Story:** Como engenheiro de qualidade, quero que as propriedades formais do sistema sejam documentadas, para que testes baseados em propriedades (PBT) possam verificar invariantes e comportamentos de corretude em toda a entrada válida do domínio.

#### Acceptance Criteria

1. FOR ALL `ContextualScreeningResult` produzidos pelo Contextual_Screening, THE Contextual_Screening SHALL garantir que `classification` pertence ao conjunto `{FALSE_POSITIVE, SUSPICIOUS, UNCERTAIN}` — invariante de classificação.
2. FOR ALL `ContextualScreeningResult` produzidos pelo Contextual_Screening, THE Contextual_Screening SHALL garantir que `confidence` está no intervalo fechado [0.00, 1.00] — invariante de confiança.
3. FOR ALL `ContextualScreeningResult` produzidos pelo Contextual_Screening, WHEN `classification` é `SUSPICIOUS` ou `UNCERTAIN`, THE Contextual_Screening SHALL garantir que `requiresAnalystReview` é `true` — invariante de roteamento para revisão.
4. FOR ALL `ContextualScreeningResult` com `classification` igual a `FALSE_POSITIVE` e `confidence` maior ou igual ao `Auto_Close_Threshold`, THE Contextual_Screening SHALL garantir que `requiresAnalystReview` é `false` — invariante de roteamento para fechamento automático.
5. FOR ALL `ContextualScreeningResult` com `classification` igual a `FALSE_POSITIVE` e `confidence` menor que o `Auto_Close_Threshold`, THE Contextual_Screening SHALL garantir que `requiresAnalystReview` é `true` — invariante de roteamento conservador.
6. FOR ALL pares de chamadas consecutivas com o mesmo `transactionId` e `ruleId`, THE Contextual_Screening SHALL garantir que ambas retornam resultados com `classification`, `confidence` e `reason` idênticos — idempotência de avaliação.
7. FOR ALL `HistoricalDecision` persistidos pelo `Register_Analyst_Decision_Use_Case`, THE Analyst_Feedback_Repository SHALL garantir que o registro pode ser recuperado pelo `Historical_Decision_Retriever` utilizando o mesmo `keyword` — round-trip de persistência de feedback.
8. FOR ALL registros de auditoria persistidos pelo `Audit_Repository`, THE Audit_Repository SHALL garantir que os campos `transactionId`, `ruleId`, `keyword`, `prompt`, `llmClassification`, `llmConfidence`, `finalClassification` e `createdAt` são não nulos — completude do registro de auditoria.
9. FOR ALL `ContextualScreeningRequest` com `transactionId` não vazio, `description` não vazia e `matchedKeyword` não vazio, THE Contextual_Screening SHALL retornar um `ContextualScreeningResult` válido com `classification` não nulo e `requiresAnalystReview` definido — completude do resultado.
10. WHEN o LLM retorna uma resposta com `classification` fora do conjunto `{FALSE_POSITIVE, SUSPICIOUS, UNCERTAIN}`, THE Contextual_Screening SHALL garantir que o `ContextualScreeningResult` final contém `classification` igual a `UNCERTAIN` — robustez contra respostas inválidas do LLM.
