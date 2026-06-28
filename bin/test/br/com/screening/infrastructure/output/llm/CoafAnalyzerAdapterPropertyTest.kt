package br.com.screening.infrastructure.output.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * Property test para [CoafAnalyzerAdapter].
 *
 * **Property 9: Mapeamento correto de resposta do coaf-analyzer**
 * **Validates: Requirements 4.3, 5.1**
 *
 * Para qualquer par (decisao, confianca) gerado arbitrariamente dentro do domínio válido
 * do coaf-analyzer, o adapter deve mapear corretamente os campos da resposta JSON para
 * os campos de LlmResponse.
 */
class CoafAnalyzerAdapterPropertyTest : StringSpec({

    val objectMapper: ObjectMapper = jacksonObjectMapper()

    "Property 9: Mapeamento correto de resposta do coaf-analyzer - classificação e confiança são preservadas" {
        val mockWebServer = MockWebServer()
        mockWebServer.start()

        try {
            val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
            val adapter = CoafAnalyzerAdapter(
                objectMapper = objectMapper,
                properties = br.com.screening.infrastructure.configuration.CoafAnalyzerProperties(
                    baseUrl = baseUrl,
                    timeoutSeconds = 5
                )
            )

            checkAll(50, Arb.element("COMUNICAR", "NAO_COMUNICAR", "REVISAO_MANUAL"), Arb.double(0.0..1.0)) { decisao, confianca ->
                val responseJson = """
                    {
                        "decisao": "$decisao",
                        "confianca": $confianca,
                        "justificativa": "teste"
                    }
                """.trimIndent()

                mockWebServer.enqueue(
                    MockResponse()
                        .setBody(responseJson)
                        .addHeader("Content-Type", "application/json")
                        .setResponseCode(200)
                )

                val result = adapter.classify("prompt de teste")

                result.success shouldBe true
                result.classification shouldBe decisao
                result.confidence shouldBe confianca
            }
        } finally {
            mockWebServer.shutdown()
        }
    }
})
