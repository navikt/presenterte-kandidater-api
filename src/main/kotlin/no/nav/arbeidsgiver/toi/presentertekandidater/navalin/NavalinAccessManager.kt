package no.nav.arbeidsgiver.toi.presentertekandidater.navalin

import com.nimbusds.jwt.JWTClaimsSet
import io.javalin.core.security.AccessManager
import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.UnauthorizedResponse
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.NavalinAccessManager.TokenUtsteder.INGEN
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.NavalinAccessManager.TokenUtsteder.TOKEN_X
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import java.net.URL

class NavalinAccessManager(
    private val rolleKonfigurasjoner: List<RolleKonfigurasjon>,
    private val miljøvariabler: Map<String, String>): AccessManager {
    val roller = rolleKonfigurasjoner.map { it.rolle }

    override fun manage(handler: Handler, ctx: Context, routeRoles: MutableSet<RouteRole>) {
        val aktuelleRollekonfigurasjoner = rolleKonfigurasjoner.filter { routeRoles.contains(it.rolle) }
        val erAutentisert = kanAutentisereMedEnAvRollene(ctx, aktuelleRollekonfigurasjoner)

        if (!erAutentisert) {
            throw UnauthorizedResponse()
        } else {
            handler.handle(ctx)
        }
    }

    private fun kanAutentisereMedEnAvRollene(context: Context, rolleKonfigurasjoner: List<RolleKonfigurasjon>): Boolean =
        rolleKonfigurasjoner.any {
            val issuerProperties = hentIssuerProperties(it.tokenUtsteder)

            val tokenClaims = if (issuerProperties != null) {
                hentTokenClaims(context, issuerProperties)
            } else {
                lagTomJwtTokenClaims()
            }
            it.autentiseringskrav(tokenClaims, context, hentAccessTokenFraHeader(context))
        }

    private fun lagTomJwtTokenClaims(): JwtTokenClaims = JwtTokenClaims(JWTClaimsSet.parse(""))

    private fun hentTokenClaims(ctx: Context, issuerProperties: IssuerProperties) =
        hentTokenValidationHandler(
            issuerProperties
        ).getValidatedTokens(ctx.httpRequest).anyValidClaims.orElseGet { null }

    private val Context.httpRequest: HttpRequest
        get() = object : HttpRequest {
            override fun getHeader(headerName: String?) = headerMap()[headerName]
            override fun getCookies() = cookieMap().map { (name, value) ->
                object : HttpRequest.NameValue {
                    override fun getName() = name
                    override fun getValue() = value
                }
            }.toTypedArray()
        }

    private fun hentIssuerProperties(tokenUtsteder: TokenUtsteder): IssuerProperties? {
        return when (tokenUtsteder) {
            TOKEN_X -> lagIssuerPropertiesForTokenX(miljøvariabler)
            INGEN -> null
        }
    }

    private fun hentAccessTokenFraHeader(context: Context): String {
        val accessTokenMedBearerPrefix = context.httpRequest.getHeader("Authorization")?.toString()
            ?: throw IllegalStateException("Prøvde å hente ut access token men Authorization header finnes ikke")

        return accessTokenMedBearerPrefix.replace("Bearer ", "")
    }

    private fun lagIssuerPropertiesForTokenX(envs: Map<String, String>) =
        IssuerProperties(
            URL(envs["TOKEN_X_WELL_KNOWN_URL"]),
            listOf(envs["TOKEN_X_CLIENT_ID"]),
            envs["TOKEN_X_PRIVATE_JWK"]
        )

    enum class TokenUtsteder {
        TOKEN_X,
        INGEN
    }
}