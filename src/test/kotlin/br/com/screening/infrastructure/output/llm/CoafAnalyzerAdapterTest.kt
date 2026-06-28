package br.com.screening.infrastructure.output.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit

/**
 * Testes unitários para [CoafAnalyzerAdapter] usando MockWebServer.
 * Validates: Requirements 4.1, 4.3, 4.4, 4.5
 */
class CoafAnalyzerAdapterTest : StringSpec({

    val objectMapper: ObjectMapper = jacksonObjectMapper()
    lateinit var mockWebServer: MockWebServer
    lateinit var adapter: CoafAnalyzerAdapter

    beforeTest {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        adapter = CoafAnalyzerAdapter(
            objectMapper = objectMapper,
            properties = br.com.screening.infrastructure.configuration.CoafAnalyzerProperties(
                baseUrl = baseUrl,
                timeoutSeconds = 2
            )
        )
    }

    afterTest {
        mockWebServer.shutdown()
    }

    // Requirement 4.3 — resposta OK com decisão COMUNICAR
    "deve retornar LlmResponse com success=true e classification=COMUNICAR quando API responde com decisao COMUNICAR" {
        val responseJson = """
            {
              "decisao": "COMUNICAR",
              "justificativa": "Valor alto em espécie sem comprovação",
              "confianca": 0.95,
              "enquadramentoLegal": ["Art. 10, Circular BACEN 3.978/2020"],
              "fundamentacaoTecnica": "Análise detalhada",
              "alertas": ["VALOR_ALTO_ESPECIE"],
              "timestamp": "2024-01-15T14:30:00.000Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json")
        )

        val result = adapter.classify("Depósito de R$ 80.000 em espécie")

        result.success shouldBe true
        result.classification shouldBe "COMUNICAR"
        result.confidence shouldBe 0.95
        result.reason shouldBe "Valor alto em espécie sem comprovação"
        result.rawResponse shouldBe responseJson
    }

    // Requirement 4.3 — resposta OK com decisão NAO_COMUNICAR
    "deve retornar LlmResponse com success=true e classification=NAO_COMUNICAR quando API responde com decisao NAO_COMUNICAR" {
        val responseJson = """
            {
              "decisao": "NAO_COMUNICAR",
              "justificativa": "Operação compatível com perfil do cliente",
              "confianca": 0.98,
              "enquadramentoLegal": ["Circular BACEN 3.978/2020"],
              "fundamentacaoTecnica": "Sem indícios",
              "alertas": [],
              "timestamp": "2024-01-15T14:32:00.000Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json")
        )

        val result = adapter.classify("Transferência de R$ 3.000 para conta própria")

        result.success shouldBe true
        result.classification shouldBe "NAO_COMUNICAR"
        result.confidence shouldBe 0.98
        result.reason shouldBe "Operação compatível com perfil do cliente"
    }

    // Requirement 4.5 — timeout do servidor
    "deve retornar LlmResponse com success=false quando ocorre timeout" {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("{}")
                .setBodyDelay(5, TimeUnit.SECONDS)
        )

        val result = adapter.classify("Prompt qualquer")

        result.success shouldBe false
        result.classification shouldBe null
        result.errorMessage shouldContain "Erro na comunicação com LLM"
    }

    // Requirement 4.4 — HTTP 500
    "deve retornar LlmResponse com success=false quando servidor retorna HTTP 500" {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val result = adapter.classify("Prompt qualquer")

        result.success shouldBe false
        result.classification shouldBe null
    }

    // Requirement 4.4 — JSON inválido
    "deve retornar LlmResponse com success=false quando resposta contém JSON inválido" {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("isto não é json válido {{{")
                .addHeader("Content-Type", "application/json")
        )

        val result = adapter.classify("Prompt qualquer")

        result.success shouldBe false
        result.classification shouldBe null
        result.errorMessage shouldContain "Erro ao parsear resposta do LLM"
    }
})
