package br.com.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractSchemaValidationTest {

    private val catalogDir: Path = Path.of(System.getProperty("user.dir"))
        .resolveSibling("pld-platform-docs/schemas/v1")
    private val fixturesDir: Path = catalogDir.resolve("fixtures")

    private val mapper = ObjectMapper()
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    private fun goldenFixtures(): List<String> {
        val fixtures = Files.list(fixturesDir).use { stream ->
            stream
                .filter { it.toString().endsWith(".json") }
                .map { it.fileName.nameWithoutExtension }
                .sorted()
                .toList()
        }
        assertThat(fixtures).`as`("Nenhuma fixture encontrada em %s", fixturesDir).isNotEmpty()
        return fixtures
    }

    @ParameterizedTest(name = "fixture {0} valida contra o schema de mesmo nome")
    @MethodSource("goldenFixtures")
    fun `fixture dourada valida contra o schema`(eventName: String) {
        val errors = validate(eventName, Files.readString(fixturePath(eventName)))

        assertThat(errors)
            .`as`("Fixture %s inválida:\n%s", eventName, errors.joinToString("\n"))
            .isEmpty()
    }

    @Test
    fun `consumidor tolera campo desconhecido aditivo`() {
        val fixture = mapper.readTree(Files.readString(fixturePath("TransactionSignalDetected")))
            as ObjectNode
        fixture.put("campoAditivoFuturo", "valor-qualquer")
        (fixture.get("payload") as ObjectNode).put("campoAditivoFuturo", 42)

        val errors = validate("TransactionSignalDetected", mapper.writeValueAsString(fixture))

        assertThat(errors)
            .`as`("Campo aditivo não pode quebrar a validação:\n%s", errors.joinToString("\n"))
            .isEmpty()
    }

    private fun validate(eventName: String, json: String): Set<ValidationMessage> =
        schemaFactory
            .getSchema(catalogDir.resolve("$eventName.schema.json").toUri())
            .validate(mapper.readTree(json))

    private fun fixturePath(eventName: String): Path = fixturesDir.resolve("$eventName.json")
}
