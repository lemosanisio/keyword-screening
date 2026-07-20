package br.com.decision.infrastructure.configuration

import br.com.decision.domain.port.CustomerRiskPort
import br.com.decision.domain.resolver.CustomerResolver
import br.com.decision.domain.resolver.ScreeningResolver
import br.com.decision.domain.service.ContextBuilder
import br.com.decision.domain.service.DecisionEngine
import br.com.decision.domain.service.ExpressionEvaluator
import br.com.decision.domain.service.FactResolver
import br.com.decision.domain.service.RuleEngine
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuração do Decision Context.
 * Registra domain services como Spring beans sem poluir o domínio com annotations de framework.
 * Domain services são classes puras — a DI é gerenciada exclusivamente aqui.
 */
@Configuration
@EnableConfigurationProperties(CustomerRiskProperties::class)
class DecisionContextConfiguration {

    @Bean
    fun expressionEvaluator() = ExpressionEvaluator()

    @Bean
    fun ruleEngine(expressionEvaluator: ExpressionEvaluator) = RuleEngine(expressionEvaluator)

    @Bean
    fun screeningResolver() = ScreeningResolver()

    @Bean
    fun customerResolver(customerRiskPort: CustomerRiskPort) = CustomerResolver(customerRiskPort)

    @Bean
    fun contextBuilder(resolvers: List<FactResolver>) = ContextBuilder(resolvers)

    @Bean
    fun decisionEngine(contextBuilder: ContextBuilder, ruleEngine: RuleEngine) =
        DecisionEngine(contextBuilder, ruleEngine)
}
