package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.domain.model.DryRunLog
import br.com.decision.domain.port.DryRunLogRepository
import br.com.decision.domain.model.vo.ConfigurationVersion
import br.com.decision.infrastructure.output.persistence.mapper.DryRunLogMapper
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class DryRunLogRepositoryImpl(
    private val jpaRepository: DryRunLogJpaRepository,
    private val mapper: DryRunLogMapper
) : DryRunLogRepository {

    override fun save(log: DryRunLog): DryRunLog {
        val entity = mapper.toEntity(log)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun findByConfigurationIdAndVersion(configurationId: UUID, version: ConfigurationVersion): List<DryRunLog> =
        jpaRepository.findByConfigurationIdAndVersion(configurationId, version.value)
            .map { mapper.toDomain(it) }

    override fun findByConfigurationId(configurationId: UUID): List<DryRunLog> =
        jpaRepository.findByConfigurationId(configurationId)
            .map { mapper.toDomain(it) }
}
