package no.nav.arbeidsgiver.toi.presentertekandidater.navalin

import io.javalin.Javalin
import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import io.javalin.plugin.json.JavalinJackson
import io.javalin.plugin.json.JsonMapper
import io.javalin.plugin.metrics.MicrometerPlugin
import io.micrometer.core.instrument.MeterRegistry
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.NavalinAccessManager.TokenUtsteder
import no.nav.security.token.support.core.jwt.JwtTokenClaims

fun startJavalin(
    port: Int = 9000,
    rollekonfigurasjoner: List<RolleKonfigurasjon>,
    miljøvariabler: Map<String, String>,
    defaultContentType: String = "application/json",
    jsonMapper: JsonMapper = JavalinJackson(),
    registry: MeterRegistry,
): Javalin {
    require(rollekonfigurasjoner.isNotEmpty()) { "Støtter ikke opprettelse av Javalin uten rollekonfigurasjon" }

    return Javalin.create {
        it.accessManager(NavalinAccessManager(rollekonfigurasjoner, miljøvariabler))
        it.defaultContentType = defaultContentType
        it.jsonMapper(jsonMapper)
        it.registerPlugin(MicrometerPlugin(registry))
    }.start(port)
}

data class RolleKonfigurasjon(
    val rolle: RouteRole,
    val tokenUtsteder: TokenUtsteder,
    val validerAutorisering: ((JwtTokenClaims, Context, AccessToken) -> Unit)? = null,
)

typealias AccessToken = String
