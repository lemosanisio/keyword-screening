# ADR-003 — deriva e dois eixos de decisão

- Status: aceita para planejamento; taxonomias finais dependem de validação PLD/Compliance
- Data: 2026-07-20

## Contexto

“Deriva” foi definida pelo negócio como o caso que precisa ir ao analista porque não há evidência suficiente para concluir com segurança. Ela não é sinônimo de suspeita.

Também há duas consequências diferentes no processo:

- aprovar, rejeitar, restringir, suspender ou encerrar uma conta/relacionamento;
- concluir se há suspeição e se deve haver comunicação ao COAF.

Colapsar tudo em `APPROVED/REJECTED/ALERT` cria inferências regulatórias erradas, dificulta explicar automação e acopla ações que podem divergir.

## Decisão

### 1. Deriva é uma rota

Deriva será modelada como rota `DERIVED_TO_ANALYST` acompanhada de motivos e requisitos não satisfeitos. Não será estado de risco, tipo de crime ou decisão final.

Motivos comuns:

```text
INSUFFICIENT_EVIDENCE
CONFLICTING_EVIDENCE
LOW_IDENTITY_CONFIDENCE
SOURCE_UNAVAILABLE
STALE_EVIDENCE
POLICY_REQUIRES_REVIEW
HIGH_IMPACT_ACTION
UNCLASSIFIED_SITUATION
```

Não usar “100% de certeza” como condição de software. A política define evidências obrigatórias, qualidade, confiança/limiar e ações permitidas.

### 2. Pendência técnica é explícita

Uma fonte indisponível/erro pode seguir `TECHNICAL_RETRY`/`TECHNICAL_PENDING`. A política decide quando esgotar retry e derivar. O sistema não deve concluir “nada encontrado”.

### 3. Dois eixos de decisão

`AccountDecision` trata relacionamento; `SuspicionDecision` trata suspeição/COAF. Ambos podem existir no mesmo caso/ciclo e apontar para evidências comuns, mas não se determinam automaticamente.

### 4. Segundo aprovador é rota/política

Mesmo com a mesma equipe, ações de alto impacto podem usar `MANDATORY_SECOND_APPROVAL`. Mesa/SLA não faz parte do modelo.

## Exemplos válidos

| Relacionamento | Suspeição | Interpretação possível |
|---|---|---|
| `APPROVE_WITH_CONDITIONS` | `KEEP_MONITORING` | onboarding permitido com revisão mais próxima |
| `REJECT` | `NO_SUSPICION` | apetite/política de relacionamento sem conclusão suspeita |
| `MAINTAIN` | `COMMUNICATE_TO_COAF` | relacionamento mantido enquanto comunicação segue em sigilo, conforme decisão autorizada |
| `TERMINATE_RELATIONSHIP` | `COMMUNICATE_TO_COAF` | ambas as ações decididas e rastreadas separadamente |
| `REQUEST_INFORMATION` | `INCONCLUSIVE` | evidência insuficiente; caso continua aberto |

Os exemplos demonstram independência do modelo, não prescrevem conduta operacional.

## Invariantes

- Deriva sempre explica por que a automação parou.
- Uma decisão automática precisa de política vigente e suficiência registrada.
- Uma fonte com erro não pode sustentar “ausência”.
- Comunicação nasce somente de `COMMUNICATE_TO_COAF` autorizada.
- Decisão de conta gera efeito externo separado e aguarda confirmação.
- Correção/retificação sucede a decisão anterior; não a altera.

## Consequências

- UI e APIs possuem campos separados, reduzindo ambiguidade.
- Casos podem priorizar deriva sem rotulá-la como suspeita.
- Métricas distinguem qualidade da automação, indisponibilidade técnica e volume suspeito.
- Políticas ficam mais explícitas, porém exigem taxonomia e validação com Compliance.

