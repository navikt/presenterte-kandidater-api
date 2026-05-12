package no.nav.arbeidsgiver.toi.presentertekandidater.navalin

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.json.JavalinJackson
import io.javalin.json.JsonMapper
import io.javalin.micrometer.MicrometerPlugin
import io.javalin.security.RouteRole
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

    val accessManager = NavalinAccessManager(rollekonfigurasjoner, miljøvariabler)

    return Javalin.create { config ->
        config.http.defaultContentType = defaultContentType
        config.jsonMapper(jsonMapper)
        config.registerPlugin(MicrometerPlugin { it.registry = registry })
    }.beforeMatched { ctx ->
        val roles = ctx.routeRoles()
        if (roles.isNotEmpty()) {
            accessManager.manage(ctx, roles)
        }
    }.start(port)
}

data class RolleKonfigurasjon(
    val rolle: RouteRole,
    val tokenUtsteder: TokenUtsteder,
    val validerAutorisering: ((JwtTokenClaims, Context, AccessToken) -> Unit)? = null,
)

typealias AccessToken = String
