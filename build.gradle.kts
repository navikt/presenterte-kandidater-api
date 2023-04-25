plugins {
    kotlin("jvm") version "1.8.20"
    id("com.github.johnrengelman.shadow") version "7.1.0"
    application
}

application {
    3
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

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.javalin:javalin:4.1.1")
    implementation("io.micrometer:micrometer-core:1.10.2")
    implementation("io.micrometer:micrometer-registry-prometheus:1.10.2")

    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-jackson:2.3.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")

    implementation("ch.qos.logback:logback-classic:1.4.4")
    implementation("net.logstash.logback:logstash-logback-encoder:7.2")

    implementation("org.flywaydb:flyway-core:9.8.1")
    implementation("org.postgresql:postgresql:42.5.1")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("no.nav.security:token-validation-core:2.1.8")

    implementation("org.apache.kafka:kafka-clients:3.4.0")
    implementation("com.github.navikt:rapids-and-rivers:2023041310341681374880.67ced5ad4dda")

    implementation("no.nav.arbeidsgiver:altinn-rettigheter-proxy-klient:3.1.0")

    implementation("no.nav.security:token-client-core:2.1.0")

    testImplementation(kotlin("test"))
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.0")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("no.nav.security:mock-oauth2-server:0.5.6")
    testImplementation("org.testcontainers:testcontainers:1.17.5")
    testImplementation("org.testcontainers:postgresql:1.17.5")
    testImplementation("org.testcontainers:junit-jupiter:1.17.5")
    testImplementation("io.mockk:mockk:1.13.2")
}
