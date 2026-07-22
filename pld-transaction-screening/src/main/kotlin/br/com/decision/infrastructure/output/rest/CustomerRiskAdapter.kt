package br.com.decision.infrastructure.output.rest

import br.com.decision.domain.model.enums.CustomerRisk
import br.com.decision.domain.port.CustomerRiskPort
import br.com.decision.infrastructure.configuration.CustomerRiskProperties
import br.com.shared.domain.valueobject.CustomerId
import org.slf4j.LoggerFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Adapter REST para busca de risco do cliente no sistema externo (Cadastro).
 * Retorna null em qualquer falha (timeout, erro de conexão, resposta inválida).
 */
class CustomerRiskAdapter(
    private val properties: CustomerRiskProperties
) : CustomerRiskPort {

    private val logger = LoggerFactory.getLogger(CustomerRiskAdapter::class.java)

    private val restClient: RestClient by lazy {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.timeoutMs.toInt())
            setReadTimeout(properties.timeoutMs.toInt())
        }
        RestClient.builder()
            .baseUrl(properties.url)
            .requestFactory(requestFactory)
            .build()
    }

    override fun getCustomerRisk(customerId: CustomerId): CustomerRisk? {
        return try {
            val response = restClient.get()
                .uri("/customers/{customerId}/risk", customerId.value)
                .retrieve()
                .body(CustomerRiskResponse::class.java)

            response?.risk?.let { riskValue ->
                try {
                    CustomerRisk.valueOf(riskValue)
                } catch (e: IllegalArgumentException) {
                    logger.warn("Valor de risco inválido para cliente {}: {}", customerId.value, riskValue)
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("Falha ao buscar risco do cliente {}: {}", customerId.value, e.message)
            null
        }
    }
}

data class CustomerRiskResponse(val risk: String? = null)
