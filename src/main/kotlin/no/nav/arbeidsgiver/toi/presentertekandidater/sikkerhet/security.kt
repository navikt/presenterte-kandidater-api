package no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet

import io.javalin.core.security.AccessManager
import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import io.javalin.http.Handler
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.setOrganisasjoner
import io.javalin.http.UnauthorizedResponse
import no.nav.arbeidsgiver.toi.presentertekandidater.setFødselsnummer
import no.nav.arbeidsgiver.toi.presentertekandidater.setOrganisasjonerForRekruttering
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.http.HttpRequest


enum class Rolle : RouteRole {
    ARBEIDSGIVER, UNPROTECTED, ARBEIDSGIVER_MED_ROLLE_REKRUTTERING
}

fun styrTilgang(issuerProperties: IssuerProperties, altinnKlient: AltinnKlient) =
    AccessManager { handler: Handler, ctx: Context, roller: Set<RouteRole> ->

        val erAutentisert = when {
            roller.contains(Rolle.ARBEIDSGIVER_MED_ROLLE_REKRUTTERING) ->
                autentiserArbeidsgiver(ctx, issuerProperties, altinnKlient, forRolleRekruttering = true)
            roller.contains(Rolle.ARBEIDSGIVER) ->
                autentiserArbeidsgiver(ctx, issuerProperties, altinnKlient, forRolleRekruttering = false)
            roller.contains(Rolle.UNPROTECTED) -> true

            else -> false
        }

        if (erAutentisert) {
            handler.handle(ctx)
        } else {
            throw UnauthorizedResponse()
        }
    }

private fun autentiserArbeidsgiver(
    context: Context,
    issuerProperties: IssuerProperties,
    altinnKlient: AltinnKlient,
    forRolleRekruttering: Boolean
): Boolean {
    val fødselsnummerClaim = hentTokenClaims(context, issuerProperties)?.get("pid")

    return if (fødselsnummerClaim == null) {
        false
    } else {
        val fnr = fødselsnummerClaim.toString()
        context.setFødselsnummer(fnr)
        val accessToken = hentAccessTokenFraHeader(context)

        if (forRolleRekruttering) {
            val organisasjoner = altinnKlient.hentOrganisasjonerMedRettighetRekruttering(fnr, accessToken)
            context.setOrganisasjonerForRekruttering(organisasjoner)
        } else {
            val organisasjoner = altinnKlient.hentOrganisasjonerFraAltinn(fnr, accessToken)
            context.setOrganisasjoner(organisasjoner)
        }
        true
    }
}


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

private fun hentAccessTokenFraHeader(context: Context): String {
    val accessTokenMedBearerPrefix = context.httpRequest.getHeader("Authorization")?.toString()
        ?: throw IllegalStateException("Prøvde å hente ut access token men Authorization header finnes ikke")

    return accessTokenMedBearerPrefix.replace("Bearer ", "")
}
