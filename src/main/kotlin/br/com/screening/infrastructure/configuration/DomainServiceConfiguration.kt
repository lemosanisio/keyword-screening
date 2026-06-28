package br.com.screening.infrastructure.configuration

import br.com.screening.domain.service.KeywordMatcher
import br.com.screening.domain.service.PromptBuilder
import br.com.screening.domain.service.ResponseNormalizer
import br.com.screening.domain.service.RoutingClassifier
import br.com.screening.domain.service.TextNormalizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DomainServiceConfiguration {

    @Bean
    fun textNormalizer(): TextNormalizer = TextNormalizer()

    @Bean
    fun keywordMatcher(): KeywordMatcher = KeywordMatcher()

    @Bean
    fun promptBuilder(): PromptBuilder = PromptBuilder()

    @Bean
    fun responseNormalizer(): ResponseNormalizer = ResponseNormalizer()

    @Bean
    fun routingClassifier(): RoutingClassifier = RoutingClassifier()
}
