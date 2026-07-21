package br.com.pld.customeranalysis.party

import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class PartyApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `creates party with snapshot and timeline entry`() {
        val createResponse = mockMvc.post("/v1/parties") {
            header("X-Correlation-Id", "corr-party-create-1")
            header("X-Actor-Id", "analyst-1")
            header("X-Actor-Role", "ANALYST")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """
                {
                  "partyType": "PERSON",
                  "officialName": "Maria Exemplo da Silva",
                  "sourceSystem": "manual"
                }
            """.trimIndent()
        }
            .andExpect {
                status { isCreated() }
                jsonPath("$.partyId", startsWith("pty_"))
                jsonPath("$.partyType") { value("PERSON") }
                jsonPath("$.currentSnapshot.snapshotId", startsWith("psn_"))
                jsonPath("$.currentSnapshot.snapshotVersion") { value(1) }
                jsonPath("$.currentSnapshot.officialName") { value("Maria Exemplo da Silva") }
                jsonPath("$.currentSnapshot.sourceSystem") { value("manual") }
            }
            .andReturn()

        val partyId = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .readTree(createResponse.response.contentAsString)
            .get("partyId")
            .asText()

        mockMvc.get("/v1/parties/{partyId}", partyId)
            .andExpect {
                status { isOk() }
                jsonPath("$.partyId") { value(partyId) }
                jsonPath("$.partyType") { value("PERSON") }
                jsonPath("$.currentSnapshot.snapshotVersion") { value(1) }
            }

        mockMvc.get("/v1/parties/{partyId}/timeline", partyId)
            .andExpect {
                status { isOk() }
                jsonPath("$.entries", hasSize<Any>(1))
                jsonPath("$.entries[0].entryType") { value("PARTY_CREATED") }
                jsonPath("$.entries[0].summaryCode") { value("PARTY_CREATED") }
                jsonPath("$.entries[0].objectType") { value("Party") }
                jsonPath("$.entries[0].objectId") { value(partyId) }
                jsonPath("$.entries[0].correlationId") { value("corr-party-create-1") }
            }
    }

    companion object {
        @Container
        private val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
