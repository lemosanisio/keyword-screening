package br.com.decision.infrastructure.output.rest

import br.com.decision.domain.model.enums.CustomerRisk
import br.com.decision.infrastructure.configuration.CustomerRiskProperties
import br.com.shared.domain.valueobject.CustomerId
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * Testes unitários para [CustomerRiskAdapter] usando MockWebServer.
 * Validates: Requirements 3.5, 3.7
 */
class CustomerRiskAdapterTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var adapter: CustomerRiskAdapter

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/").toString().trimEnd('/')
        adapter = CustomerRiskAdapter(
            properties = CustomerRiskProperties(
                url = baseUrl,
                timeoutMs = 2000
            )
        )
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    @DisplayName("deve retornar CustomerRisk.AR quando API responde com risco AR")
    fun `deve retornar CustomerRisk AR quando API responde com risco AR`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("""{"risk": "AR"}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = adapter.getCustomerRisk(CustomerId("CUST-001"))

        assertEquals(CustomerRisk.AR, result)
        val request = mockWebServer.takeRequest()
        assertEquals("/customers/CUST-001/risk", request.path)
    }

    @Test
    @DisplayName("deve retornar CustomerRisk.MR quando API responde com risco MR")
    fun `deve retornar CustomerRisk MR quando API responde com risco MR`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("""{"risk": "MR"}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = adapter.getCustomerRisk(CustomerId("CUST-002"))

        assertEquals(CustomerRisk.MR, result)
    }

    @Test
    @DisplayName("deve retornar CustomerRisk.BR quando API responde com risco BR")
    fun `deve retornar CustomerRisk BR quando API responde com risco BR`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("""{"risk": "BR"}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = adapter.getCustomerRisk(CustomerId("CUST-003"))

        assertEquals(CustomerRisk.BR, result)
    }

    @Test
    @DisplayName("deve retornar null quando API responde com valor de risco inválido")
    fun `deve retornar null quando API responde com valor de risco invalido`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("""{"risk": "INVALID_VALUE"}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = adapter.getCustomerRisk(CustomerId("CUST-004"))

        assertNull(result)
    }

    @Test
    @DisplayName("deve retornar null quando API responde com risk null")
    fun `deve retornar null quando API responde com risk null`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("""{"risk": null}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = adapter.getCustomerRisk(CustomerId("CUST-005"))

        assertNull(result)
    }

    @Test
    @DisplayName("deve retornar null quando ocorre timeout")
    fun `deve retornar null quando ocorre timeout`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("""{"risk": "AR"}""")
                .setBodyDelay(5, TimeUnit.SECONDS)
        )

        val result = adapter.getCustomerRisk(CustomerId("CUST-006"))

        assertNull(result)
    }

    @Test
    @DisplayName("deve retornar null quando servidor retorna HTTP 500")
    fun `deve retornar null quando servidor retorna HTTP 500`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val result = adapter.getCustomerRisk(CustomerId("CUST-007"))

        assertNull(result)
    }

    @Test
    @DisplayName("deve retornar null quando servidor retorna HTTP 404")
    fun `deve retornar null quando servidor retorna HTTP 404`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not Found")
        )

        val result = adapter.getCustomerRisk(CustomerId("CUST-008"))

        assertNull(result)
    }

    @Test
    @DisplayName("deve retornar null quando resposta contém JSON inválido")
    fun `deve retornar null quando resposta contem JSON invalido`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("isto não é json {{{")
                .addHeader("Content-Type", "application/json")
        )

        val result = adapter.getCustomerRisk(CustomerId("CUST-009"))

        assertNull(result)
    }

    @Test
    @DisplayName("deve retornar null quando campo risk está ausente no JSON")
    fun `deve retornar null quando campo risk esta ausente no JSON`() {
        mockWebServer.enqueue(
            MockResponse()
                .setBody("""{"otherField": "value"}""")
                .addHeader("Content-Type", "application/json")
        )

        val result = adapter.getCustomerRisk(CustomerId("CUST-010"))

        assertNull(result)
    }

    @Test
    @DisplayName("deve retornar null quando resposta é vazia (body vazio)")
    fun `deve retornar null quando resposta e vazia`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(204)
        )

        val result = adapter.getCustomerRisk(CustomerId("CUST-011"))

        assertNull(result)
    }

    @Test
    @DisplayName("deve retornar null quando servidor retorna HTTP 503")
    fun `deve retornar null quando servidor retorna HTTP 503`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("Service Unavailable")
        )

        val result = adapter.getCustomerRisk(CustomerId("CUST-012"))

        assertNull(result)
    }
}
