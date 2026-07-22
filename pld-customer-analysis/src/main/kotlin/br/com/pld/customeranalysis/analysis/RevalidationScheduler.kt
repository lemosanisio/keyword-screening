package br.com.pld.customeranalysis.analysis

import br.com.pld.customeranalysis.identityaccess.Actor
import br.com.pld.customeranalysis.identityaccess.ActorRole
import br.com.pld.customeranalysis.party.PartyJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Scheduler que identifica parties elegíveis para revalidação periódica
 * com base na política de intervalo por risco.
 *
 * Coalescência: se já existe um AnalysisCycle aberto (não CLOSED/DECIDED),
 * não cria novo ciclo.
 */
@Component
class RevalidationScheduler(
    private val analysisCycleService: AnalysisCycleService,
    private val analysisCycleRepository: AnalysisCycleJpaRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(RevalidationScheduler::class.java)
    private val systemActor = Actor("revalidation-scheduler", ActorRole.SYSTEM)

    /**
     * Executa diariamente às 03:00 (em produção).
     * Para o protótipo, pode ser invocado manualmente via endpoint dev.
     */
    @Scheduled(cron = "\${pld.revalidation.cron:0 0 3 * * *}")
    fun checkEligibleParties() {
        val eligible = findEligible()
        logger.info("Revalidation check: {} parties eligible", eligible.size)

        eligible.forEach { (partyId, riskLevel) ->
            // Coalescência: não criar ciclo se já existe um aberto
            val openCycles = analysisCycleRepository.findByPartyIdAndStatusIn(
                partyId,
                listOf(
                    AnalysisCycleStatus.CREATED,
                    AnalysisCycleStatus.COLLECTING_EVIDENCE,
                    AnalysisCycleStatus.READY_FOR_EVALUATION,
                    AnalysisCycleStatus.UNDER_REVIEW,
                ),
            )
            if (openCycles.isNotEmpty()) {
                logger.debug("Party {} already has open cycle, skipping revalidation", partyId)
                return@forEach
            }

            analysisCycleService.open(
                OpenAnalysisCycleCommand(
                    partyId = partyId,
                    cycleType = AnalysisCycleType.PERIODIC_REVIEW,
                    policyVersion = "revalidation-policy-$riskLevel",
                    actor = systemActor,
                    correlationId = "revalidation-${Instant.now(clock).toEpochMilli()}",
                ),
            )
            logger.info("Periodic review opened for party={}, riskLevel={}", partyId, riskLevel)
        }
    }

    private fun findEligible(): List<Pair<String, String>> {
        val now = Instant.now(clock)
        return jdbcTemplate.query(
            """
            SELECT p.id, p.current_risk_level
            FROM party p
            JOIN revalidation_policy rp ON rp.risk_level = COALESCE(p.current_risk_level, 'LOW')
            WHERE p.last_review_completed_at IS NULL
               OR p.last_review_completed_at + (rp.review_interval_days || ' days')::interval < ?
            """.trimIndent(),
            { rs, _ -> rs.getString("id") to rs.getString("current_risk_level") },
            java.sql.Timestamp.from(now),
        )
    }
}
