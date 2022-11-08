package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.RapidApplication
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.http.Context
import io.javalin.plugin.json.JavalinJackson
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.security.token.support.core.configuration.IssuerProperties
import java.util.TimeZone
import java.util.UUID

fun startApp(
    javalin: Javalin,
    rapidsConnection: RapidsConnection,
    presenterteKandidaterService: PresenterteKandidaterService,
    repository: Repository,
    rapidIsAlive: () -> Boolean,
) {
    javalin.routes {
        get("/isalive", isAlive(rapidIsAlive), Rolle.UNPROTECTED)
        get("/kandidater", hentKandidater(/*repository::hentKandidater*/), Rolle.ARBEIDSGIVER)
        get("/kandidatliste/{stillingId}", hentKandidatlisteMedKandidater(repository), Rolle.ARBEIDSGIVER)
        get("/kandidatlister", hentKandidatlister(repository), Rolle.ARBEIDSGIVER)
    }

    rapidsConnection.also {
        PresenterteKandidaterLytter(it, presenterteKandidaterService)
    }.start()
}

val hentKandidatlister: (repository: Repository) -> (Context) -> Unit = {repository ->
    { context ->
        val virksomhetsnummer = context.queryParam("virksomhetsnummer")
        if(virksomhetsnummer.isNullOrBlank()) {
           context.status(400)
        } else {
            val lister = repository.hentKandidatlisterMedAntall(virksomhetsnummer)
            context.json(lister).status(200)
        }
    }
}

val hentKandidatlisteMedKandidater: (repository: Repository) -> (Context) -> Unit = { repository ->
    { context ->
        val stillingId = context.pathParam("stillingId")
        if (stillingId.isNullOrBlank()) {
           context.status(400)
        } else {
            val liste = repository.hentKandidatlisteMedKandidater(UUID.fromString(stillingId))
            if (liste == null) {
                context.status(404)
            } else {
                context.json(liste).status(200)
            }
        }
    }
}

val hentKandidater: () -> (Context) -> Unit = {
    { context ->
        context.json("heisann").status(200)
    }
}

private val isAlive: (() -> Boolean) -> (Context) -> Unit = { isAlive ->
    { context ->
        context.status(if (isAlive()) 200 else 500)
    }
}

fun opprettJavalinMedTilgangskontroll(
    issuerProperties: Map<Rolle, IssuerProperties>
): Javalin =
    Javalin.create {
        it.defaultContentType = "application/json"
        it.accessManager(styrTilgang(issuerProperties))
        it.jsonMapper(JavalinJackson(
            jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .setTimeZone(TimeZone.getTimeZone("Europe/Oslo"))
        ))
    }.start(9000)

fun main() {
    val env = System.getenv()
    val issuerProperties = hentIssuerProperties(System.getenv())
    val javalin = opprettJavalinMedTilgangskontroll(issuerProperties)

    val datasource = Databasekonfigurasjon(env).lagDatasource()
    val repository = Repository(datasource)
    repository.kjørFlywayMigreringer()

    val presenterteKandidaterService = PresenterteKandidaterService(repository)

    lateinit var rapidIsAlive: () -> Boolean
    val rapidsConnection = RapidApplication.create(env, configure = { _, kafkaRapid ->
        rapidIsAlive = kafkaRapid::isRunning
    })

    startApp(javalin, rapidsConnection, presenterteKandidaterService,repository, rapidIsAlive )
}
