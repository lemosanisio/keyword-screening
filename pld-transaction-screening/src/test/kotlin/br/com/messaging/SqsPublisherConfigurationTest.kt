package br.com.messaging

import br.com.integration.SqsIntegrationConfiguration
import br.com.integration.TransactionSignalProperties
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import java.time.Instant

class SqsPublisherConfigurationTest {
    @Test
    fun `publishes the v1 envelope directly and adds publishedAt`() {
        val client = mockk<SqsClient>()
        val request = slot<SendMessageRequest>()
        every { client.sendMessage(capture(request)) } returns SendMessageResponse.builder().messageId("message-1").build()
        val objectMapper = ObjectMapper().findAndRegisterModules()
        val publisher = SqsIntegrationConfiguration().integrationEventPublisher(
            client,
            objectMapper,
            TransactionSignalProperties(
                enabled = true,
                queueUrl = "http://localhost/queue/transaction-signals",
            ),
        )
        val publishedAt = Instant.parse("2026-07-21T18:00:00Z")
        val envelope = """
            {
              "eventId":"01J6ZK7Q3W8K0M2N4P6R8T0V2A",
              "eventType":"TransactionSignalDetected",
              "eventVersion":1,
              "payload":{"signalId":"sig_01J6ZK7Q3W8K0M2N4P6R8T0V2F"}
            }
        """.trimIndent()

        publisher.publish(
            "01J6ZK7Q3W8K0M2N4P6R8T0V2A",
            "TransactionSignalDetected",
            envelope,
            publishedAt,
        )

        val body = objectMapper.readTree(request.captured.messageBody())
        assertThat(body.required("eventType").asText()).isEqualTo("TransactionSignalDetected")
        assertThat(body.required("publishedAt").asText()).isEqualTo(publishedAt.toString())
        assertThat(body.required("payload").required("signalId").asText()).startsWith("sig_")
        assertThat(body.has("envelope")).isFalse()
    }
}
