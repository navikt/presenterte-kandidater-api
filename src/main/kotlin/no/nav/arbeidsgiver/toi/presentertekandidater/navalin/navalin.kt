package no.nav.arbeidsgiver.toi.presentertekandidater.navalin

import io.javalin.Javalin
import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import io.javalin.plugin.json.JavalinJackson
import io.javalin.plugin.json.JsonMapper
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.NavalinAccessManager.TokenUtsteder
import no.nav.security.token.support.core.jwt.JwtTokenClaims

fun startJavalin(
    port: Int = 9000,
    rollekonfigurasjoner: List<RolleKonfigurasjon>,
    miljøvariabler: Map<String, String>,
    defaultContentType: String = "application/json",
    jsonMapper: JsonMapper = JavalinJackson()
): Javalin {
    require(rollekonfigurasjoner.isNotEmpty()) { "Støtter ikke opprettelse av Javalin uten rollekonfigurasjon" }

    return Javalin.create {
        it.accessManager(NavalinAccessManager(rollekonfigurasjoner, miljøvariabler))
        it.defaultContentType = defaultContentType
        it.jsonMapper(jsonMapper)
    }.start(port)
}

data class RolleKonfigurasjon(
    val rolle: RouteRole,
    val tokenUtsteder: TokenUtsteder,
    val autoriseringskrav: ((JwtTokenClaims, Context, AccessToken) -> Boolean)? = null
)

typealias AccessToken = String
