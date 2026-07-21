# ADR-006 — estratégia de identidade e acesso

- Status: aceita
- Data: 2026-07-20

## Contexto

NFR-02 exige autenticação corporativa e autorização no backend com RBAC (`ANALYST`, `APPROVER`, `RULE_ADMIN`, `AUDITOR`, `SYSTEM`) desde cedo. Ao mesmo tempo:

- a empresa possui uma lib interna de autenticação baseada em JWT, **indisponível no ambiente de desenvolvimento atual**;
- o IdP corporativo definitivo não está escolhido neste pacote (o handoff classifica como decisão local não bloqueante).

## Decisão

1. **O que se adia é a integração, não a autorização.** RBAC e checagens de permissão vivem no backend desde o primeiro incremento, atrás de uma porta de identidade no módulo `identity-access`.
2. Ator e papéis são extraídos de **claims JWT** pela porta de identidade. O domínio nunca conhece o provedor.
3. **Dev:** adapter stub que aceita JWT local não assinado (ou configuração equivalente). Nunca habilitado em produção.
4. **Produção:** adapter usando a lib interna corporativa quando ela estiver disponível no ambiente. A escolha do IdP e o mapeamento de grupos → papéis serão registrados em ADR próprio nesse momento.

## Consequências

- Controllers, comandos e testes já operam com `actorId`/`actorRole` reais do ponto de vista do domínio; auditoria registra ator desde o primeiro caso de uso.
- Testes de autorização (endpoint e objeto, NFR-09) rodam contra o stub sem infra externa.
- A troca do adapter de produção não toca domínio, APIs nem frontend.
- Risco assumido: até o adapter corporativo existir, não há SSO real em nenhum ambiente. Ambientes acessíveis externamente não podem ser expostos com o stub ativo.
