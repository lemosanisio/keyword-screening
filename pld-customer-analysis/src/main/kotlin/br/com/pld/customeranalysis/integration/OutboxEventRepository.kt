package br.com.pld.customeranalysis.integration

import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, String>
