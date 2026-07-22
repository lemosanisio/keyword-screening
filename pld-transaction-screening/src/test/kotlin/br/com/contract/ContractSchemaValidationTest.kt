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

    @Test
    fun `avaliacao failed valida com failureStage e failureCode sem outcome`() {
        val fixture = mapper.readTree(Files.readString(fixturePath("TransactionEvaluationCompletedV2")))
            as ObjectNode
        val payload = fixture.get("payload") as ObjectNode
        payload.put("executionStatus", "FAILED")
        payload.put("failureStage", "RULE_EVALUATION")
        payload.put("failureCode", "RULE_ENGINE_UNAVAILABLE")
        payload.remove("evaluationOutcome")
        payload.remove("reviewRequired")
        payload.remove("recommendedRoute")

        val errors = validate("TransactionEvaluationCompletedV2", mapper.writeValueAsString(fixture))

        assertThat(errors)
            .`as`("Avaliação FAILED válida:\n%s", errors.joinToString("\n"))
            .isEmpty()
    }

    @Test
    fun `avaliacao failed rejeita outcome e revisao`() {
        val fixture = mapper.readTree(Files.readString(fixturePath("TransactionEvaluationCompletedV2")))
            as ObjectNode
        val payload = fixture.get("payload") as ObjectNode
        payload.put("executionStatus", "FAILED")
        payload.put("failureStage", "RULE_EVALUATION")
        payload.put("failureCode", "RULE_ENGINE_UNAVAILABLE")

        val errors = validate("TransactionEvaluationCompletedV2", mapper.writeValueAsString(fixture))

        assertThat(errors).isNotEmpty()
    }

    @Test
    fun `avaliacao indeterminate exige fato indeterminado`() {
        val fixture = mapper.readTree(Files.readString(fixturePath("TransactionEvaluationCompletedV2")))
            as ObjectNode
        val payload = fixture.get("payload") as ObjectNode
        payload.put("executionStatus", "INDETERMINATE")
        payload.putArray("indeterminateFacts")

        val errors = validate("TransactionEvaluationCompletedV2", mapper.writeValueAsString(fixture))

        assertThat(errors).isNotEmpty()
    }

    @Test
    fun `fato nao presente exige reason code`() {
        val fixture = mapper.readTree(Files.readString(fixturePath("TransactionEvaluationCompletedV2")))
            as ObjectNode
        val payload = fixture.get("payload") as ObjectNode
        val unknownFact = payload.get("factsConsidered").get(1) as ObjectNode
        unknownFact.put("reasonCode", "")

        val errors = validate("TransactionEvaluationCompletedV2", mapper.writeValueAsString(fixture))

        assertThat(errors).isNotEmpty()
    }

    @Test
    fun `pedido humano rejeita rota de retry tecnico`() {
        val fixture = mapper.readTree(Files.readString(fixturePath("ManualReviewRequestedV2")))
            as ObjectNode
        val payload = fixture.get("payload") as ObjectNode
        payload.put("recommendedRoute", "TECHNICAL_RETRY")

        val errors = validate("ManualReviewRequestedV2", mapper.writeValueAsString(fixture))

        assertThat(errors).isNotEmpty()
    }

    private fun validate(eventName: String, json: String): Set<ValidationMessage> =
        schemaFactory
            .getSchema(catalogDir.resolve("$eventName.schema.json").toUri())
            .validate(mapper.readTree(json))

    private fun fixturePath(eventName: String): Path = fixturesDir.resolve("$eventName.json")
}
