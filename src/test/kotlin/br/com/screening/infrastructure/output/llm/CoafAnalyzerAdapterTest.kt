package br.com.screening.infrastructure.output.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * Testes unitários para [CoafAnalyzerAdapter] usando MockWebServer.
 * Validates: Requirements 4.1, 4.3, 4.4, 4.5
 */
class CoafAnalyzerAdapterTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private lateinit var mockWebServer: MockWebServer
    private lateinit var adapter: CoafAnalyzerAdapter

    @BeforeEach
    fun setup() {
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

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    @DisplayName("deve retornar LlmResponse com success=true e classification=COMUNICAR quando API responde com decisao COMUNICAR")
    fun shouldReturnSuccessWithComunicar() {
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

        assertEquals(true, result.success)
        assertEquals("COMUNICAR", result.classification)
        assertEquals(0.95, result.confidence)
        assertEquals("Valor alto em espécie sem comprovação", result.reason)
        assertEquals(responseJson, result.rawResponse)
    }

    @Test
    @DisplayName("deve retornar LlmResponse com success=true e classification=NAO_COMUNICAR quando API responde com decisao NAO_COMUNICAR")
    fun shouldReturnSuccessWithNaoComunicar() {
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

        assertEquals(true, result.success)
        assertEquals("NAO_COMUNICAR", result.classification)
        assertEquals(0.98, result.confidence)
        assertEquals("Operação compatível com perfil do cliente", result.reason)
    }

    @Test
    @DisplayName("deve retornar LlmResponse com success=false quando ocorre timeout")
    fun shouldReturnFailureOnTimeout() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("{}")
                .setBodyDelay(5, TimeUnit.SECONDS)
        )

        val result = adapter.classify("Prompt qualquer")

        assertEquals(false, result.success)
        assertNull(result.classification)
        assertTrue(result.errorMessage!!.contains("Erro na comunicação com LLM"))
    }

    @Test
    @DisplayName("deve retornar LlmResponse com success=false quando servidor retorna HTTP 500")
    fun shouldReturnFailureOnHttp500() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val result = adapter.classify("Prompt qualquer")

        assertEquals(false, result.success)
        assertNull(result.classification)
    }

    @Test
    @DisplayName("deve retornar LlmResponse com success=false quando resposta contém JSON inválido")
    fun shouldReturnFailureOnInvalidJson() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("isto não é json válido {{{")
                .addHeader("Content-Type", "application/json")
        )

        val result = adapter.classify("Prompt qualquer")

        assertEquals(false, result.success)
        assertNull(result.classification)
        assertTrue(result.errorMessage!!.contains("Erro ao parsear resposta do LLM"))
    }

    @Test
    @DisplayName("deve retornar LlmResponse com campos parciais quando JSON falta campos opcionais")
    fun shouldReturnPartialFieldsWhenJsonMissingOptionalFields() {
        val responseJson = """
            {
              "decisao": "COMUNICAR"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json")
        )

        val result = adapter.classify("Prompt parcial")

        assertEquals(true, result.success)
        assertEquals("COMUNICAR", result.classification)
        assertNull(result.confidence)
        assertNull(result.reason)
        assertEquals(responseJson, result.rawResponse)
    }

    @Test
    @DisplayName("deve enviar request com corpo correto incluindo texto e prioridade")
    fun shouldSendCorrectRequestBody() {
        val responseJson = """
            {
              "decisao": "NAO_COMUNICAR",
              "justificativa": "OK",
              "confianca": 0.99
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json")
        )

        adapter.classify("Meu prompt de teste")

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/coaf/analisar", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"texto\""))
        assertTrue(body.contains("Meu prompt de teste"))
        assertTrue(body.contains("\"prioridade\""))
        assertTrue(body.contains("ALTA"))
    }

    @Test
    @DisplayName("deve retornar LlmResponse com success=false quando resposta é null (empty body)")
    fun shouldReturnFailureOnNullResponseBody() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(204) // No content
        )

        val result = adapter.classify("Prompt qualquer")

        // The RestClient returns null for empty body, triggering parseResponse(null)
        assertEquals(false, result.success)
        assertNull(result.classification)
        assertNull(result.confidence)
    }

    @Test
    @DisplayName("deve retornar LlmResponse com success=true e classification=REVISAO_MANUAL")
    fun shouldReturnSuccessWithRevisaoManual() {
        val responseJson = """
            {
              "decisao": "REVISAO_MANUAL",
              "justificativa": "Análise inconclusiva",
              "confianca": 0.55,
              "enquadramentoLegal": [],
              "alertas": [],
              "timestamp": "2024-01-15T14:35:00.000Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json")
        )

        val result = adapter.classify("Operação com valores múltiplos")

        assertEquals(true, result.success)
        assertEquals("REVISAO_MANUAL", result.classification)
        assertEquals(0.55, result.confidence)
        assertEquals("Análise inconclusiva", result.reason)
    }
}
