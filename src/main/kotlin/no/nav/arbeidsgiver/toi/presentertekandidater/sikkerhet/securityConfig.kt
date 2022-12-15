package no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet

import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import io.javalin.http.HttpResponseException
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.AccessToken
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.NavalinAccessManager.TokenUtsteder.INGEN
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.NavalinAccessManager.TokenUtsteder.TOKEN_X
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.RolleKonfigurasjon
import no.nav.arbeidsgiver.toi.presentertekandidater.samtykke.SamtykkeRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.setFødselsnummer
import no.nav.arbeidsgiver.toi.presentertekandidater.setOrganisasjoner
import no.nav.arbeidsgiver.toi.presentertekandidater.setOrganisasjonerForRekruttering
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import org.eclipse.jetty.http.HttpStatus


fun konfigurerRoller(altinnKlient: AltinnKlient, samtykkeRepository: SamtykkeRepository) = listOf(
    RolleKonfigurasjon(
        rolle = Rolle.ARBEIDSGIVER,
        tokenUtsteder = TOKEN_X,
        autentiseringskrav = autentiserArbeidsgiver(altinnKlient, samtykkeRepository, false)
    ),
    RolleKonfigurasjon(
        rolle = Rolle.ARBEIDSGIVER_MED_ROLLE_REKRUTTERING,
        tokenUtsteder = TOKEN_X,
        autentiseringskrav = autentiserArbeidsgiver(altinnKlient, samtykkeRepository, true)
    ),
    RolleKonfigurasjon(
        rolle = Rolle.UNPROTECTED,
        tokenUtsteder = INGEN,
        autentiseringskrav = åpent
    )
)

enum class Rolle : RouteRole {
    ARBEIDSGIVER, UNPROTECTED, ARBEIDSGIVER_MED_ROLLE_REKRUTTERING
}

val åpent: (JwtTokenClaims, Context, AccessToken) -> Boolean = {
    jwtTokenClaims, context, accessToken -> true
}

val autentiserArbeidsgiver: (AltinnKlient, SamtykkeRepository, Boolean) -> (JwtTokenClaims, Context, AccessToken) -> Boolean =
    { altinnKlient, samtykkeRepository, forRolleRekruttering ->
        { jwtTokenClaims, context, accessToken ->
            val fødselsnummerClaim = jwtTokenClaims.get("pid")

            if (fødselsnummerClaim == null) {
                false
            } else {
                val fnr = fødselsnummerClaim.toString()
                context.setFødselsnummer(fnr)

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
                }
                true
            }
        }
    }

class UnavailableForLegalReasons @JvmOverloads constructor(
    message: String = "Unavailable for legal reasons",
    details: Map<String, String> = mapOf()
) : HttpResponseException(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS_451, message, details)