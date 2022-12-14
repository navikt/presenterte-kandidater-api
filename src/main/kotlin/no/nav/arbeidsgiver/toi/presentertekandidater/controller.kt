package no.nav.arbeidsgiver.toi.presentertekandidater

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.*
import no.nav.arbeidsgiver.toi.presentertekandidater.opensearch.Cv
import no.nav.arbeidsgiver.toi.presentertekandidater.opensearch.OpenSearchKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.samtykke.SamtykkeRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.Rolle
import java.util.*

fun startController(
    javalin: Javalin,
    kandidatlisteRepository: KandidatlisteRepository,
    samtykkeRepository: SamtykkeRepository,
    openSearchKlient: OpenSearchKlient
) {
    javalin.routes {
        get("/organisasjoner", hentOrganisasjoner, Rolle.ARBEIDSGIVER)
        get("/kandidatlister", hentKandidatlister(kandidatlisteRepository), Rolle.ARBEIDSGIVER_MED_ROLLE_REKRUTTERING)
        get(
            "/kandidatliste/{stillingId}",
            hentKandidatliste(kandidatlisteRepository, openSearchKlient),
            Rolle.ARBEIDSGIVER_MED_ROLLE_REKRUTTERING
        )
        get("/samtykke", hentSamtykke(samtykkeRepository), Rolle.ARBEIDSGIVER)
        post("/samtykke", lagreSamtykke(samtykkeRepository), Rolle.ARBEIDSGIVER)
        put(
            "/kandidat/{uuid}/vurdering",
            oppdaterArbeidsgiversVurdering(kandidatlisteRepository),
            Rolle.ARBEIDSGIVER_MED_ROLLE_REKRUTTERING
        )
        delete("/kandidat/{uuid}", slettKandidat(kandidatlisteRepository), Rolle.ARBEIDSGIVER_MED_ROLLE_REKRUTTERING)
        get(
            "/ekstern/antallkandidater",
            hentAntallKandidater(kandidatlisteRepository),
            Rolle.EKSTERN_ARBEIDSGIVER
        )
    }.exception(IllegalArgumentException::class.java) { e, ctx ->
        log("controller").warn("Kall mot ${ctx.path()} feiler p?? grunn av ugyldig input.", e)
        ctx.status(400)
    }.exception(Exception::class.java) { e, ctx ->
        log("controller").error("Kall mot ${ctx.path()} f??rte til en ukjent feil.", e)
        ctx.status(500)
    }
}

private val oppdaterArbeidsgiversVurdering: (kandidatlisteRepository: KandidatlisteRepository) -> (Context) -> Unit =
    { repository ->
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

private val hentSamtykke: (samtykkeRepository: SamtykkeRepository) -> (Context) -> Unit = { samtykkeRepository ->
    { context ->
        val f??dselsnummer = context.hentF??dselsnummer()
        when (samtykkeRepository.harSamtykket(f??dselsnummer)) {
            true -> context.status(200)
            false -> context.status(403)
        }
    }
}

private val lagreSamtykke: (samtykkeRepository: SamtykkeRepository) -> (Context) -> Unit = { samtykkeRepository ->
    { context ->
        val f??dselsnummer = context.hentF??dselsnummer()

        val harSamtykketAllerede = samtykkeRepository.harSamtykket(f??dselsnummer)
        if (!harSamtykketAllerede) {
            samtykkeRepository.lagre(f??dselsnummer)
        }
        context.status(200)
    }
}

private val hentKandidatlister: (kandidatlisteRepository: KandidatlisteRepository) -> (Context) -> Unit =
    { repository ->
        { context ->
            val virksomhetsnummer = context.queryParam("virksomhetsnummer") ?: throw BadRequestResponse()
            context.validerRekruttererRolleIOrganisasjon(virksomhetsnummer)
            context.json(repository.hentKandidatlisterSomIkkeErSlettetMedAntall(virksomhetsnummer))
        }
    }

private val slettKandidat: (kandidatlisteRepository: KandidatlisteRepository) -> (Context) -> Unit =
    { kandidatlisteRepository ->
        { context ->
            val kandidatUuid: UUID = UUID.fromString(context.pathParam("uuid"))
            val kandidatliste =
                kandidatlisteRepository.hentKandidatlisteTilKandidat(kandidatUuid) ?: throw BadRequestResponse()
            context.validerRekruttererRolleIOrganisasjon(kandidatliste.virksomhetsnummer)
            kandidatlisteRepository.slettKandidat(kandidatUuid)
        }
    }

private val hentKandidatliste: (kandidatlisteRepository: KandidatlisteRepository, opensearchKlient: OpenSearchKlient) -> (Context) -> Unit =
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
                    val cver = opensearchKlient.hentCver(kandidater.map { it.akt??rId })
                    val kandidatDtoer = kandidater.map { KandidatDto(it, cver[it.akt??rId]) }

                    val antallKandidater = kandidater.size
                    val antallCver = cver.filter { cv -> cv.value != null }.size

                    if (antallKandidater != antallCver) {
                        log("hentKandidatliste")
                            .info("Henter kandidater for stilling $stillingId. Listen har $antallKandidater kandidater, og OpenSearch returnerte $antallCver CV-er.")
                    }

                    context.json(KandidatlisteDto(kandidatliste, kandidatDtoer))
                }

            }
        }
    }

private val hentAntallKandidater: (kandidatlisteRepository: KandidatlisteRepository) -> (Context) -> Unit =
    { repository ->
        { context ->
            log("hentKandidaterForArbeidsgiver").info("Henter kandidater for arbeidsgiver.")
            val virksomhetsnummer = context.queryParam("virksomhetsnummer") ?: throw BadRequestResponse()
            context.validerRekruttererRolleIOrganisasjon(virksomhetsnummer)
            val antallKandidater = repository.hentKandidatlisterSomIkkeErSlettetMedAntall(virksomhetsnummer).map {
                it.antallKandidater
            }.sum()

            context.json(
                object {
                    val antallKandidater = antallKandidater
                }
            )
        }
    }

private val hentOrganisasjoner: (Context) -> Unit =
    { context ->
        log("hentOrganisasjoner").info("Henter organisasjoner for bruker med fnr p?? ${context.hentF??dselsnummer().length} sifre")
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

fun Context.hentF??dselsnummer(): String =
    attribute("f??dselsnummer") ?: error("Context har ikke f??dselsnummer")

fun Context.setF??dselsnummer(f??dselsnummer: String) = attribute("f??dselsnummer", f??dselsnummer)
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


