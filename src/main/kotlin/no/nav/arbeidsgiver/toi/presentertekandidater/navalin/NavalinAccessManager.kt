package no.nav.arbeidsgiver.toi.presentertekandidater.navalin

import com.nimbusds.jwt.JWTClaimsSet
import io.javalin.http.Context
import io.javalin.http.Header
import io.javalin.http.UnauthorizedResponse
import io.javalin.security.RouteRole
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.NavalinAccessManager.TokenUtsteder.INGEN
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.NavalinAccessManager.TokenUtsteder.TOKEN_X
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.NavalinAccessManager.TokenUtsteder.AZURE_AD
import no.nav.arbeidsgiver.toi.presentertekandidater.variable
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import java.net.URI

class NavalinAccessManager(
    private val rolleKonfigurasjoner: List<RolleKonfigurasjon>,
    private val miljøvariabler: Map<String, String>,
) {

    fun manage(ctx: Context, routeRoles: Set<RouteRole>) {
        require(routeRoles.size == 1) { "Støtter kun bruk av en rolle per endepunkt." }
        val rollekonfigurasjon = rolleKonfigurasjoner.find { routeRoles.contains(it.rolle) }
            ?: error("Bruker ukonfigurert rolle på endepunktet.")

        kanAutentisereOgAutorisere(ctx, rollekonfigurasjon.tokenUtsteder, rollekonfigurasjon.validerAutorisering)
    }

    private fun kanAutentisereOgAutorisere(
        context: Context,
        tokenUtsteder: TokenUtsteder,
        validerAutorisering: ((JwtTokenClaims, Context, AccessToken) -> Unit)?,
    ) {
        val issuerProperties = hentIssuerProperties(tokenUtsteder)

        val tokenClaims = if (issuerProperties != null) {
            hentTokenClaims(context, issuerProperties, tokenUtsteder)
        } else {
            lagTomJwtTokenClaims()
        }

        if (tokenClaims == null) {
            throw UnauthorizedResponse()
        }
        if (validerAutorisering != null) {
            validerAutorisering(tokenClaims, context, hentAccessTokenFraHeader(context))
        }
    }

    private fun lagTomJwtTokenClaims(): JwtTokenClaims = JwtTokenClaims(JWTClaimsSet.parse("{}"))

    private fun hentTokenClaims(ctx: Context, issuerProperties: IssuerProperties, tokenUtsteder: TokenUtsteder) =
        hentTokenValidationHandler(
            issuerProperties, tokenUtsteder
        ).getValidatedTokens(ctx.httpRequest).anyValidClaims

    private val Context.httpRequest: HttpRequest
        get() = object : HttpRequest {
            override fun getHeader(headerName: String) = headerMap()[headerName]
        }

    private fun hentIssuerProperties(tokenUtsteder: TokenUtsteder): IssuerProperties? {
        return when (tokenUtsteder) {
            TOKEN_X -> lagIssuerPropertiesForTokenX(miljøvariabler)
            AZURE_AD -> lagIssuerPropertiesForAzureAD(miljøvariabler)
            INGEN -> null
        }
    }

    private fun hentAccessTokenFraHeader(context: Context): String {
        val accessTokenMedBearerPrefix = context.header(Header.AUTHORIZATION)
            ?: throw IllegalStateException("Prøvde å hente ut access token men Authorization header finnes ikke")

        return accessTokenMedBearerPrefix.replace("Bearer ", "")
    }

    private fun lagIssuerPropertiesForTokenX(envs: Map<String, String>) =
        IssuerProperties(
            URI(envs.variable("TOKEN_X_WELL_KNOWN_URL")).toURL(),
            listOf(envs.variable("TOKEN_X_CLIENT_ID")),
        )

    private fun lagIssuerPropertiesForAzureAD(envs: Map<String, String>) =
        IssuerProperties(
            URI(envs.variable("AZURE_APP_WELL_KNOWN_URL")).toURL(),
            listOf(envs.variable("AZURE_APP_CLIENT_ID")),
        )

    enum class TokenUtsteder {
        TOKEN_X,
        AZURE_AD,
        INGEN
    }
}
