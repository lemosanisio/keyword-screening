import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.openapi.generator") version "7.10.0"
    jacoco
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
}

group = "br.com.screening"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Database
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // JSONB support via hypersistence-utils
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.8.3")

    // Jakarta validation (necessário para código gerado pelo OpenAPI Generator)
    implementation("jakarta.validation:jakarta.validation-api")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.networknt:json-schema-validator:1.5.6")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.3")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// OpenAPI Generator — API First: gera interfaces Kotlin a partir do openapi.yaml
// ═══════════════════════════════════════════════════════════════════════════════
val openApiOutputDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set("$projectDir/src/main/resources/static/openapi/openapi.yaml")
    outputDir.set(openApiOutputDir.map { it.asFile.absolutePath }.get())

    apiPackage.set("br.com.generated.api")
    modelPackage.set("br.com.generated.model")

    configOptions.set(mapOf(
        "interfaceOnly" to "true",
        "useTags" to "true",
        "useSpringBoot3" to "true",
        "documentationProvider" to "none",
        "reactive" to "false",
        "serviceInterface" to "false",
        "skipDefaultInterface" to "true",
        "enumPropertyNaming" to "UPPERCASE",
        "exceptionHandler" to "false",
        "gradleBuildFile" to "false",
        "applicationName" to "screening"
    ))

    globalProperties.set(mapOf(
        "apis" to "",
        "models" to "",
        "supportingFiles" to ""
    ))
}

// Adicionar código gerado ao source set
sourceSets {
    main {
        kotlin {
            srcDir(openApiOutputDir.map { it.dir("src/main/kotlin") })
        }
    }
}

// Garantir que geração ocorre antes da compilação
tasks.withType<KotlinCompile> {
    dependsOn("openApiGenerate")
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxHeapSize = "1g"
    jvmArgs = listOf(
        "-XX:+UseG1GC",
        "-XX:MaxMetaspaceSize=512m"
    )
    forkEvery = 100
}

tasks.test {
    exclude("**/integration/**", "**/*IntegrationTest*")
    // Catálogo de contratos vive fora do projeto: mudança em schema/fixture deve re-rodar os testes
    inputs.dir(file("../pld-platform-docs/schemas"))
}

tasks.register<Test>("integrationTest") {
    include("**/integration/**", "**/*IntegrationTest*")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.test)
}

// ═══════════════════════════════════════════════════════════════════════════════
// JaCoCo — Cobertura de código
// ═══════════════════════════════════════════════════════════════════════════════
jacoco {
    toolVersion = "0.8.12"
}

