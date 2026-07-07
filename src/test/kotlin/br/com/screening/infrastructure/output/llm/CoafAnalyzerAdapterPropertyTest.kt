package br.com.screening.infrastructure.output.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.TestInstance
import kotlin.random.Random

/**
 * Property test para [CoafAnalyzerAdapter].
 *
 * Property 9: Mapeamento correto de resposta do coaf-analyzer
 * Validates: Requirements 4.3, 5.1
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoafAnalyzerAdapterPropertyTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private lateinit var mockWebServer: MockWebServer
    private lateinit var adapter: CoafAnalyzerAdapter

    @BeforeAll
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        adapter = CoafAnalyzerAdapter(
            objectMapper = objectMapper,
            properties = br.com.screening.infrastructure.configuration.CoafAnalyzerProperties(
                baseUrl = baseUrl,
                timeoutSeconds = 5
            )
        )
    }

    @AfterAll
    fun teardown() {
        mockWebServer.shutdown()
    }

    @RepeatedTest(50)
    @DisplayName("Property 9: Mapeamento correto de resposta do coaf-analyzer - classificação e confiança são preservadas")
    fun classificationAndConfidenceArePreserved() {
        val decisoes = listOf("COMUNICAR", "NAO_COMUNICAR", "REVISAO_MANUAL")
        val decisao = decisoes[Random.nextInt(3)]
        val confianca = Random.nextDouble(0.0, 1.0)

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

        assertEquals(true, result.success)
        assertEquals(decisao, result.classification)
        assertEquals(confianca, result.confidence)
    }
}
