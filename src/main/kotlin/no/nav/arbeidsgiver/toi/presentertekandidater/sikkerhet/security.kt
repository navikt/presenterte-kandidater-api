package no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet

import io.javalin.core.security.AccessManager
import io.javalin.core.security.RouteRole
import io.javalin.http.*
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.arbeidsgiver.toi.presentertekandidater.setOrganisasjoner
import no.nav.arbeidsgiver.toi.presentertekandidater.samtykke.SamtykkeRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.setFødselsnummer
import no.nav.arbeidsgiver.toi.presentertekandidater.setOrganisasjonerForRekruttering
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.http.HttpRequest
import org.eclipse.jetty.http.HttpStatus


enum class Rolle : RouteRole {
    ARBEIDSGIVER, UNPROTECTED, ARBEIDSGIVER_MED_ROLLE_REKRUTTERING
}

fun styrTilgang(
    issuerProperties: IssuerProperties,
    altinnKlient: AltinnKlient,
    samtykkeRepository: SamtykkeRepository
) =
    AccessManager { handler: Handler, ctx: Context, roller: Set<RouteRole> ->
        log("styrTilgang").info("Er på toppen av access manager med rolle $roller")

        val erAutentisert = when {
            roller.contains(Rolle.ARBEIDSGIVER_MED_ROLLE_REKRUTTERING) ->
                autentiserArbeidsgiver(
                    ctx,
                    issuerProperties,
                    altinnKlient,
                    samtykkeRepository,
                    forRolleRekruttering = true
                )

            roller.contains(Rolle.ARBEIDSGIVER) ->
                autentiserArbeidsgiver(
                    ctx,
                    issuerProperties,
                    altinnKlient,
                    samtykkeRepository,
                    forRolleRekruttering = false
                )

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
    samtykkeRepository: SamtykkeRepository,
    forRolleRekruttering: Boolean
): Boolean {
    val fødselsnummerClaim = hentTokenClaims(context, issuerProperties)?.get("pid")
    log("autentiserArbeidsgiver").info("Er på toppen av autentiserArbeidsgiver")

    return if (fødselsnummerClaim == null) {
        log("autentiserArbeidsgiver").info("Ouch. Har ikke fødselsnummer-claims ...")

        false
    } else {
        val fnr = fødselsnummerClaim.toString()
        context.setFødselsnummer(fnr)
        val accessToken = hentAccessTokenFraHeader(context)

        log("autentiserArbeidsgiver").info("Validerer token for rolle rekruttering? $forRolleRekruttering")

        if (forRolleRekruttering) {
            val harSamtykketVilkår = samtykkeRepository.harSamtykket(fnr)
            if (!harSamtykketVilkår) {
                throw UnavailableForLegalReasons()
            }

            val organisasjoner = altinnKlient.hentOrganisasjonerMedRettighetRekrutteringFraAltinn(fnr, accessToken)
            context.setOrganisasjonerForRekruttering(organisasjoner)
        } else {
            val organisasjoner = altinnKlient.hentOrganisasjoner(fnr, accessToken)
            context.setOrganisasjoner(organisasjoner)

            log("autentiserArbeidsgiver").info("Autentiserer arbeidsgiver. Har fødselsnummer med ${fnr.length} sifre. Har tilgang til ${organisasjoner.size} organisasjoner, inkludert ${(organisasjoner.firstOrNull()?.name ?: "ingen organisasjoner")}")
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

class UnavailableForLegalReasons @JvmOverloads constructor(
    message: String = "Unavailable for legal reasons",
    details: Map<String, String> = mapOf()
) : HttpResponseException(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS_451, message, details)