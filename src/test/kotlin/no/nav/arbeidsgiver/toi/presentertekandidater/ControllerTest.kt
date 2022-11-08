package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import io.javalin.Javalin
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControllerTest {

    private val mockOAuth2Server = MockOAuth2Server()
    private lateinit var javalin: Javalin
    private val repository = opprettTestRepositoryMedLokalPostgres()

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18301)
        javalin = opprettJavalinMedTilgangskontroll(issuerProperties)
        startLocalApplication(javalin = javalin, repository = repository)
    }

    @AfterAll
    fun cleanUp() {
        mockOAuth2Server.shutdown()
        javalin.stop()
    }

    @Test
    fun `GET mot kandidater-endepunkt gir httpstatus 200`() {
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidater")
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `GET mot kandidater-endepunkt gir httpstatus 403 uten et gyldig token`() {
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidater")
            .authentication().bearer(hentUgyldigToken(mockOAuth2Server))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `GET mot kandidater-endepunkt gir httpstatus 403 uten token`() {
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidater")
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `GET mot kandidaterlister gir status 200`() {
        repository.lagre(RepositoryTest.GYLDIG_KANDIDATLISTE.copy(stillingId = UUID.fromString("4bd2c240-92d2-4166-ac54-ba3d21bfbc07")))
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=123456789")
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(response.body().asString("application/json"))
            .isEqualTo(("""
                [
                  {
                    "kandidatliste": {
                      "id": 1,
                      "stillingId": "4bd2c240-92d2-4166-ac54-ba3d21bfbc07",
                      "tittel": "Tittel",
                      "status": "Status",
                      "slettet": false,
                      "virksomhetsnummer": "123456789"
                    },
                    "antallKandidater": 0
                  }
                ]
            """.filter { !it.isWhitespace() }))
    }
}
