package br.com.screening.domain.repository

import br.com.screening.domain.model.RestrictedTerm

interface RestrictedTermRepository {
    fun findAllActive(): List<RestrictedTerm>
}
