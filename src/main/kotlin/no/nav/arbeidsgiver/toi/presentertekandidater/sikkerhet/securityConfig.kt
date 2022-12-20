package no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet

import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.HttpResponseException
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.hentFødselsnummer
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
        autoriseringskrav = hentRepresenterteOrganisasjoner(altinnKlient, samtykkeRepository, true)
    ),
    RolleKonfigurasjon(
        rolle = Rolle.ARBEIDSGIVER_MED_ROLLE_REKRUTTERING,
        tokenUtsteder = TOKEN_X,
        autoriseringskrav = sjekkSamtykkeOgRolleRekruttering(altinnKlient, samtykkeRepository)
    ),
    RolleKonfigurasjon(
        rolle = Rolle.EKSTERN_ARBEIDSGIVER,
        tokenUtsteder = TOKEN_X,
        autoriseringskrav = sjekkRepresentererOrganisasjonMedRolleRekruttering(altinnKlient, samtykkeRepository)
    ),
    RolleKonfigurasjon(
        rolle = Rolle.UNPROTECTED,
        tokenUtsteder = INGEN
    )
)

enum class Rolle : RouteRole {
    ARBEIDSGIVER, UNPROTECTED, ARBEIDSGIVER_MED_ROLLE_REKRUTTERING, EKSTERN_ARBEIDSGIVER
}

val sjekkSamtykkeOgRolleRekruttering : (AltinnKlient, SamtykkeRepository) -> (JwtTokenClaims, Context, AccessToken) -> Boolean =
    { altinnKlient, samtykkeRepository ->
        { jwtTokenClaims, context, accessToken ->

            val harRolleRekruttering = sjekkRepresentererOrganisasjonMedRolleRekruttering(altinnKlient, samtykkeRepository)(jwtTokenClaims, context, accessToken)
            val harSamtykket = sjekkSamtykke(samtykkeRepository)(jwtTokenClaims, context, accessToken)
            harRolleRekruttering && harSamtykket
        }
    }

val sjekkSamtykke: (SamtykkeRepository) -> (JwtTokenClaims, Context, AccessToken) -> Boolean =
    { samtykkeRepository ->
        { jwtTokenClaims, context, accessToken ->
            settFødselsnummerPåKontekst(jwtTokenClaims, context)
            val fnr = context.hentFødselsnummer()

            val harSamtykketVilkår = samtykkeRepository.harSamtykket(fnr)
            if (!harSamtykketVilkår) {
                throw UnavailableForLegalReasons()
            }
            true
        }
    }

val sjekkRepresentererOrganisasjonMedRolleRekruttering: (AltinnKlient, SamtykkeRepository) -> (JwtTokenClaims, Context, AccessToken) -> Boolean =
    { altinnKlient, samtykkeRepository ->
        { jwtTokenClaims, context, accessToken ->
            settFødselsnummerPåKontekst(jwtTokenClaims, context)
            val fnr = context.hentFødselsnummer()

            val organisasjoner =
                altinnKlient.hentOrganisasjonerMedRettighetRekrutteringFraAltinn(fnr, accessToken)

            if (organisasjoner.isEmpty()) {
                false
            } else {
                context.setOrganisasjonerForRekruttering(organisasjoner)
                true
            }
        }
    }

val hentRepresenterteOrganisasjoner: (AltinnKlient, SamtykkeRepository, Boolean) -> (JwtTokenClaims, Context, AccessToken) -> Boolean =
    { altinnKlient, samtykkeRepository, forRolleRekruttering ->
        { jwtTokenClaims, context, accessToken ->
            settFødselsnummerPåKontekst(jwtTokenClaims, context)
            val fnr = context.hentFødselsnummer()

            val organisasjoner =
                altinnKlient.hentOrganisasjoner(fnr, accessToken)

            context.setOrganisasjoner(organisasjoner)

            true

        }
    }

fun settFødselsnummerPåKontekst(claims: JwtTokenClaims, context: Context) {
    val fødselsnummerClaim = claims.get("pid") ?: throw ForbiddenResponse()

    val fnr = fødselsnummerClaim.toString()
    context.setFødselsnummer(fnr)
}

class UnavailableForLegalReasons @JvmOverloads constructor(
    message: String = "Unavailable for legal reasons",
    details: Map<String, String> = mapOf()
) : HttpResponseException(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS_451, message, details)