package br.com.screening.infrastructure.output.persistence.repository

import br.com.screening.infrastructure.output.persistence.entity.RestrictedTermEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RestrictedTermJpaRepository : JpaRepository<RestrictedTermEntity, Long> {
    fun findAllByActiveTrue(): List<RestrictedTermEntity>
}
