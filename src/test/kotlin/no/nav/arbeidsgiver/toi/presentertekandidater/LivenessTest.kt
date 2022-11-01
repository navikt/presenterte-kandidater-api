package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.kittinunf.fuel.Fuel
import io.javalin.Javalin
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LivenessTest {
    private lateinit var javalin: Javalin

    @BeforeAll
    fun init() {
        javalin = opprettJavalinMedTilgangskontroll(issuerProperties)

        startLocalApplication(javalin)
    }

    @AfterAll
    fun cleanUp() {
        javalin.stop()
    }

    @Test
    fun `Applikasjonen skal svare 200 OK på isalive-endepunkt`() {
        val (_, response) = Fuel.get("http://localhost:9000/isalive").response()

        assertThat(response.statusCode).isEqualTo(200)
    }
}