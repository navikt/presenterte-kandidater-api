package no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet

import io.javalin.core.security.AccessManager
import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.Handler
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.setOrganisasjoner
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.http.HttpRequest


enum class Rolle : RouteRole {
    ARBEIDSGIVER, UNPROTECTED
}

fun styrTilgang(issuerProperties: Map<Rolle, IssuerProperties>, altinnKlient: AltinnKlient) =
    AccessManager { handler: Handler, ctx: Context, roller: Set<RouteRole> ->

        val erAutentisert = when {
            roller.contains(Rolle.UNPROTECTED) -> true
            roller.contains(Rolle.ARBEIDSGIVER) -> autentiserArbeidsgiver(ctx, issuerProperties, altinnKlient)

            else -> false
        }

        if (erAutentisert) {
            handler.handle(ctx)
        } else {
            throw ForbiddenResponse()
        }
    }

private fun autentiserArbeidsgiver(
    context: Context,
    issuerProperties: Map<Rolle, IssuerProperties>,
    altinnKlient: AltinnKlient,
): Boolean {
    val fødselsnummerClaim = hentTokenClaims(context, issuerProperties, Rolle.ARBEIDSGIVER)?.get("pid")

    return if (fødselsnummerClaim == null) {
        false
    } else {
        val fnr = fødselsnummerClaim.toString()
        val accessToken = hentAccessTokenFraHeader(context)

        val organisasjoner = altinnKlient.hentOrganisasjonerMedRettighetRekruttering(fnr, accessToken)

        context.setOrganisasjoner(organisasjoner)
        true
    }
}

private fun hentTokenClaims(ctx: Context, issuerProperties: Map<Rolle, IssuerProperties>, rolle: Rolle) =
    hentTokenValidationHandler(
        issuerProperties,
        rolle
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

private fun hentAccessTokenFraHeader(context: Context): String {
    val accessTokenMedBearerPrefix = context.httpRequest.getHeader("Authorization")?.toString()
        ?: throw IllegalStateException("Prøvde å hente ut access token men Authorization header finnes ikke")

    return accessTokenMedBearerPrefix.replace("Bearer ", "")
}
