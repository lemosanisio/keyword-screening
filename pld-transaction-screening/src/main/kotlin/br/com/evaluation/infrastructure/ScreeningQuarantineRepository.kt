package br.com.evaluation.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ScreeningQuarantineJpaRepository : JpaRepository<ScreeningQuarantineEntity, UUID>
