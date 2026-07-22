package br.com.decision.infrastructure.output.persistence.repository

import br.com.decision.domain.model.RuleConfiguration
import br.com.decision.domain.port.RuleConfigurationRepository
import br.com.decision.domain.model.vo.RuleId
import br.com.decision.infrastructure.output.persistence.mapper.RuleConfigurationMapper
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class RuleConfigurationRepositoryImpl(
    private val jpaRepository: RuleConfigurationJpaRepository,
    private val versionJpaRepository: ConfigurationVersionJpaRepository,
    private val mapper: RuleConfigurationMapper
) : RuleConfigurationRepository {

    override fun save(config: RuleConfiguration): RuleConfiguration {
        val entity = mapper.toEntity(config)
        val isNew = !jpaRepository.existsById(config.id)
        val saved = jpaRepository.save(entity)

        if (isNew) {
            // Somente insere versions na criação; updates e ativação não alteram versions existentes
            val versionEntities = config.versions.map { mapper.versionToEntity(it, saved.id) }
            versionJpaRepository.saveAll(versionEntities)
        } else {
            // Inserir apenas versions novas (versão maior que a última persistida)
            val existingVersions = versionJpaRepository.findByConfigurationId(saved.id)
                .map { it.version }
                .toSet()
            val newVersions = config.versions.filter { it.version.value !in existingVersions }
            if (newVersions.isNotEmpty()) {
                val versionEntities = newVersions.map { mapper.versionToEntity(it, saved.id) }
                versionJpaRepository.saveAll(versionEntities)
            }
        }

        return findById(saved.id) ?: mapper.toDomain(saved)
    }

    override fun findById(id: UUID): RuleConfiguration? =
        jpaRepository.findById(id).orElse(null)?.let { mapper.toDomain(it) }

    override fun findActiveByRuleId(ruleId: RuleId): RuleConfiguration? =
        jpaRepository.findByRuleIdAndActiveTrue(ruleId.value)?.let { mapper.toDomain(it) }

    override fun findByRuleId(ruleId: RuleId): List<RuleConfiguration> =
        jpaRepository.findByRuleId(ruleId.value).map { mapper.toDomain(it) }
}
