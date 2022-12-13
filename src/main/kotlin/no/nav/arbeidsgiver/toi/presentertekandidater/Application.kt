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
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.PresenterteKandidaterLytter
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.PresenterteKandidaterService
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.OpenSearchKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.KandidatlisteRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.startPeriodiskSlettingAvKandidaterOgKandidatlister
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
    val kandidatlisteRepository = KandidatlisteRepository(datasource)
    kandidatlisteRepository.kjørFlywayMigreringer()

    val openSearchKlient = OpenSearchKlient(env)
    val presenterteKandidaterService = PresenterteKandidaterService(kandidatlisteRepository)


    lateinit var rapidIsAlive: () -> Boolean
    val rapidsConnection = RapidApplication.create(env, configure = { _, kafkaRapid ->
        rapidIsAlive = kafkaRapid::isRunning
    })

    startApp(
        javalin,
        rapidsConnection,
        presenterteKandidaterService,
        kandidatlisteRepository,
        openSearchKlient,
        KonverteringFilstier(env),
        rapidIsAlive,
    )
}

fun startApp(
    javalin: Javalin,
    rapidsConnection: RapidsConnection,
    presenterteKandidaterService: PresenterteKandidaterService,
    kandidatlisteRepository: KandidatlisteRepository,
    openSearchKlient: OpenSearchKlient,
    konverteringFilstier: KonverteringFilstier,
    rapidIsAlive: () -> Boolean,
) {
    javalin.get("/isalive", { it.status(if (rapidIsAlive()) 200 else 500) }, Rolle.UNPROTECTED)
    startController(javalin, kandidatlisteRepository, openSearchKlient, konverteringFilstier)
    startPeriodiskSlettingAvKandidaterOgKandidatlister(kandidatlisteRepository)

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
