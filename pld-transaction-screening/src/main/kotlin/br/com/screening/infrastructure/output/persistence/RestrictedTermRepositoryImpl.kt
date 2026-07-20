package br.com.screening.infrastructure.output.persistence

import br.com.screening.domain.model.RestrictedTerm
import br.com.screening.domain.repository.RestrictedTermRepository
import br.com.screening.infrastructure.output.persistence.mapper.RestrictedTermMapper
import br.com.screening.infrastructure.output.persistence.repository.RestrictedTermJpaRepository
import org.springframework.stereotype.Repository

@Repository
class RestrictedTermRepositoryImpl(
    private val jpaRepository: RestrictedTermJpaRepository,
    private val mapper: RestrictedTermMapper
) : RestrictedTermRepository {
    override fun findAllActive(): List<RestrictedTerm> =
        jpaRepository.findAllByActiveTrue().map { mapper.toDomain(it) }
}
