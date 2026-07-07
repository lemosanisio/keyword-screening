# ADR-008: Dry-Run Obrigatório Antes da Ativação

## Status

Aceita

## Contexto

Analistas configuram regras de PLD que impactam diretamente a geração de alertas. Uma configuração incorreta pode gerar milhares de falsos positivos ou, pior, silenciar alertas legítimos. É necessário um mecanismo de validação antes da ativação.

## Decisão

O fluxo de ativação de uma Rule Configuration exige um **dry-run prévio** na versão sendo ativada:

```
1. Criar configuração (estado: draft=true, active=false)
2. Editar/ajustar expressões (versão incrementa)
3. Executar dry-run com facts de teste (obrigatório)
4. Ativar configuração (dry-run verificado, active=true)
```

### Validação na ativação

```kotlin
val dryRunLogs = dryRunLogRepository.findByConfigurationIdAndVersion(config.id, config.currentVersion)
if (dryRunLogs.isEmpty()) {
    throw InvalidConfigurationException("Dry-run obrigatório antes da ativação")
}
```

### Propriedades do Dry-Run

- Usa o **mesmo RuleEngine** do fluxo produtivo (parity garantida)
- **Não** invoca FactResolvers (facts fornecidos manualmente)
- **Não** persiste DecisionExecution
- **Não** publica eventos
- **Não** gera alertas
- Funciona para configs draft **e** active
- Persiste DryRunLog para auditoria e como pré-requisito de ativação

## Consequências

- Analistas validam comportamento antes de impactar produção
- Reduz risco de configurações incorretas em produção
- Um passo extra no workflow (trade-off aceitável para PLD)
- DryRunLog serve como evidência de diligência para compliance
