package br.com.evaluation.infrastructure

import br.com.decision.domain.model.vo.FactValue
import br.com.evaluation.domain.TransactionEvaluation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface TransactionEvaluationJpaRepository : JpaRepository<TransactionEvaluationEntity, String> {
    fun findByTransactionIdAndTransactionVersionAndRulesetVersionAndPurpose(
        transactionId: String,
        transactionVersion: Int,
        rulesetVersion: String,
        purpose: String,
    ): TransactionEvaluationEntity?

    fun findBySourceSystemAndExternalTransactionIdAndTransactionVersionAndRulesetVersionAndPurpose(
        sourceSystem: String,
        externalTransactionId: String,
        transactionVersion: Int,
        rulesetVersion: String,
        purpose: String,
    ): TransactionEvaluationEntity?

    fun findByEvaluationRequestIdAndPurpose(evaluationRequestId: String, purpose: String): TransactionEvaluationEntity?
    fun findByInputEventIdAndPurpose(inputEventId: String, purpose: String): TransactionEvaluationEntity?
}

@Repository
class TransactionEvaluationRepository(
    private val repository: TransactionEvaluationJpaRepository,
    private val jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate,
) {
    fun save(evaluation: TransactionEvaluation): TransactionEvaluation {
        repository.saveAndFlush(
            TransactionEvaluationEntity(
                evaluationId = evaluation.evaluationId,
                decisionExecutionId = evaluation.decisionExecutionId,
                transactionId = evaluation.transactionId,
                sourceSystem = evaluation.sourceSystem,
                externalTransactionId = evaluation.externalTransactionId,
                transactionVersion = evaluation.transactionVersion,
                purpose = evaluation.purpose,
                evaluationRequestId = evaluation.evaluationRequestId,
                inputEventId = evaluation.inputEventId,
                inputEventSchemaVersion = evaluation.inputEventSchemaVersion,
                snapshot = evaluation.snapshot,
                snapshotRef = evaluation.snapshotRef,
                snapshotFormatVersion = evaluation.snapshotFormatVersion,
                snapshotHash = evaluation.snapshotHash,
                rulesetVersion = evaluation.rulesetVersion,
                riskContext = mapOf(
                    "source" to evaluation.riskContext.source,
                    "quality" to evaluation.riskContext.quality,
                    "reasonCode" to evaluation.riskContext.reasonCode,
                    "riskProfileVersion" to evaluation.riskContext.riskProfileVersion,
                ).filterValues { it != null },
                facts = evaluation.facts.map { fact ->
                    mapOf(
                        "code" to fact.name.value,
                        "quality" to fact.quality.name,
                        "source" to fact.source,
                        "reasonCode" to fact.reasonCode,
                        "value" to fact.value?.let(::factValue),
                    ).filterValues { it != null }
                },
                rulesExecuted = evaluation.rulesExecuted.map(::ruleReference),
                rulesTriggered = evaluation.rulesTriggered.map(::ruleReference),
                executionStatus = evaluation.executionStatus.name,
                evaluationOutcome = evaluation.evaluationOutcome?.name,
                reviewRequired = evaluation.reviewRequired,
                recommendedRoute = evaluation.recommendedRoute?.name,
                explanation = evaluation.explanation,
                partyId = evaluation.partyId,
                correlationId = evaluation.correlationId,
                causationId = evaluation.causationId,
                evaluatedAt = evaluation.evaluatedAt,
                failureStage = evaluation.failureStage?.name,
                failureCode = evaluation.failureCode,
            ),
        )
        evaluation.executions.forEach { link ->
            jdbcTemplate.update(
                """
                    INSERT INTO transaction_evaluation_execution (evaluation_id, decision_execution_id, rule_code)
                    VALUES (?, ?, ?)
                """.trimIndent(),
                evaluation.evaluationId,
                link.decisionExecutionId,
                link.ruleCode,
            )
        }
        return evaluation
    }

    fun findDecisionExecutionId(
        transactionId: String,
        externalTransactionId: String,
        sourceSystem: String,
        transactionVersion: Int,
        rulesetVersion: String,
        purpose: String,
        evaluationRequestId: String?,
        inputEventId: String?,
        snapshotHash: String,
    ): java.util.UUID? {
        val byInput = inputEventId?.let { repository.findByInputEventIdAndPurpose(it, purpose) }
        if (byInput != null) {
            requireSameInput(byInput, sourceSystem, externalTransactionId, transactionVersion, snapshotHash)
            require(byInput.rulesetVersion == rulesetVersion) { "input event conflicts with ruleset" }
            return byInput.decisionExecutionId
        }
        val existing = if (purpose == "LIVE") {
            repository.findByTransactionIdAndTransactionVersionAndRulesetVersionAndPurpose(
                transactionId,
                transactionVersion,
                rulesetVersion,
                purpose,
            )
        } else {
            evaluationRequestId?.let { repository.findByEvaluationRequestIdAndPurpose(it, purpose) }
        }
        existing ?: return null
        if (purpose == "LIVE") {
            require(existing.snapshotHash == snapshotHash) { "evaluation identity conflicts with snapshot" }
        } else {
            requireSameInput(existing, sourceSystem, externalTransactionId, transactionVersion, snapshotHash)
        }
        require(existing.rulesetVersion == rulesetVersion) { "evaluation identity conflicts with ruleset" }
        return existing.decisionExecutionId
    }

    private fun requireSameInput(
        existing: TransactionEvaluationEntity,
        sourceSystem: String,
        externalTransactionId: String,
        transactionVersion: Int,
        snapshotHash: String,
    ) {
        require(existing.sourceSystem == sourceSystem) { "evaluation identity conflicts with sourceSystem" }
        require(existing.externalTransactionId == externalTransactionId) { "evaluation identity conflicts with transaction" }
        require(existing.transactionVersion == transactionVersion) { "evaluation identity conflicts with transactionVersion" }
        require(existing.snapshotHash == snapshotHash) { "evaluation identity conflicts with snapshot" }
    }

    private fun ruleReference(rule: br.com.evaluation.domain.RuleEvaluationReference): Map<String, Any?> =
        mapOf(
            "ruleCode" to rule.ruleCode,
            "ruleVersion" to rule.ruleVersion,
            "explanationCode" to rule.explanationCode,
        ).filterValues { it != null }

    private fun factValue(value: FactValue): Any = when (value) {
        is FactValue.BooleanValue -> value.value
        is FactValue.EnumValue -> value.value
        is FactValue.NumberValue -> value.value
        is FactValue.StringValue -> value.value
        is FactValue.MoneyValue -> mapOf("amount" to value.amount, "currency" to value.currency)
    }
}