val jacocoExclusions = listOf(
    // Código gerado pelo OpenAPI Generator
    "br/com/generated/**",

    // Application class
    "**/KeywordScreeningApplication*",

    // Configurações Spring (@Configuration, @ConfigurationProperties)
    "**/infrastructure/configuration/**",

    // Interfaces — use case (input ports)
    "br/com/screening/application/usecase/EvaluateKeywordScreeningUseCase*",
    "br/com/screening/application/usecase/EvaluateContextualScreeningUseCase*",
    "br/com/screening/application/usecase/RegisterAnalystDecisionUseCase*",
    "br/com/alert/application/usecase/CreateAlertUseCase*",
    "br/com/alert/application/usecase/QueryAlertUseCase*",
    "br/com/alert/application/usecase/UpdateAlertStatusUseCase*",
    "br/com/decision/application/usecase/ExecuteDecisionUseCase*",
    "br/com/decision/application/usecase/ExecuteDryRunUseCase*",
    "br/com/decision/application/usecase/ManageRuleConfigurationUseCase*",
    "br/com/decision/application/usecase/QueryDecisionExecutionUseCase*",

    // Interfaces — domain ports (output ports)
    "br/com/screening/domain/port/**",
    "br/com/screening/domain/repository/**",
    "br/com/alert/domain/port/**",
    "br/com/decision/domain/port/**",

    // Interfaces — shared domain
    "br/com/shared/domain/DomainEvent*",
    "br/com/shared/domain/DomainEventPublisher*",
    "br/com/screening/domain/ScreeningRule*",
    "br/com/decision/domain/service/FactResolver*",

    // Interfaces — JPA repositories (Spring Data auto-implementa)
    "**/*JpaRepository*",

    // Value Objects (sem lógica)
    "br/com/shared/domain/valueobject/**",
    "br/com/alert/domain/model/vo/**",
    "br/com/decision/domain/model/vo/**",

    // Commands e Results (data classes puras)
    "**/*Command*",
    "**/*Result*",
    "**/*ResultDto*",

    // Domain models sem lógica (data classes puras)
    "br/com/screening/domain/model/ContextualScreeningAudit*",
    "br/com/screening/domain/model/ContextualScreeningResult*",
    "br/com/screening/domain/model/HistoricalDecision*",
    "br/com/screening/domain/model/MatchResult*",
    "br/com/screening/domain/model/RestrictedTerm*",
    "br/com/screening/domain/model/ScreeningResult*",
    "br/com/screening/domain/model/RuleExecution*",
    "br/com/decision/domain/model/DecisionExecution*",
    "br/com/decision/domain/model/DecisionResult*",
    "br/com/decision/domain/model/DecisionExplanation*",
    "br/com/decision/domain/model/DryRunLog*",
    "br/com/decision/domain/model/EntityDefinition*",
    "br/com/decision/domain/model/Expression*",
    "br/com/decision/domain/model/Condition*",
    "br/com/decision/domain/model/Group*",
    "br/com/decision/domain/model/LogicalOperator*",
    "br/com/decision/domain/model/ExpressionEvaluation*",
    "br/com/decision/domain/model/FactDefinition*",
    "br/com/decision/domain/model/RuleDefinition*",
    "br/com/decision/domain/model/RuleConfiguration*",
    "br/com/decision/domain/model/ConfigurationVersionEntry*",
    "br/com/decision/domain/model/ReceptionStep*",
    "br/com/decision/domain/model/RuleIdentificationStep*",
    "br/com/decision/domain/model/ContextBuildingStep*",
    "br/com/decision/domain/model/ResolverResult*",
    "br/com/decision/domain/model/ResolverOutcome*",
    "br/com/decision/domain/model/EvaluationStep*",
    "br/com/decision/domain/model/DecisionStep*",
    "br/com/decision/domain/model/PersistenceStep*",
    "br/com/decision/domain/model/PublicationStep*",
    "br/com/decision/domain/model/ExplanationStep*",

    // Domain events (data classes puras)
    "br/com/decision/domain/event/**",

    // Enums (sem lógica)
    "br/com/screening/domain/model/Category*",
    "br/com/screening/domain/model/Classification*",
    "br/com/decision/domain/model/enums/**",

    // Exceções de domínio (sem lógica)
    "br/com/shared/domain/DomainException*",
    "**/domain/exception/**",

    // DTOs HTTP (data classes puras)
    "**/infrastructure/input/http/dto/**",
    "**/infrastructure/input/http/handler/ErrorResponse*",

    // JPA Entities (data holders gerenciados pelo framework)
    "**/infrastructure/output/persistence/entity/**",

    // Repository implementations (dependem de JPA/banco — cobertos por testes de integração)
    "**/infrastructure/output/persistence/*RepositoryImpl*",
    "**/infrastructure/output/persistence/repository/*RepositoryImpl*"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    classDirectories.setFrom(
        files(sourceSets.main.get().output.classesDirs.files.map {
            fileTree(it) { exclude(jacocoExclusions) }
        })
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)

    classDirectories.setFrom(
        files(sourceSets.main.get().output.classesDirs.files.map {
            fileTree(it) { exclude(jacocoExclusions) }
        })
    )

    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.98".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
