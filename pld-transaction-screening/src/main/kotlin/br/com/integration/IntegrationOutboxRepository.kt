package br.com.integration

import org.springframework.data.jpa.repository.JpaRepository

interface IntegrationOutboxRepository : JpaRepository<IntegrationOutboxEntity, String>
