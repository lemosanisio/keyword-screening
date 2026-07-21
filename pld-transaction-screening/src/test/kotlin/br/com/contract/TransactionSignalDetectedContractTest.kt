package br.com.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TransactionSignalDetectedContractTest {

    private val catalogDir: Path = Path.of(System.getProperty("user.dir"))
        .resolveSibling("pld-platform-docs/schemas/v1")

    private val mapper = ObjectMapper()
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    @Test
    fun `fixture dourada valida contra o schema TransactionSignalDetected_v1`() {
        val schema = schemaFactory.getSchema(
            catalogDir.resolve("TransactionSignalDetected.schema.json").toUri()
        )
        val fixture = mapper.readTree(
            Files.readString(catalogDir.resolve("fixtures/TransactionSignalDetected.json"))
        )

        val errors = schema.validate(fixture)

        assertThat(errors)
            .`as`("Fixture inválida:\n%s", errors.joinToString("\n"))
            .isEmpty()
    }
}
