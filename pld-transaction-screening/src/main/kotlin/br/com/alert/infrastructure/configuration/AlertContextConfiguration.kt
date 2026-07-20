package br.com.alert.infrastructure.configuration

import org.springframework.context.annotation.Configuration

/**
 * Configuração do Alert Context.
 * Ativa component scanning e beans necessários para o contexto de alertas.
 * No MVP, o Spring auto-detecta os componentes via annotations (@Service, @Repository, @Component).
 * Esta classe existe como ponto de extensão para beans futuros que necessitem de configuração manual.
 */
@Configuration
class AlertContextConfiguration
