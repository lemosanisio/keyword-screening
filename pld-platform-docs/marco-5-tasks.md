# Marco 5 — evidências simuladas e prontidão decisória

Status: concluído e validado

## Objetivo

Adicionar evidências, fatos e requisitos simulados ao protótipo para testar se o analista entende a suficiência das informações antes de decidir. O marco não implementa fontes reais, catálogo corporativo, object storage, workers ou motor de política definitivo.

## Hipóteses

- Uma matriz de requisitos ajuda o analista a entender por que o caso existe e o que falta.
- Estados de fonte diferentes precisam aparecer de forma textual e visual, sem depender só de cor.
- Fonte, evidência, fato e requisito devem ser conceitos separados.
- Requisito obrigatório não satisfeito deve bloquear decisão no backend e na UI.
- Retentativa simulada deve preservar a execução anterior e atualizar a prontidão.

## Vocabulário mínimo

Estados de execução de fonte:

```text
SUCCESS_WITH_DATA
SUCCESS_NO_RESULTS
PARTIAL
CONFLICT
UNAVAILABLE
ERROR
EXPIRED
```

Qualidade de fato:

```text
PRESENT
UNKNOWN
STALE
ERROR
```

Resultado de requisito:

```text
PENDING
SATISFIED
NOT_SATISFIED
TECHNICAL_PENDING
WAIVED
```

## Decisões do marco

- [x] Requisito obrigatório não satisfeito bloqueia decisão.
- [x] Retentativa simulada de fonte entra no marco.
- [x] Decisões não fecham mais caso automaticamente.
- [x] Caso vira `DECIDED` somente por conclusão explícita.
- [x] Fila principal mostra casos ativos, não somente `OPEN`.

## Entregas

- [x] Corrigir fila para listar casos ativos.
- [x] Adicionar ação explícita `COMPLETE_CASE`.
- [x] Implementar `POST /v1/cases/{caseId}/complete`.
- [x] Ajustar decisões para manter caso em análise ou pendente de aprovação.
- [x] Criar schema exploratório de evidência, fato, execução e requisito.
- [x] Criar matriz de evidência/requisitos por ciclo/caso.
- [x] Criar cenários demo `CLEAR`, `SOURCE_UNAVAILABLE` e `RISK_CONTEXT`.
- [x] Implementar retry simulado de fonte indisponível.
- [x] Bloquear decisões quando requisito obrigatório estiver pendente.
- [x] Expor workspace agregado com matriz e readiness.
- [x] Atualizar frontend para exibir matriz de requisitos.
- [x] Desabilitar decisão na UI quando backend indicar bloqueio.
- [x] Validar fluxo ponta a ponta no Chromium.

## Critérios de aceite

- [x] Cenário `SOURCE_UNAVAILABLE` bloqueia decisão.
- [x] Retry cria nova execução e preserva a anterior.
- [x] Retry satisfatório libera decisão.
- [x] Decisão registrada não remove o caso da fila ativa.
- [x] Conclusão explícita move caso para `DECIDED` e remove da fila ativa.
- [x] Timeline mostra coleta, retry e conclusão.
- [x] Frontend exibe matriz com estados textuais.
- [x] Backend rejeita decisão mesmo se a UI for contornada.

## Fora deste marco

- Fontes externas reais.
- Workers assíncronos reais.
- Catálogo administrável.
- Object storage e anexos.
- IA/OCR.
- Dispensa de requisito.
- Motor de política configurável.
- Dossiê e COAF.
