package br.com.pld.customeranalysis

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class CustomerAnalysisApplication

fun main(args: Array<String>) {
    runApplication<CustomerAnalysisApplication>(*args)
}
