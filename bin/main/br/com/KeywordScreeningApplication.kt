package br.com

import br.com.decision.infrastructure.configuration.CustomerRiskProperties
import br.com.screening.infrastructure.configuration.CoafAnalyzerProperties
import br.com.screening.infrastructure.configuration.ContextualScreeningProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(
    CoafAnalyzerProperties::class,
    ContextualScreeningProperties::class,
    CustomerRiskProperties::class
)
class KeywordScreeningApplication

fun main(args: Array<String>) {
    runApplication<KeywordScreeningApplication>(*args)
}
