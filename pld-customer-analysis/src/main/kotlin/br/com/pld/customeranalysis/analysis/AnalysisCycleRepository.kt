package br.com.pld.customeranalysis.analysis

import org.springframework.data.jpa.repository.JpaRepository

interface AnalysisCycleJpaRepository : JpaRepository<AnalysisCycleEntity, String>
