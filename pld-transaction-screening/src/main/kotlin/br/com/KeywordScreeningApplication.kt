package br.com

import br.com.decision.infrastructure.configuration.CustomerRiskProperties
import br.com.integration.OutboxDrainProperties
import br.com.integration.SqsProperties
import br.com.integration.TransactionSignalProperties
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
    CustomerRiskProperties::class,
    TransactionSignalProperties::class,
    SqsProperties::class,
    OutboxDrainProperties::class,
)
class KeywordScreeningApplication

fun main(args: Array<String>) {
    runApplication<KeywordScreeningApplication>(*args)
}
