# Requisitos não funcionais

Os itens são comuns aos dois backends e ao frontend, salvo indicação. Metas numéricas devem ser calibradas com o volume real; a ausência delas não altera as fronteiras arquiteturais.

## NFR-01 — auditabilidade regulatória

- Toda decisão relevante registra ator, papel, horários, política/regra e versão, entradas consideradas, evidências, justificativa e correlação.
- Registros históricos são append-only. Correção cria nova versão, evento compensatório ou retificação.
- A reconstrução “como se sabia naquela data” não depende do estado atual de cadastros ou regras.
- Relógios de negócio usam UTC; a UI pode exibir o fuso do usuário.
- Exportação do dossiê tem identificador, versão, data de geração e hash de integridade.

Critério: selecionar uma decisão histórica e reconstruir, por IDs persistidos, o snapshot e a política usados sem consultar dados mutáveis atuais.

## NFR-02 — segurança e privacidade

- Autenticação corporativa e autorização no backend; ocultar botão no frontend não é controle.
- RBAC mínimo: `ANALYST`, `APPROVER`, `RULE_ADMIN`, `AUDITOR`, com permissões finas para COAF e dados sensíveis.
- Segregação maker-checker é configurável por tipo de ação; uma mesma pessoa não aprova sua própria decisão quando a política exigir segundo aprovador.
- Criptografia em trânsito e repouso; gestão de segredos fora do repositório.
- Mascarar CPF/CNPJ, nomes, endereços, narrativas e payloads em logs, traces e métricas.
- Acesso à comunicação COAF deve ser restrito e não gerar notificações visíveis a canais de atendimento/cliente.
- Consultas, exportações e visualização de evidência sensível geram auditoria de acesso.
- Aplicar minimização: eventos e projeções recebem só os dados necessários à finalidade.

## NFR-03 — retenção e descarte

- Classes de dados têm política configurável de retenção, legal hold, descarte e anonimização quando permitida.
- O desenho suporta retenção mínima regulatória aplicável aos registros de PLD sem depender da retenção de logs técnicos.
- Evidência em object storage usa versionamento, integridade e controle de acesso; metadados no banco apontam para a versão exata.
- O descarte é rastreado e não deixa referências quebradas silenciosamente.
- Backups respeitam retenção, restauração testada e chaves de criptografia gerenciadas.

## NFR-04 — disponibilidade e resiliência

- A indisponibilidade de fonte externa não derruba toda análise; a execução fica `UNAVAILABLE`/`ERROR`, com retry e circuito.
- Retry usa backoff e jitter e só ocorre em operações idempotentes.
- Dead-letter/quarentena possui motivo, payload protegido, ferramenta de inspeção e replay controlado.
- Outbox/inbox evita perda e duplicidade de efeitos.
- Nenhuma dependência remota síncrona existe no hot path de avaliação de transações.
- Falha da UI não impede ingestão, avaliação, publicação ou timers regulatórios.
- Runbooks cobrem backlog de eventos, fonte indisponível, divergência de projeção e falha de envio COAF.

## NFR-05 — desempenho e escala

- Dimensionar o motor transacional por throughput de pico e latência do caminho crítico, medidos com dados sintéticos representativos.
- Particionamento preserva ordenação por chave apenas onde necessário, tipicamente `accountId` ou `partyId`.
- Consultas do Workbench usam read models paginados; não carregam dossiê/evidências completas na fila.
- Relatórios e backtests pesados executam como jobs assíncronos com progresso e cancelamento seguro.
- Payloads grandes ficam fora do broker; eventos transportam referência e metadados.
- Definir SLOs a partir do baseline atual para ingestão, atraso de projeção, resposta da UI e prazo de jobs. Não criar SLAs distintos por “mesa”, pois o processo atual usa a mesma equipe.

## NFR-06 — observabilidade

- OpenTelemetry ou padrão equivalente em HTTP, mensageria, jobs e integrações externas.
- `correlationId`, `causationId`, `eventId`, `evaluationId`, `analysisCycleId` e `caseId` aparecem como campos estruturados quando aplicáveis, sem PII.
- Métricas mínimas: throughput, latência, taxa de erro, backlog, idade do evento mais antigo, retries, DLQ, casos criados, deriva por motivo, fontes por status e divergência de projeção.
- Alertas distinguem falha técnica de aumento legítimo de sinais.
- Logs têm retenção operacional; eventos/timeline têm retenção regulatória. Um não substitui o outro.

## NFR-07 — explicabilidade e uso de IA

- Toda regra determinística retorna código, versão, fatos, operadores e resultado.
- Classificadores estatísticos/LLM registram modelo, versão, prompt/template quando aplicável, parâmetros, entrada referenciada, saída bruta protegida e normalização.
- IA pode extrair, resumir, classificar e priorizar; não deve condenar, rejeitar, encerrar conta ou comunicar ao COAF de forma autônoma.
- Resultado de IA carrega confiança e política de fallback. Baixa confiança deriva.
- Avaliações de qualidade usam conjunto versionado, revisão humana e métricas por classe; dados reais são anonimizados quando usados fora do ambiente autorizado.
- Prompt injection e conteúdo de notícia são tratados como dados não confiáveis, nunca como instruções.

## NFR-08 — interoperabilidade e evolução

- OpenAPI para HTTP e schema versionado para eventos.
- IDs opacos e estáveis; não embutir significado que impeça migração.
- Mudanças incompatíveis convivem durante uma janela mensurada.
- Migrations são forward-only em produção, testadas em cópia representativa e acompanhadas de estratégia de rollback lógico.
- Feature flags separam deploy de ativação, com owner e data de expiração.

## NFR-09 — qualidade e testes

Pirâmide mínima:

- testes de domínio para estados, políticas, invariantes e autorização;
- property-based tests para avaliador de expressões e normalização quando útil;
- testes de repositório/migration;
- testes de contrato HTTP e evento;
- testes de integração com broker/banco reais via containers;
- testes end-to-end dos fluxos críticos;
- testes de replay, duplicidade, reorder e poison message;
- testes de carga do motor e das projeções;
- testes de segurança para IDOR, elevação de privilégio, exportação e vazamento em logs;
- testes de acessibilidade do frontend.

Fixtures não contêm PII real.

## NFR-10 — experiência operacional

- A fila apresenta dados suficientes para priorização sem abrir cada caso.
- Qualquer estado parcial, desatualizado ou indisponível é visível; a UI não preenche lacuna com “não encontrado”.
- Ações destrutivas ou regulatórias mostram consequência e pedem justificativa; confirmação genérica não basta.
- Conflito de edição usa versão/ETag e nunca sobrescreve silenciosamente trabalho de outro analista.
- Autosave de rascunho não cria decisão; submissão final é um comando explícito.
- Exportações têm marcação de confidencialidade e auditoria.

