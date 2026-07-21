# Decisões pendentes

Decisões identificadas na revisão de requisitos (2026-07-20) que **não pertencem à engenharia local** ou foram conscientemente adiadas. Revisitar nos marcos indicados. Nada aqui bloqueia Marco 0/1, salvo indicação contrária.

## Externas (negócio / organização)

| # | Decisão pendente | Quem decide | Necessário em | Por que importa |
|---|---|---|---|---|
| EXT-1 | Taxonomias PLD: motivos de deriva, valores de decisão, ações que exigem maker-checker, o que pode ser automático | PLD/Compliance | Antes de ligar decisão automática; idealmente antes do Marco 3 | ADR-003 marca as taxonomias como "a validar". Nós codificamos o modelo; o conteúdo aprovado é deles. |
| EXT-2 | Sistema mestre de cadastro/contas: quem produz `CustomerOnboardingStarted`, `CustomerDataChanged`, `PartyRelationshipChanged`, `AccountStatusChanged`? Existe eventing hoje? | Dono do sistema mestre | Marco 5 (onboarding real); Marco 1 usa API manual de Party | Maior dependência externa do projeto. Sem produtor, os eventos de entrada não existem. |
| EXT-3 | Sistema de contas aceita comandos idempotentes (`AccountDecisionPort`) e confirma via callback/evento? | Dono do sistema de contas | Marco 3 (efeito de decisão de conta) | CA-FR-015 separa decisão de aplicação; precisa de confirmação real do executor. |
| EXT-4 | Catálogo de fontes aprovadas: fornecedores de mídia, listas/sanções, mandados, descoberta de processos, geocodificação | Compliance/Jurídico/Produto | Marco 5 | Cada fonte exige licença, base legal e política de retenção (base regulatória). DataJud exige revisão dos termos antes de uso comercial. |
| EXT-5 | Canal COAF homologado: manual assistido, lote ou webservice | Responsável PLD + Compliance | Marco 6 | Define qual adapter de `CoafSubmissionPort` construir primeiro. |
| EXT-6 | Validação da base regulatória: retenção por classe de dado (10 anos Circular 3.978), prazo de comunicação, checklist de 9 itens | Compliance/Jurídico/DPO/Segurança | Antes de produção | `shared/regulatory-basis.md` não é parecer jurídico; checklist de validação está no fim do documento. |
| EXT-7 | Terminologia e requisitos de KYE/KYS/KYP | Negócio | Pós-MVP (MVP é KYC-only) | CA-FR-002 adia; `DueDiligenceProgram` já modela o contexto sem backend novo. |
| EXT-8 | Endereço residencial de PF no Street View: avaliação de privacidade/necessidade | DPO | Marco 5 | Exigência da base regulatória, seção Street View. |

## Internas adiadas (engenharia — revisitar no marco indicado)

| # | Questão | Revisitar em | Observação |
|---|---|---|---|
| INT-2 | Corte exato da primeira fatia: requirements ("MVP recomendado") incluem caso/decisão; handoff Marco 1 é só fundação | Planejamento do Marco 1 | Proposta: seguir os Marcos (mais finos). Confirmar na virada. |
| INT-3 | Compartilhar biblioteca de expressão entre os dois motores de regra? | Quando houver duplicação real e medida | Default: não compartilhar. Acoplamento prematuro > duplicação pequena. |
| INT-4 | Calendário de dias úteis para prazo COAF (feriados nacionais/locais) | Marco 6 | Fonte de feriados e exceções de calendário a definir. |
| INT-5 | Ferramenta de antivírus/validação de anexos | Marco 5 | Arquitetura exige validação antes de disponibilizar anexos; ferramenta aberta. |
| INT-6 | SQS standard vs FIFO (ordenação por `partyId` via MessageGroupId) | Marco 0/1, junto com o design do outbox | NFR-05 menciona ordenação por chave onde necessário. Definir por fila, não globalmente. |

## Internas resolvidas

| # | Decisão | Resolvida em | Resultado |
|---|---|---|---|
| INT-1 | Dono e ciclo de vida de `RiskProfile` | Marco 0 | `RiskProfile` é projeção versionada derivada de `Assessment`, publicada por `pld-customer-analysis` somente em mudança material consumível pelo motor transacional. O contrato `CustomerRiskProfileUpdated.v1` transporta apenas `riskLevel`, `segments`, `transactionFacts`, validade, política e referência `assessmentId`; evidências/narrativas ficam no serviço de origem. |

## Regras deste arquivo

- Toda decisão tomada aqui sai da lista e vira ADR (se material) ou nota no documento apropriado.
- Revisar este arquivo no início de cada Marco.
