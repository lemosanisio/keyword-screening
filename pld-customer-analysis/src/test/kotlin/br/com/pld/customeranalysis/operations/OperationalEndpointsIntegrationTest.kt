package br.com.pld.customeranalysis.operations

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class OperationalEndpointsIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `exposes health info openapi and integration metrics`() {
        mockMvc.get("/actuator/health")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
            }

        mockMvc.get("/actuator/info")
            .andExpect {
                status { isOk() }
                jsonPath("$.app.name") { value("pld-customer-analysis") }
                jsonPath("$.app.milestone") { value("marco-1-foundation") }
            }

        mockMvc.get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.info.title") { value("PLD Customer Analysis API") }
                jsonPath("$.paths['/v1/parties']") { exists() }
            }

        mockMvc.get("/actuator/metrics/pld.outbox.events")
            .andExpect {
                status { isOk() }
                jsonPath("$.name") { value("pld.outbox.events") }
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
