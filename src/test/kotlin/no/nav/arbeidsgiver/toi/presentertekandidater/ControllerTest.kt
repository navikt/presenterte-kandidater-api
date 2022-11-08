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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
    fun `GET mot kandidaterliste med kandidater gir status 200`() {
        val stillingId = UUID.fromString("4bd2c240-92d2-4166-ac54-ba3d21bfbc07")
        repository.lagre(RepositoryTest.GYLDIG_KANDIDATLISTE.copy(stillingId = stillingId))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        val hendelsestidspunkt = LocalDateTime.of(2022, 1, 1, 0, 0, 0)
        repository.lagre(
            Kandidat(
                aktørId = "test",
                arbeidsgiversStatus = "status",
                kandidatlisteId = kandidatliste?.id!!,
                hendelsestype = "type",
                hendelsestidspunkt = hendelsestidspunkt
            )
        )
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidatliste/$stillingId")
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(response.body().asString("application/json;charset=utf-8"))
            .isEqualTo(("""
                {
                  "stillingId": "4bd2c240-92d2-4166-ac54-ba3d21bfbc07",
                  "tittel": "Tittel",
                  "status": "Status",
                  "slettet": false,
                  "virksomhetsnummer": "123456789",
                  "kandidater": [
                    {
                      "aktørId": "test",
                      "hendelsestidspunkt": "2022-01-01T00:00:00",
                      "hendelsestype": "type",
                      "arbeidsgiversStatus": "status"
                    }
                  ]
                }
            """.filter { !it.isWhitespace() })
            )
    }

    @Test
    fun `GET mot kandidaterliste gir status 200`() {
        repository.lagre(
            RepositoryTest.GYLDIG_KANDIDATLISTE.copy(
                virksomhetsnummer = "123456788",
                stillingId = UUID.fromString("4bd2c240-92d2-4166-ac54-ba3d21bfbc07")
            )
        )
        val (_, response) = Fuel
            .get("http://localhost:9000/kandidatlister?virksomhetsnummer=123456788")
            .authentication().bearer(hentToken(mockOAuth2Server))
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(response.body().asString("application/json;charset=utf-8"))
            .isEqualTo(("""
                [
                  {
                    "kandidatliste": {
                      "stillingId": "4bd2c240-92d2-4166-ac54-ba3d21bfbc07",
                      "tittel": "Tittel",
                      "status": "Status",
                      "slettet": false,
                      "virksomhetsnummer": "123456788"
                    },
                    "antallKandidater": 0
                  }
                ]
            """.filter { !it.isWhitespace() })
            )
    }
}
