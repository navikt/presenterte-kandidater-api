package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.javalin.plugin.json.JavalinJackson
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.exporter.common.TextFormat
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.NotifikasjonPubliserer
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.PresenterteKandidaterLytter
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.PresenterteKandidaterService
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.KandidatlisteRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.opensearch.OpenSearchKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.startPeriodiskSlettingAvKandidaterOgKandidatlister
import no.nav.arbeidsgiver.toi.presentertekandidater.konfigurasjon.Databasekonfigurasjon
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.startJavalin
import no.nav.arbeidsgiver.toi.presentertekandidater.samtykke.SamtykkeRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.Rolle
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.TokendingsKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.konfigurerRoller
import no.nav.arbeidsgiver.toi.presentertekandidater.statistikk.StatistikkMetrikkJobb
import no.nav.arbeidsgiver.toi.presentertekandidater.statistikk.StatistikkRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.visningkontaktinfo.VisningKontaktinfoRepository
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.flywaydb.core.Flyway
import java.util.*
import javax.sql.DataSource

val defaultObjectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

fun main() {
    val env = System.getenv()
    val tokendingsKlient = TokendingsKlient(env)
    val altinnKlient = AltinnKlient(env, tokendingsKlient)

    val databasekonfigurasjon = Databasekonfigurasjon(env)
    val dataSource = databasekonfigurasjon.lagDatasource()

    val openSearchKlient = OpenSearchKlient(env)

    lateinit var rapidIsAlive: () -> Boolean
    val rapidsConnection = RapidApplication.create(env, configure = { _, kafkaRapid ->
        rapidIsAlive = kafkaRapid::isRunning
    })

    startApp(
        rapidsConnection,
        dataSource,
        openSearchKlient,
        rapidIsAlive,
        altinnKlient,
        env
    )
}

fun startApp(
    rapidsConnection: RapidsConnection,
    dataSource: DataSource,
    openSearchKlient: OpenSearchKlient,
    rapidIsAlive: () -> Boolean,
    altinnKlient: AltinnKlient,
    envs: Map<String, String>
) {
    val samtykkeRepository = SamtykkeRepository(dataSource)
    kjørFlywayMigreringer(dataSource)
    val kandidatlisteRepository = KandidatlisteRepository(dataSource)
    val visningKontaktinfoRepository = VisningKontaktinfoRepository(dataSource)
    val presenterteKandidaterService = PresenterteKandidaterService(kandidatlisteRepository)

    val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val rollekonfigurasjon = konfigurerRoller(altinnKlient, samtykkeRepository)

    val statistikkRepository = StatistikkRepository(dataSource)
    val statistikkMetrikkJobb = StatistikkMetrikkJobb(statistikkRepository, openSearchKlient, prometheusRegistry)

    val javalin = startJavalin(
        rollekonfigurasjoner = rollekonfigurasjon,
        jsonMapper = JavalinJackson(
            jacksonObjectMapper().registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .setTimeZone(TimeZone.getTimeZone("Europe/Oslo"))
        ),
        miljøvariabler = envs,
        registry = prometheusRegistry
    )
    javalin.get("/isalive", { it.status(if (rapidIsAlive()) 200 else 500) }, Rolle.UNPROTECTED)
    javalin.get(
        "/internal/prometheus",
        { it.contentType(TextFormat.CONTENT_TYPE_004).result(prometheusRegistry.scrape()) }, Rolle.UNPROTECTED
    )


    startController(javalin, kandidatlisteRepository, samtykkeRepository, visningKontaktinfoRepository, openSearchKlient)
    startPeriodiskSlettingAvKandidaterOgKandidatlister(kandidatlisteRepository)
    statistikkMetrikkJobb.start()

    log("ApplicationKt").info("Starter Kafka-lytting")
    rapidsConnection.also {
        PresenterteKandidaterLytter(it, NotifikasjonPubliserer(it), prometheusRegistry, presenterteKandidaterService)
        log("Application").info("Startet lytter")
    }.start()
}

fun kjørFlywayMigreringer(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
}