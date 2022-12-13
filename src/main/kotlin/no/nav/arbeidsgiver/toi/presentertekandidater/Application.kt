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
import org.flywaydb.core.Flyway
import java.util.TimeZone
import javax.sql.DataSource

val defaultObjectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

fun main() {
    val env = System.getenv()
    val issuerProperties = hentIssuerPropertiesForTokenX(env)
    val tokendingsKlient = TokendingsKlient(env)
    val altinnKlient = AltinnKlient(env, tokendingsKlient)
    val javalin = opprettJavalinMedTilgangskontroll(issuerProperties, altinnKlient)

    val databasekonfigurasjon = Databasekonfigurasjon(env)
    val dataSource = databasekonfigurasjon.lagDatasource()

    val openSearchKlient = OpenSearchKlient(env)

    lateinit var rapidIsAlive: () -> Boolean
    val rapidsConnection = RapidApplication.create(env, configure = { _, kafkaRapid ->
        rapidIsAlive = kafkaRapid::isRunning
    })

    startApp(
        javalin,
        rapidsConnection,
        dataSource,
        KonverteringFilstier(env),
        openSearchKlient,
        rapidIsAlive,
    )
}

fun startApp(
    javalin: Javalin,
    rapidsConnection: RapidsConnection,
    dataSource: DataSource,
    konverteringFilstier: KonverteringFilstier,
    openSearchKlient: OpenSearchKlient,
    rapidIsAlive: () -> Boolean,
) {
    kjørFlywayMigreringer(dataSource)
    val kandidatlisteRepository = KandidatlisteRepository(dataSource)
    val presenterteKandidaterService = PresenterteKandidaterService(kandidatlisteRepository)
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

fun kjørFlywayMigreringer(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
}