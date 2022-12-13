package no.nav.arbeidsgiver.toi.presentertekandidater

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.apibuilder.ApiBuilder.put
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.Handler
import io.javalin.http.NotFoundResponse
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.Rolle
import java.util.*

fun startController(
    javalin: Javalin,
    repository: Repository,
    openSearchKlient: OpenSearchKlient,
    konverteringFilstier: KonverteringFilstier,
) {
    javalin.routes {
        get("/organisasjoner", hentOrganisasjoner, Rolle.ARBEIDSGIVER)
        get("/kandidatlister", hentKandidatlister(repository), Rolle.ARBEIDSGIVER_MED_ROLLE_REKRUTTERING)
        get(
            "/kandidatliste/{stillingId}",
            hentKandidatliste(repository, openSearchKlient),
            Rolle.ARBEIDSGIVER_MED_ROLLE_REKRUTTERING
        )
        get("/samtykke", hentSamtykke(repository), Rolle.ARBEIDSGIVER)
        put(
            "/kandidat/{uuid}/vurdering",
            oppdaterArbeidsgiversVurdering(repository),
            Rolle.ARBEIDSGIVER_MED_ROLLE_REKRUTTERING
        )
        post(
            "/internal/konverterdata",
            konverterFraArbeidsmarked(repository, openSearchKlient, konverteringFilstier),
            Rolle.UNPROTECTED
        )
    }.exception(IllegalArgumentException::class.java) { e, ctx ->
        log("controller").warn("Kall mot ${ctx.path()} feiler på grunn av ugyldig input.", e)
        ctx.status(400)
    }
}

private val oppdaterArbeidsgiversVurdering: (repository: Repository) -> (Context) -> Unit = { repository ->
    { context ->
        val kandidatUuid = UUID.fromString(context.pathParam("uuid"))
        val jsonBody = defaultObjectMapper.readTree(context.body())
        val arbeidsgiversVurdering =
            Kandidat.ArbeidsgiversVurdering.fraString(jsonBody["arbeidsgiversVurdering"].asText())

        val kandidatliste = repository.hentKandidatlisteTilKandidat(kandidatUuid) ?: throw BadRequestResponse()
        context.validerRekruttererRolleIOrganisasjon(kandidatliste.virksomhetsnummer)

        when (repository.oppdaterArbeidsgiversVurdering(kandidatUuid, arbeidsgiversVurdering)) {
            true -> context.status(200)
            false -> context.status(400)
        }
    }
}

private val hentSamtykke: (repository: Repository) -> (Context) -> Unit = { repository ->
    { context ->
        context.status(403)
    }
}

private val hentKandidatlister: (repository: Repository) -> (Context) -> Unit = { repository ->
    { context ->
        val virksomhetsnummer = context.queryParam("virksomhetsnummer") ?: throw BadRequestResponse()
        context.validerRekruttererRolleIOrganisasjon(virksomhetsnummer)
        context.json(repository.hentKandidatlisterSomIkkeErSlettetMedAntall(virksomhetsnummer))
    }
}

private val hentKandidatliste: (repository: Repository, opensearchKlient: OpenSearchKlient) -> (Context) -> Unit =
    { repository, opensearchKlient ->
        { context ->
            val stillingId: String = context.pathParam("stillingId")

            val kandidatliste = repository.hentKandidatliste(UUID.fromString(stillingId))?.let {
                context.validerRekruttererRolleIOrganisasjon(it.virksomhetsnummer)
                it
            }

            when {
                kandidatliste == null -> context.status(404)
                kandidatliste.slettet -> context.status(404)
                else -> {
                    val kandidater = repository.hentKandidater(kandidatliste.id!!)
                    val cver = opensearchKlient.hentCver(kandidater.map { it.aktørId })
                    val kandidatDtoer = kandidater.map { KandidatDto(it, cver[it.aktørId]) }

                    context.json(KandidatlisteDto(kandidatliste, kandidatDtoer))
                }

            }
        }
    }

private val hentOrganisasjoner: (Context) -> Unit =
    { context ->
        context.json(
            context.hentOrganisasjoner()
        )
    }

data class KandidatDto(
    val kandidat: Kandidat, val cv: Cv?,
)

data class KandidatlisteDto(
    val kandidatliste: Kandidatliste, val kandidater: List<KandidatDto>,
)

typealias KandidatlisterDto = List<KandidatlisteMedAntallKandidater>

fun Context.hentOrganisasjoner(): List<AltinnReportee> =
    attribute("organisasjoner") ?: error("Context har ikke organisasjoner")

fun Context.setOrganisasjoner(altinnReportee: List<AltinnReportee>) = attribute("organisasjoner", altinnReportee)
fun Context.setOrganisasjonerForRekruttering(altinnReportee: List<AltinnReportee>) =
    attribute("organisasjonerForRekruttering", altinnReportee)

fun Context.setOrganisasjonerForRekruttering(): List<AltinnReportee> =
    attribute("organisasjonerForRekruttering") ?: error("Context har ikke organisasjoner for rekruttering.")

fun Context.validerRekruttererRolleIOrganisasjon(virksomhetsnummer: String) {
    val representererVirksomhet =
        virksomhetsnummer in this.setOrganisasjonerForRekruttering().map { it.organizationNumber }
    if (!representererVirksomhet) {
        log.info("Bruker har ikke enkeltrettighet Rekruttering for virksomheten ${virksomhetsnummer}")
        throw ForbiddenResponse("Bruker har ikke enkeltrettighet Rekruttering for virksomheten ${virksomhetsnummer}")
    }
}


