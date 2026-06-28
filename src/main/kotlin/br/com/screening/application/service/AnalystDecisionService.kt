package br.com.screening.application.service

import br.com.screening.application.usecase.AnalystDecisionResultDto
import br.com.screening.application.usecase.RegisterAnalystDecisionCommand
import br.com.screening.application.usecase.RegisterAnalystDecisionUseCase
import br.com.screening.domain.exception.AuditNotFoundException
import br.com.screening.domain.exception.InvalidClassificationException
import br.com.screening.domain.model.Classification
import br.com.screening.domain.model.HistoricalDecision
import br.com.screening.domain.port.ContextualScreeningAuditRepository
import br.com.screening.domain.port.HistoricalDecisionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AnalystDecisionService(
    private val auditRepository: ContextualScreeningAuditRepository,
    private val historicalDecisionRepository: HistoricalDecisionRepository
) : RegisterAnalystDecisionUseCase {

    @Transactional
    override fun execute(command: RegisterAnalystDecisionCommand): AnalystDecisionResultDto {
        // 1. Validar classificação
        val decision = try {
            Classification.valueOf(command.analystDecision)
        } catch (e: IllegalArgumentException) {
            throw InvalidClassificationException(command.analystDecision)
        }

        // 2. Buscar audit existente
        val audit = auditRepository.findByTransactionIdAndRuleId(command.transactionId, command.ruleId)
            ?: throw AuditNotFoundException(command.transactionId, command.ruleId)

        // 3. Persistir HistoricalDecision para RAG futuro
        val historicalDecision = HistoricalDecision(
            keyword = audit.keyword,
            description = audit.keyword,
            analystDecision = decision,
            createdAt = Instant.now()
        )
        historicalDecisionRepository.save(historicalDecision)

        // 4. Atualizar analystDecision no audit
        auditRepository.updateAnalystDecision(command.transactionId, command.ruleId, decision)

        return AnalystDecisionResultDto(
            transactionId = command.transactionId,
            ruleId = command.ruleId,
            analystDecision = decision.name,
            registeredAt = Instant.now().toString()
        )
    }
}
