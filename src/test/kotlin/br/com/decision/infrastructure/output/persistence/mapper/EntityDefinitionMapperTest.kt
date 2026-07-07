package br.com.decision.infrastructure.output.persistence.mapper

import br.com.decision.domain.model.EntityDefinition
import br.com.decision.domain.model.vo.FactName
import br.com.decision.infrastructure.output.persistence.entity.EntityDefinitionEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("EntityDefinitionMapper")
class EntityDefinitionMapperTest {

    private val mapper = EntityDefinitionMapper()

    @Test
    @DisplayName("toDomain maps all entity fields correctly")
    fun toDomainMapsAllFields() {
        val id = UUID.randomUUID()
        val entity = EntityDefinitionEntity(
            id = id,
            name = "transaction",
            displayName = "Transação PIX",
            sourceSystem = "core-banking",
            factNames = listOf("amount", "currency", "customerRisk")
        )

        val domain = mapper.toDomain(entity)

        assertEquals(id, domain.id)
        assertEquals("transaction", domain.name)
        assertEquals("Transação PIX", domain.displayName)
        assertEquals("core-banking", domain.sourceSystem)
        assertEquals(3, domain.factNames.size)
        assertEquals(FactName("amount"), domain.factNames[0])
        assertEquals(FactName("currency"), domain.factNames[1])
        assertEquals(FactName("customerRisk"), domain.factNames[2])
    }

    @Test
    @DisplayName("toEntity maps all domain fields correctly")
    fun toEntityMapsAllFields() {
        val id = UUID.randomUUID()
        val domain = EntityDefinition(
            id = id,
            name = "customer",
            displayName = "Cliente",
            sourceSystem = "crm",
            factNames = listOf(FactName("riskLevel"), FactName("segment"))
        )

        val entity = mapper.toEntity(domain)

        assertEquals(id, entity.id)
        assertEquals("customer", entity.name)
        assertEquals("Cliente", entity.displayName)
        assertEquals("crm", entity.sourceSystem)
        assertEquals(listOf("riskLevel", "segment"), entity.factNames)
    }

    @Test
    @DisplayName("round-trip domain -> entity -> domain preserves all data")
    fun roundTripPreservesData() {
        val id = UUID.randomUUID()
        val original = EntityDefinition(
            id = id,
            name = "account",
            displayName = "Conta Bancária",
            sourceSystem = "accounts-api",
            factNames = listOf(FactName("balance"), FactName("status"), FactName("openDate"))
        )

        val result = mapper.toDomain(mapper.toEntity(original))

        assertEquals(original, result)
    }

    @Test
    @DisplayName("handles empty factNames list")
    fun handlesEmptyFactNames() {
        val id = UUID.randomUUID()
        val domain = EntityDefinition(
            id = id,
            name = "empty",
            displayName = "Empty Entity",
            sourceSystem = "none",
            factNames = emptyList()
        )

        val result = mapper.toDomain(mapper.toEntity(domain))

        assertEquals(emptyList<FactName>(), result.factNames)
    }
}
