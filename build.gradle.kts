plugins {
    kotlin("jvm") version "1.9.25"
    application
    id("com.github.ben-manes.versions") version "0.51.0" // Gir oversikt over nyere dependencies med "./gradlew dependencyUpdates"
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
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


// TODO Are: Funker dette?
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Step 1: Find the rapids-and-rivers JAR(s) in the runtime classpath
    val rapidsAndRiversJars = configurations.runtimeClasspath.get()
        .filter { it.name.contains("rapids-and-rivers") }

    // Step 2: Remove the rapids-and-rivers JAR(s) from the final JAR
    exclude(rapidsAndRiversJars.map { it.name })

    // Step 3: Re-add rapids-and-rivers manually, but exclude logback.xml
    from({
        rapidsAndRiversJars.map { zipTree(it) }
    }) {
        exclude("logback.xml")
        exclude("META-INF/**/logback.xml")
    }
}




tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.javalin:javalin:4.6.8")
    implementation("io.micrometer:micrometer-core:1.10.2")
    implementation("io.micrometer:micrometer-registry-prometheus:1.10.2")

    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-jackson:2.3.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")

    implementation("ch.qos.logback:logback-classic:1.4.4")
    implementation("net.logstash.logback:logstash-logback-encoder:7.2")

    implementation("org.flywaydb:flyway-core:9.8.1")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("no.nav.security:token-validation-core:2.1.8")

    implementation("org.apache.kafka:kafka-clients:3.9.0")
    implementation("com.github.navikt:rapids-and-rivers:2023041310341681374880.67ced5ad4dda")

    implementation("no.nav.arbeidsgiver:altinn-rettigheter-proxy-klient:3.1.0")

    implementation("no.nav.security:token-client-core:2.1.0")

    testImplementation(kotlin("test"))
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.1")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("no.nav.security:mock-oauth2-server:0.5.6")
    testImplementation("org.testcontainers:testcontainers:1.17.5")
    testImplementation("org.testcontainers:postgresql:1.17.5")
    testImplementation("org.testcontainers:junit-jupiter:1.17.5")
    testImplementation("io.mockk:mockk:1.13.2")
    testImplementation("uk.org.webcompere:ModelAssert:1.0.0")
}
