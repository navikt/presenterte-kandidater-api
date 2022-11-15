package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.RapidApplication
import io.javalin.Javalin
import io.javalin.plugin.json.JavalinJackson
import no.nav.arbeidsgiver.toi.presentertekandidater.konfigurasjon.Databasekonfigurasjon
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.Rolle
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.styrTilgang
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.security.token.support.core.configuration.IssuerProperties
import java.util.TimeZone

val defaultObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

fun main() {
    val env = System.getenv()
    val issuerProperties = hentIssuerProperties(env)
    val javalin = opprettJavalinMedTilgangskontroll(issuerProperties)

    val datasource = Databasekonfigurasjon(env).lagDatasource()
    val repository = Repository(datasource)
    repository.kjørFlywayMigreringer()

    val openSearchKlient = OpenSearchKlient(env)

    val presenterteKandidaterService = PresenterteKandidaterService(repository)

    lateinit var rapidIsAlive: () -> Boolean
    val rapidsConnection = RapidApplication.create(env, configure = { _, kafkaRapid ->
        rapidIsAlive = kafkaRapid::isRunning
    })

    startApp(javalin, rapidsConnection, presenterteKandidaterService, repository, openSearchKlient, rapidIsAlive)
}

fun startApp(
    javalin: Javalin,
    rapidsConnection: RapidsConnection,
    presenterteKandidaterService: PresenterteKandidaterService,
    repository: Repository,
    openSearchKlient: OpenSearchKlient,
    rapidIsAlive: () -> Boolean,
) {
    javalin.get("/isalive", { it.status(if (rapidIsAlive()) 200 else 500) }, Rolle.UNPROTECTED)
    startKandidatlisteController(javalin, repository, openSearchKlient)

    rapidsConnection.also {
        PresenterteKandidaterLytter(it, presenterteKandidaterService)
    }.start()
}

fun opprettJavalinMedTilgangskontroll(
    issuerProperties: Map<Rolle, IssuerProperties>
): Javalin =
    Javalin.create {
        it.defaultContentType = "application/json"
        it.accessManager(styrTilgang(issuerProperties))
        it.jsonMapper(
            JavalinJackson(
                jacksonObjectMapper()
                    .registerModule(JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                    .setTimeZone(TimeZone.getTimeZone("Europe/Oslo"))
            )
        )
    }.start(9000)
