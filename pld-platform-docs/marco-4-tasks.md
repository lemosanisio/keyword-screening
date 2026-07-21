# Marco 4 — workbench exploratório ponta a ponta

Status: em andamento

## Objetivo

Construir a primeira versão navegável do `pld-workbench`, usando React, TypeScript, Bun, Tailwind e shadcn/ui, para testar a experiência operacional de análise sem pretensão de virar diretamente código produtivo.

O marco deve validar a jornada completa do analista sobre o backend `pld-customer-analysis` já existente: fila, caso, comentários, decisão, pendência de aprovação e aprovação por segundo ator.

## Princípios do corte

- Priorizar aprendizado de domínio, UX, contratos e fronteiras de serviço.
- Manter código limpo, mas aceitar que modelos e persistência são descartáveis.
- Criar uma demo funcional com dados simulados/semeados.
- Ajustar BFF/read models apenas quando a tela provar a necessidade.
- Usar visual regulatório denso, próximo do shadcn/ui padrão, sem polimento cosmético excessivo.

## Decisões técnicas

- [x] React + TypeScript.
- [x] Bun como runtime/bundler/dev server.
- [x] Sem Vite.
- [x] Tailwind CSS.
- [x] shadcn/ui como base de componentes copiados para o projeto.
- [x] Atomic Design para organização do frontend.
- [x] Dev auth por seletor de ator, propagando headers `X-Actor-Id`, `X-Actor-Role` e `X-Correlation-Id`.

## Entregas

- [x] Criar `pld-workbench/`.
- [x] Configurar React/TypeScript/Bun.
- [x] Configurar Tailwind e base shadcn/ui.
- [x] Organizar componentes com Atomic Design.
- [x] Implementar shell e seletor de ator dev.
- [x] Implementar cliente HTTP para `pld-customer-analysis`.
- [x] Implementar fila de casos.
- [x] Implementar workspace do caso.
- [x] Implementar comandos operacionais: assumir, iniciar análise e devolver à fila.
- [x] Implementar comentários.
- [x] Implementar decisão de relacionamento e suspeição.
- [x] Implementar aprovação de decisão sensível por segundo ator.
- [x] Criar cenário demo/seed local.
- [x] Tratar conflito de versão `409` sem sobrescrever estado.
- [x] Rodar build/verificações possíveis.

Observação: backend validado com Gradle. Frontend validado com Bun instalado via `mise`.

## Critérios de aceite

- [ ] Com backend rodando e cenário seedado, a fila exibe ao menos um caso.
- [ ] O usuário abre o workspace a partir da fila.
- [ ] O usuário assume, inicia análise e comenta no caso.
- [ ] O usuário registra decisão não sensível e vê o caso decidido.
- [ ] O usuário registra decisão sensível e vê pendência de aprovação.
- [ ] O usuário troca para ator aprovador e aprova a decisão.
- [ ] Timeline, decisões e status refletem o fluxo.
- [x] `bun run build` passa com Bun instalado via `mise`.

## Fora deste marco

- SSO real.
- Autorização corporativa real.
- Dossiê.
- COAF.
- Evidências reais e integrações externas.
- Modelo produtivo de domínio/banco de dados.
- Polimento visual final.
