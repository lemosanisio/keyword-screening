package br.com.screening.infrastructure.output.persistence.mapper

import br.com.screening.domain.model.RestrictedTerm
import br.com.screening.infrastructure.output.persistence.entity.RestrictedTermEntity
import org.springframework.stereotype.Component

@Component
class RestrictedTermMapper {

    fun toEntity(domain: RestrictedTerm): RestrictedTermEntity =
        RestrictedTermEntity(
            id = domain.id,
            term = domain.term,
            category = domain.category,
            active = domain.active,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )

    fun toDomain(entity: RestrictedTermEntity): RestrictedTerm =
        RestrictedTerm(
            id = entity.id,
            term = entity.term,
            category = entity.category,
            active = entity.active,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
}
