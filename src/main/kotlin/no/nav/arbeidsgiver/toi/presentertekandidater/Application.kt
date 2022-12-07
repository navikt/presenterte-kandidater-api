package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.RapidApplication
import io.javalin.Javalin
import io.javalin.plugin.json.JavalinJackson
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.konfigurasjon.Databasekonfigurasjon
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.*
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.security.token.support.core.configuration.IssuerProperties
import java.util.TimeZone

val defaultObjectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

fun main() {
    val env = System.getenv()
    val issuerProperties = hentIssuerPropertiesForTokenX(env)
    val tokendingsKlient = TokendingsKlient(env)
    val altinnKlient = AltinnKlient(env, tokendingsKlient)

    val javalin = opprettJavalinMedTilgangskontroll(issuerProperties, altinnKlient)

    val datasource = Databasekonfigurasjon(env).lagDatasource()
    val repository = Repository(datasource)
    repository.kjørFlywayMigreringer()

    val openSearchKlient = OpenSearchKlient(env)
    val presenterteKandidaterService = PresenterteKandidaterService(repository)


    lateinit var rapidIsAlive: () -> Boolean
    val rapidsConnection = RapidApplication.create(env, configure = { _, kafkaRapid ->
        rapidIsAlive = kafkaRapid::isRunning
    })

    startApp(
        javalin,
        rapidsConnection,
        presenterteKandidaterService,
        repository,
        openSearchKlient,
        KonverteringFilstier(env),
        rapidIsAlive,
    )
}

fun startApp(
    javalin: Javalin,
    rapidsConnection: RapidsConnection,
    presenterteKandidaterService: PresenterteKandidaterService,
    repository: Repository,
    openSearchKlient: OpenSearchKlient,
    konverteringFilstier: KonverteringFilstier,
    rapidIsAlive: () -> Boolean,
) {
    javalin.get("/isalive", { it.status(if (rapidIsAlive()) 200 else 500) }, Rolle.UNPROTECTED)
    startController(javalin, repository, openSearchKlient, konverteringFilstier)
    startPeriodiskSlettingAvKandidaterOgKandidatlister(repository)

    rapidsConnection.also {
        PresenterteKandidaterLytter(it, presenterteKandidaterService)
    }.start()
}

fun opprettJavalinMedTilgangskontroll(
    issuerProperties: IssuerProperties,
    altinnKlient: AltinnKlient
): Javalin = Javalin.create {
    it.defaultContentType = "application/json"
    it.accessManager(styrTilgang(issuerProperties, altinnKlient))
    it.jsonMapper(
        JavalinJackson(
            jacksonObjectMapper().registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .setTimeZone(TimeZone.getTimeZone("Europe/Oslo"))
        )
    )
}.start(9000)
