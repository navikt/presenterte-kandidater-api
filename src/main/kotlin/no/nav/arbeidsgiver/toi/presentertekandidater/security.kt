package no.nav.arbeidsgiver.toi

import io.javalin.core.security.AccessManager
import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.Handler
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler

enum class Rolle : RouteRole {
    ARBEIDSGIVER,
    UNPROTECTED
}

fun styrTilgang(issuerProperties: Map<Rolle, IssuerProperties>) =
    AccessManager { handler: Handler, ctx: Context, roller: Set<RouteRole> ->

        val erAutentisert =
            when {
                roller.contains(Rolle.UNPROTECTED) -> true
                roller.contains(Rolle.ARBEIDSGIVER) -> autentiserArbeidsgiver(hentTokenClaims(ctx, issuerProperties[Rolle.ARBEIDSGIVER]!!))
                else -> false
            }

        if (erAutentisert) {
            handler.handle(ctx)
        } else {
            throw ForbiddenResponse()
        }
    }


fun interface Autentiseringsmetode {
    operator fun invoke(claims: JwtTokenClaims?): Boolean
}


val autentiserArbeidsgiver = Autentiseringsmetode { it != null }


private fun hentTokenClaims(ctx: Context, issuerProperties: IssuerProperties) =
    lagTokenValidationHandler(issuerProperties)
        .getValidatedTokens(ctx.httpRequest)
        .anyValidClaims.orElseGet { null }

private fun lagTokenValidationHandler(issuerProperties: IssuerProperties) =
    JwtTokenValidationHandler(
        MultiIssuerConfiguration(mapOf(issuerProperties.cookieName to issuerProperties))
    )

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



