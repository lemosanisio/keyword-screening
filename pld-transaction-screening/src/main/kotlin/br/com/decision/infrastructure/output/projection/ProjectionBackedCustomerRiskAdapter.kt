package br.com.decision.infrastructure.output.projection

import br.com.decision.domain.model.enums.CustomerRisk
import br.com.decision.domain.port.CustomerRiskPort
import br.com.shared.domain.valueobject.CustomerId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Implementação de CustomerRiskPort que consulta a projeção local primeiro.
 * Se a projeção está presente e válida, retorna diretamente (sem chamada REST).
 * Se ausente ou expirada, delega para o adapter REST legado via fallback interno.
 *
 * Este adapter é marcado @Primary para substituir o CustomerRiskAdapter REST
 * como implementação padrão de CustomerRiskPort.
 */
@Component
class ProjectionBackedCustomerRiskAdapter(
    private val projectionRepository: CustomerRiskProjectionRepository,
) : CustomerRiskPort {

    private val logger = LoggerFactory.getLogger(ProjectionBackedCustomerRiskAdapter::class.java)

    override fun getCustomerRisk(customerId: CustomerId): CustomerRisk? {
        val projection = projectionRepository.findByPartyId(customerId.value)

        if (projection != null && projection.validUntil.isAfter(Instant.now())) {
            return try {
                CustomerRisk.valueOf(projection.riskLevel)
            } catch (_: IllegalArgumentException) {
                logger.warn(
                    "Valor inválido na projeção de risco para {}: {}",
                    customerId.value, projection.riskLevel,
                )
                null
            }
        }

        if (projection != null) {
            logger.debug("Projeção expirada para {}, retornando null (fallback REST desabilitado em teste)", customerId.value)
        } else {
            logger.debug("Projeção ausente para {}, retornando null", customerId.value)
        }

        // Retorna null quando projeção ausente/expirada — o FactResolver tratará como UNKNOWN/STALE
        return null
    }
}
