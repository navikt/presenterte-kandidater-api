package no.nav.arbeidsgiver.toi.presentertekandidater

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.apibuilder.ApiBuilder.put
import io.javalin.http.Context
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.Rolle
import java.util.*

fun startController(
    javalin: Javalin,
    repository: Repository,
    openSearchKlient: OpenSearchKlient,
    altinnKlient: AltinnKlient,
    konverteringFilstier: KonverteringFilstier,
) {
    javalin.routes {
        get("/organisasjoner", hentOrganisasjoner(altinnKlient), Rolle.ARBEIDSGIVER)
        get("/kandidatlister", hentKandidatlister(repository), Rolle.ARBEIDSGIVER)
        get("/kandidatliste/{stillingId}", hentKandidatliste(repository, openSearchKlient), Rolle.ARBEIDSGIVER)
        put("/kandidat/{uuid}/vurdering", oppdaterArbeidsgiversVurdering(repository), Rolle.ARBEIDSGIVER)
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
        when (repository.oppdaterArbeidsgiversVurdering(kandidatUuid, arbeidsgiversVurdering)) {
            true -> context.status(200)
            false -> context.status(400)
        }
    }
}

private val hentKandidatlister: (repository: Repository) -> (Context) -> Unit = { repository ->
    { context ->
        val virksomhetsnummer = context.queryParam("virksomhetsnummer")

        when (virksomhetsnummer) {
            "", null -> context.status(400)
            !in context.hentOrganisasjoner().map { it.organizationNumber } -> context.status(403)
            else -> context.json(repository.hentKandidatlisterSomIkkeErSlettetMedAntall(virksomhetsnummer))
        }
    }
}

private val hentKandidatliste: (repository: Repository, opensearchKlient: OpenSearchKlient) -> (Context) -> Unit =
    { repository, opensearchKlient ->
        { context ->
            val stillingId = context.pathParam("stillingId")

            if (stillingId.isNullOrBlank()) {
                context.status(400)
            } else {
                val kandidatliste = repository.hentKandidatliste(UUID.fromString(stillingId))

                if (kandidatliste == null || kandidatliste.slettet) {
                    context.status(404)
                } else if (kandidatliste.virksomhetsnummer !in context.hentOrganisasjoner().map { it.organizationNumber }) {
                    context.status(403)
                } else {
                    val kandidater = repository.hentKandidater(kandidatliste.id!!)
                    val cver = opensearchKlient.hentCver(kandidater.map { it.aktørId })
                    val kandidatDtoer = kandidater.map { KandidatDto(it, cver[it.aktørId]) }

                    context.json(KandidatlisteDto(kandidatliste, kandidatDtoer))
                }
            }
        }
    }

private val hentOrganisasjoner: (altinnKlient: AltinnKlient) -> (Context) -> Unit =
    { altinnKlient ->
        { context ->
            context.json(
                context.hentOrganisasjoner()
            )
        }
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


