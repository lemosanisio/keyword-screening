package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.infrastructure.output.persistence.entity.DryRunLogEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DryRunLogJpaRepository : JpaRepository<DryRunLogEntity, UUID> {
    fun findByConfigurationIdAndVersion(configurationId: UUID, version: Int): List<DryRunLogEntity>
    fun findByConfigurationId(configurationId: UUID): List<DryRunLogEntity>
}
