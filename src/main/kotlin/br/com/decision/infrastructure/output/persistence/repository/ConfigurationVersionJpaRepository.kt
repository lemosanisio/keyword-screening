package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.infrastructure.output.persistence.entity.ConfigurationVersionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConfigurationVersionJpaRepository : JpaRepository<ConfigurationVersionEntity, UUID> {
    fun findByConfigurationId(configurationId: UUID): List<ConfigurationVersionEntity>
}
