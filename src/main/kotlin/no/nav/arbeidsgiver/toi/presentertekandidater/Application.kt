package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.javalin.json.JavalinJackson3
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.arbeidsgiver.toi.presentertekandidater.SecureLogLogger.Companion.secure
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.hendelser.*
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.KandidatlisteRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.startPeriodiskSlettingAvKandidaterOgKandidatlister
import no.nav.arbeidsgiver.toi.presentertekandidater.konfigurasjon.Databasekonfigurasjon
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.startJavalin
import no.nav.arbeidsgiver.toi.presentertekandidater.opensearch.OpenSearchKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.samtykke.SamtykkeRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.TokendingsKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.konfigurerRoller
import no.nav.arbeidsgiver.toi.presentertekandidater.statistikk.StatistikkMetrikkJobb
import no.nav.arbeidsgiver.toi.presentertekandidater.statistikk.StatistikkRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.visningkontaktinfo.VisningKontaktinfoPubliserer
import no.nav.arbeidsgiver.toi.presentertekandidater.visningkontaktinfo.VisningKontaktinfoRepository
import no.nav.helse.rapids_rivers.RapidApplication
import org.flywaydb.core.Flyway
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.util.TimeZone
import javax.sql.DataSource

private val logger = noClassLogger()

val defaultObjectMapper: JsonMapper = jacksonMapperBuilder()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    .defaultTimeZone(TimeZone.getTimeZone("Europe/Oslo"))
    .accessorNaming(
        DefaultAccessorNamingStrategy.Provider()
            .withFirstCharAcceptance(true, true)
    )
    .build()

fun main() {
    logger.info("Starter app.")
    secure(logger).info("Starter app. Dette er ment å logges til Securelogs. Hvis du ser dette i den ordinære apploggen er noe galt, og sensitive data kan havne i feil logg.")

    val env = System.getenv()
    val tokendingsKlient = TokendingsKlient(env)
    val altinnKlient = AltinnKlient(env, tokendingsKlient)
    val openSearchKlient = OpenSearchKlient(env)
    val databasekonfigurasjon = Databasekonfigurasjon(env)
    val dataSource = databasekonfigurasjon.lagDatasource()

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
        jsonMapper = JavalinJackson3(defaultObjectMapper),
        miljøvariabler = envs,
        rapidIsAlive = rapidIsAlive,
        registry = prometheusRegistry,
        kandidatlisteRepository = kandidatlisteRepository,
        samtykkeRepository = samtykkeRepository,
        visningKontaktinfoRepository = visningKontaktinfoRepository,
        openSearchKlient = openSearchKlient
    )

    startPeriodiskSlettingAvKandidaterOgKandidatlister(kandidatlisteRepository)
    statistikkMetrikkJobb.start()

    logger.info("Starter Kafka-lytting")

    val konsumerUtløptSamtykkeMeldinger = envs["KONSUMER_UTLOPT_SAMTYKKE"].equals("true", ignoreCase = true)
    rapidsConnection.also {
        if (konsumerUtløptSamtykkeMeldinger) {
            TrukketSamtykkeLytter(it, prometheusRegistry, presenterteKandidaterService)
            UtløptSamtykkeLytter(it, prometheusRegistry, presenterteKandidaterService)
        }
        CvDeltLytter(it, NotifikasjonPubliserer(it), prometheusRegistry, presenterteKandidaterService)
        KandidatlisteLukketLytter(it, prometheusRegistry, presenterteKandidaterService)
        SlettetStillingLytter(it, prometheusRegistry, presenterteKandidaterService)
        SlettFraArbeidsgiversKandidatlisteLytter(it, prometheusRegistry, presenterteKandidaterService)
        VisningKontaktinfoPubliserer(it, visningKontaktinfoRepository)
        OpprettetKandidatlisteLytter(it, presenterteKandidaterService)
        logger.info("Startet lytter")
    }.start()
}

fun kjørFlywayMigreringer(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
}
