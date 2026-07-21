package br.com.pld.customeranalysis.operations

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {
    @Bean
    fun customerAnalysisOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("PLD Customer Analysis API")
                .version("v1")
                .description("Workbench APIs for Party, AnalysisCycle and timeline foundation."),
        )
}
