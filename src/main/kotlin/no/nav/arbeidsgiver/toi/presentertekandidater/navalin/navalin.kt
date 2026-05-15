package no.nav.arbeidsgiver.toi.presentertekandidater.navalin

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.json.JavalinJackson
import io.javalin.json.JsonMapper
import io.javalin.micrometer.MicrometerPlugin
import io.javalin.router.JavalinDefaultRoutingApi
import io.javalin.security.RouteRole
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.exporter.common.TextFormat
import no.nav.arbeidsgiver.toi.presentertekandidater.SecureLogLogger.Companion.secure
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnException
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnServiceException
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnTilgangException
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.KandidatlisteRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.NavalinAccessManager.TokenUtsteder
import no.nav.arbeidsgiver.toi.presentertekandidater.noClassLogger
import no.nav.arbeidsgiver.toi.presentertekandidater.opensearch.OpenSearchKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.samtykke.SamtykkeRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.Rolle
import no.nav.arbeidsgiver.toi.presentertekandidater.startController
import no.nav.arbeidsgiver.toi.presentertekandidater.visningkontaktinfo.VisningKontaktinfoRepository
import no.nav.security.token.support.core.jwt.JwtTokenClaims

private val logger = noClassLogger()

fun startJavalin(
    port: Int = 9000,
    rollekonfigurasjoner: List<RolleKonfigurasjon>,
    miljøvariabler: Map<String, String>,
    defaultContentType: String = "application/json",
    jsonMapper: JsonMapper = JavalinJackson(),
    registry: PrometheusMeterRegistry,
    rapidIsAlive: () -> Boolean,
    kandidatlisteRepository: KandidatlisteRepository,
    samtykkeRepository: SamtykkeRepository,
    visningKontaktinfoRepository: VisningKontaktinfoRepository,
    openSearchKlient: OpenSearchKlient,
): Javalin {
    require(rollekonfigurasjoner.isNotEmpty()) { "Støtter ikke opprettelse av Javalin uten rollekonfigurasjon" }

    val accessManager = NavalinAccessManager(rollekonfigurasjoner, miljøvariabler)

    return Javalin.create { config ->
        config.http.defaultContentType = defaultContentType
        config.jsonMapper(jsonMapper)
        config.registerPlugin(MicrometerPlugin { it.registry = registry })

        config.routes.beforeMatched { ctx ->
            val roller = ctx.routeRoles()
            if (roller.isNotEmpty()) {
                accessManager.manage(ctx, roller)
            }
        }

        config.routes.internalApi(rapidIsAlive, registry)
        config.routes.exceptionHandling()
        startController(
            config.routes,
            kandidatlisteRepository,
            samtykkeRepository,
            visningKontaktinfoRepository,
            openSearchKlient
        )
    }.start(port)
}

private fun JavalinDefaultRoutingApi.internalApi(
    rapidIsAlive: () -> Boolean,
    registry: PrometheusMeterRegistry
) {
    get("/isalive", { it.status(if (rapidIsAlive()) 200 else 500) }, Rolle.UNPROTECTED)
    get(
        "/internal/prometheus",
        { it.contentType(TextFormat.CONTENT_TYPE_004).result(registry.scrape()) }, Rolle.UNPROTECTED
    )
}

private fun JavalinDefaultRoutingApi.exceptionHandling() {
    exception(AltinnException::class.java) { e, ctx ->
        when (e) {
            is AltinnServiceException -> {
                logger.error("Feil ved kall mot Altinn. Se securelogs for stacktrace.")
                secure(logger).error("Feil ved kall mot Altinn", e)
                ctx.status(503).json(
                    mapOf(
                        "feilkode" to "ALTINN_FEIL",
                        "feilmelding" to "Kall mot Altinn feilet"
                    )
                )
            }

            is AltinnTilgangException -> {
                logger.error("Feil ved kall mot Altinn. Se securelogs for stacktrace.")
                secure(logger).error("Feil ved kall mot Altinn", e)
                ctx.status(403).json(
                    mapOf(
                        "feilkode" to "ALTINN_FEIL",
                        "feilmelding" to "Kall mot Altinn feilet"
                    )
                )
            }
        }
    }
}

data class RolleKonfigurasjon(
    val rolle: RouteRole,
    val tokenUtsteder: TokenUtsteder,
    val validerAutorisering: ((JwtTokenClaims, Context, AccessToken) -> Unit)? = null,
)

typealias AccessToken = String
