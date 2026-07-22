package br.com.decision.infrastructure.output.projection

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface CustomerRiskProjectionJpaRepository : JpaRepository<CustomerRiskProjectionEntity, String>

@Repository
class CustomerRiskProjectionRepository(
    private val jpaRepository: CustomerRiskProjectionJpaRepository,
) {
    fun findByPartyId(partyId: String): CustomerRiskProjectionEntity? =
        jpaRepository.findById(partyId).orElse(null)

    fun upsert(entity: CustomerRiskProjectionEntity): CustomerRiskProjectionEntity {
        val existing = jpaRepository.findById(entity.partyId).orElse(null)
        if (existing != null && existing.profileVersion >= entity.profileVersion) {
            // Ignore older version (deduplication)
            return existing
        }
        return jpaRepository.save(entity)
    }
}
