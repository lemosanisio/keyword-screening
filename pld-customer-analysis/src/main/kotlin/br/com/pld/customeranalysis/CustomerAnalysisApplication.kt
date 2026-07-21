package br.com.pld.customeranalysis

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CustomerAnalysisApplication

fun main(args: Array<String>) {
    runApplication<CustomerAnalysisApplication>(*args)
}
