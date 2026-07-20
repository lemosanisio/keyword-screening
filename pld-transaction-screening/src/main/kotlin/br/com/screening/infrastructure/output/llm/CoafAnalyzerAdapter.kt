package br.com.screening.infrastructure.output.llm

import br.com.screening.domain.port.LlmClassifierPort
import br.com.screening.domain.port.LlmResponse
import br.com.screening.infrastructure.configuration.CoafAnalyzerProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class CoafAnalyzerAdapter(
    private val objectMapper: ObjectMapper,
    private val properties: CoafAnalyzerProperties
) : LlmClassifierPort {

    private val restClient: RestClient by lazy {
        val timeoutMillis = (properties.timeoutSeconds * 1000).toInt()
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(timeoutMillis)
            setReadTimeout(timeoutMillis)
        }
        RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .build()
    }

    override fun classify(prompt: String): LlmResponse {
        return try {
            val requestBody = mapOf("texto" to prompt, "prioridade" to "ALTA")

            val responseBody = restClient.post()
                .uri("/api/coaf/analisar")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String::class.java)

            parseResponse(responseBody)
        } catch (e: Exception) {
            LlmResponse(
                classification = null,
                confidence = null,
                reason = null,
                rawResponse = null,
                success = false,
                errorMessage = "Erro na comunicação com LLM: ${e.message}"
            )
        }
    }

    private fun parseResponse(responseBody: String?): LlmResponse {
        if (responseBody == null) {
            return LlmResponse(
                classification = null,
                confidence = null,
                reason = null,
                rawResponse = null,
                success = false,
                errorMessage = "Resposta vazia do LLM"
            )
        }
        return try {
            val json = objectMapper.readTree(responseBody)
            LlmResponse(
                classification = json.get("decisao")?.asText(),
                confidence = json.get("confianca")?.asDouble(),
                reason = json.get("justificativa")?.asText(),
                rawResponse = responseBody,
                success = true
            )
        } catch (e: Exception) {
            LlmResponse(
                classification = null,
                confidence = null,
                reason = null,
                rawResponse = responseBody,
                success = false,
                errorMessage = "Erro ao parsear resposta do LLM: ${e.message}"
            )
        }
    }
}
