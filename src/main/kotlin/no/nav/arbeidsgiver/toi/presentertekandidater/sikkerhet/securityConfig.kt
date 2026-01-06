package no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet

import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.HttpResponseException
import io.javalin.http.UnauthorizedResponse
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.hentFødselsnummer
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.AccessToken
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.NavalinAccessManager.TokenUtsteder.INGEN
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.NavalinAccessManager.TokenUtsteder.TOKEN_X
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.NavalinAccessManager.TokenUtsteder.AZURE_AD
import no.nav.arbeidsgiver.toi.presentertekandidater.navalin.RolleKonfigurasjon
import no.nav.arbeidsgiver.toi.presentertekandidater.samtykke.SamtykkeRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.setFødselsnummer
import no.nav.arbeidsgiver.toi.presentertekandidater.setOrganisasjoner
import no.nav.arbeidsgiver.toi.presentertekandidater.setRepresenterteOrganisasjonerMedRettighetKandidater
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import org.eclipse.jetty.http.HttpStatus


fun konfigurerRoller(altinnKlient: AltinnKlient, samtykkeRepository: SamtykkeRepository) = listOf(
    RolleKonfigurasjon(
        rolle = Rolle.ARBEIDSGIVER,
        tokenUtsteder = TOKEN_X,
        validerAutorisering = hentRepresenterteOrganisasjoner(altinnKlient)
    ),
    RolleKonfigurasjon(
        rolle = Rolle.ARBEIDSGIVER_MED_ROLLE_KANDIDATER,
        tokenUtsteder = TOKEN_X,
        validerAutorisering = validerSamtykkeOgRolleKandidater(altinnKlient, samtykkeRepository)
    ),
    RolleKonfigurasjon(
        rolle = Rolle.EKSTERN_ARBEIDSGIVER,
        tokenUtsteder = TOKEN_X,
        validerAutorisering = validerRepresentererOrganisasjonMedRolleKandidater(altinnKlient)
    ),
    RolleKonfigurasjon(
        rolle = Rolle.VEILEDER,
        tokenUtsteder = AZURE_AD,
        validerAutorisering = validerVeileder(altinnKlient)
    ),
    RolleKonfigurasjon(
        rolle = Rolle.UNPROTECTED,
        tokenUtsteder = INGEN
    )
)

enum class Rolle : RouteRole {
    ARBEIDSGIVER, UNPROTECTED, ARBEIDSGIVER_MED_ROLLE_KANDIDATER, EKSTERN_ARBEIDSGIVER, VEILEDER
}

val validerSamtykkeOgRolleKandidater: (AltinnKlient, SamtykkeRepository) -> (JwtTokenClaims, Context, AccessToken) -> Unit =
    { altinnKlient, samtykkeRepository ->
        { jwtTokenClaims, context, accessToken ->
            validerRepresentererOrganisasjonMedRolleKandidater(altinnKlient)(
                jwtTokenClaims,
                context,
                accessToken
            )
            validerSamtykke(samtykkeRepository)(jwtTokenClaims, context, accessToken)
        }
    }

val validerSamtykke: (SamtykkeRepository) -> (JwtTokenClaims, Context, AccessToken) -> Unit =
    { samtykkeRepository ->
        { jwtTokenClaims, context, _ ->
            settFødselsnummerPåKontekst(jwtTokenClaims, context)
            val fnr = context.hentFødselsnummer()

            val harSamtykketVilkår = samtykkeRepository.harSamtykket(fnr)
            if (!harSamtykketVilkår) {
                throw UnavailableForLegalReasons()
            }
        }
    }

val validerRepresentererOrganisasjonMedRolleKandidater: (AltinnKlient) -> (JwtTokenClaims, Context, AccessToken) -> Unit =
    { altinnKlient ->
        { jwtTokenClaims, context, accessToken ->
            settFødselsnummerPåKontekst(jwtTokenClaims, context)
            val fnr = context.hentFødselsnummer()

            val organisasjoner =
                altinnKlient.hentOrganisasjonerMedRettighetKandidaterFraAltinn(fnr, accessToken)

            if (organisasjoner.isEmpty()) {
                throw UnauthorizedResponse()
            } else {
                context.setRepresenterteOrganisasjonerMedRettighetKandidater(organisasjoner)
            }
        }
    }

val validerVeileder: (AltinnKlient) -> (JwtTokenClaims, Context, AccessToken) -> Unit =
    {
        { jwtTokenClaims, context, accessToken ->
            if(jwtTokenClaims.get("NAVident") == null) throw ForbiddenResponse()
        }
    }

val hentRepresenterteOrganisasjoner: (AltinnKlient) -> (JwtTokenClaims, Context, AccessToken) -> Unit =
    { altinnKlient ->
        { jwtTokenClaims, context, accessToken ->
            settFødselsnummerPåKontekst(jwtTokenClaims, context)
            val fnr = context.hentFødselsnummer()

            val organisasjoner =
                altinnKlient.hentOrganisasjoner(fnr, accessToken)

            context.setOrganisasjoner(organisasjoner)
        }
    }

fun settFødselsnummerPåKontekst(claims: JwtTokenClaims, context: Context) {
    val fødselsnummerClaim = claims.get("pid") ?: throw ForbiddenResponse()

    val fnr = fødselsnummerClaim.toString()
    context.setFødselsnummer(fnr)
}

class UnavailableForLegalReasons @JvmOverloads constructor(
    message: String = "Unavailable for legal reasons",
    details: Map<String, String> = mapOf(),
) : HttpResponseException(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS_451, message, details)