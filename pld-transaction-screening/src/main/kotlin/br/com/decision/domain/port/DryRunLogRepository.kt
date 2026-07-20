package br.com.decision.domain.port

import br.com.decision.domain.model.DryRunLog
import br.com.decision.domain.model.vo.ConfigurationVersion
import java.util.UUID

/**
 * Output port para persistência de logs de dry-run.
 * Usado para rastreabilidade e como pré-requisito para ativação de configurações.
 */
interface DryRunLogRepository {
    fun save(log: DryRunLog): DryRunLog
    fun findByConfigurationIdAndVersion(configurationId: UUID, version: ConfigurationVersion): List<DryRunLog>
    fun findByConfigurationId(configurationId: UUID): List<DryRunLog>
}
