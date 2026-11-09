plugins {
    kotlin("jvm") version "2.4.21"
    application
    id("io.github.ben-manes.versions") version "0.67.0" // Gir oversikt over nyere dependencies med "./gradlew dependencyUpdates"
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("no.nav.arbeidsgiver.toi.presentertekandidater.ApplicationKt")
}

repositories {
    mavenCentral()

    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
    maven("https://jitpack.io")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

testing {
    suites {
        getByName<JvmTestSuite>("test") {
            useJUnitJupiter()
        }
    }
}

val javalinVersion = "7.2.4"
val micrometerVersion = "1.17.2"
val flywayVersion = "13.12.1"
val rapidsAndRiversVersion = "2026110308421793691732"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("io.javalin:javalin-micrometer:$javalinVersion")
    implementation("io.micrometer:micrometer-core:$micrometerVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
    implementation("io.prometheus:prometheus-metrics-exposition-textformats:1.10.0")

    implementation(platform("tools.jackson:jackson-bom:3.2.4"))
    implementation("tools.jackson.module:jackson-module-kotlin")

    implementation("ch.qos.logback:logback-classic:1.6.6")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("no.nav.security:token-validation-core:7.0.1")

    implementation("org.apache.kafka:kafka-clients:4.4.0")
    implementation("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")

    testImplementation(kotlin("test"))
    testImplementation("com.github.navikt.rapids-and-rivers:rapids-and-rivers-test:$rapidsAndRiversVersion")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("no.nav.security:mock-oauth2-server:6.1.4")
    testImplementation("org.testcontainers:testcontainers:2.0.5")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("io.mockk:mockk:1.14.11")
}
