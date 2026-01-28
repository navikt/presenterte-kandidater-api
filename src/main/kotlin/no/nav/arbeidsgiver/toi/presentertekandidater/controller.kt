package no.nav.arbeidsgiver.toi.presentertekandidater

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.InternalServerErrorResponse
import no.nav.arbeidsgiver.toi.presentertekandidater.SecureLogLogger.Companion.secure
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnReportee
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.KandidatlisteRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.opensearch.Cv
import no.nav.arbeidsgiver.toi.presentertekandidater.opensearch.OpenSearchKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.samtykke.SamtykkeRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.Rolle
import no.nav.arbeidsgiver.toi.presentertekandidater.visningkontaktinfo.VisningKontaktinfoRepository
import no.nav.helse.rapids_rivers.toUUID
import org.slf4j.Logger
import java.util.*

val log: Logger = noClassLogger() // Er public bare fordi den brukes i en test

fun startController(
    javalin: Javalin,
    kandidatlisteRepository: KandidatlisteRepository,
    samtykkeRepository: SamtykkeRepository,
    visningKontaktinfoRepository: VisningKontaktinfoRepository,
    openSearchKlient: OpenSearchKlient
) {
    javalin.routes {
        get("/organisasjoner", hentOrganisasjoner, Rolle.ARBEIDSGIVER)
        get("/kandidatlister", hentKandidatlister(kandidatlisteRepository), Rolle.ARBEIDSGIVER_MED_ROLLE_KANDIDATER)
        get(
            "/kandidatliste/{stillingId}",
            hentKandidatliste(kandidatlisteRepository, openSearchKlient),
            Rolle.ARBEIDSGIVER_MED_ROLLE_KANDIDATER
        )
        get(
            "/kandidatliste/{stillingId}/vurdering",
            hentKandidatlisteVurderinger(kandidatlisteRepository),
            Rolle.VEILEDER
        )
        get("/samtykke", hentSamtykkeGammel(samtykkeRepository), Rolle.ARBEIDSGIVER)
        get("/hentsamtykke", hentSamtykke(samtykkeRepository), Rolle.ARBEIDSGIVER)
        post("/samtykke", lagreSamtykke(samtykkeRepository), Rolle.ARBEIDSGIVER)
        put(
            "/kandidat/{uuid}/vurdering",
            oppdaterArbeidsgiversVurdering(kandidatlisteRepository),
            Rolle.ARBEIDSGIVER_MED_ROLLE_KANDIDATER
        )
        delete("/kandidat/{uuid}", slettKandidat(kandidatlisteRepository), Rolle.ARBEIDSGIVER_MED_ROLLE_KANDIDATER)
        post(
            "/kandidat/{uuid}/registrerviskontaktinfo",
            registrerVisningAvKontaktInfo(kandidatlisteRepository, visningKontaktinfoRepository),
            Rolle.ARBEIDSGIVER_MED_ROLLE_KANDIDATER
        )
        get(
            "/ekstern/antallkandidater",
            hentAntallKandidater(kandidatlisteRepository),
            Rolle.EKSTERN_ARBEIDSGIVER
        )
    }.exception(IllegalArgumentException::class.java) { e, ctx ->
        log.warn("Kall mot ${ctx.path()} feiler på grunn av ugyldig input. Se SecureLog for stacktrace.")
        secure(log).warn("Kall mot ${ctx.path()} feiler på grunn av ugyldig input.", e)
        ctx.status(400)
    }.exception(Exception::class.java) { e, ctx ->
        log.error("Kall mot ${ctx.path()} førte til en ukjent feil. Se SecureLog for stacktrace.")
        secure(log).error("Kall mot ${ctx.path()} førte til en ukjent feil.", e)
        ctx.status(500)
    }
}

private val registrerVisningAvKontaktInfo: (KandidatlisteRepository, VisningKontaktinfoRepository) -> (Context) -> Unit =
    { kandidatlisteRepository, visningKontakInfoRepository ->
        { context ->
            val kandidatUuid = UUID.fromString(context.pathParam("uuid"))
            val kandidat = kandidatlisteRepository.hentKandidatMedUUID(kandidatUuid) ?: throw BadRequestResponse()
            val kandidatliste = kandidatlisteRepository.hentKandidatlisteTilKandidat(kandidatUuid)
                ?: throw InternalServerErrorResponse()
            val lagringGikkBra = try {
                visningKontakInfoRepository.registrerVisning(kandidat.aktørId, kandidatliste.stillingId)
            } catch (e: Exception) {
                false
            }
            if (!lagringGikkBra) {
                log.error("Fikk ikke til å lagre visning av kontakinfo med kandidatuuid: $kandidatUuid")
            }
            context.status(200)
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

private val hentSamtykkeGammel: (samtykkeRepository: SamtykkeRepository) -> (Context) -> Unit = { samtykkeRepository ->
    { context ->
        val fødselsnummer = context.hentFødselsnummer()
        when (samtykkeRepository.harSamtykket(fødselsnummer)) {
            true -> context.status(200)
            false -> context.status(403)
        }
    }
}

data class Respons(val harSamtykket: Boolean)
private val hentSamtykke: (samtykkeRepository: SamtykkeRepository) -> (Context) -> Unit = { samtykkeRepository ->
    { context ->
        val fødselsnummer = context.hentFødselsnummer()
        val harSamtykket = samtykkeRepository.harSamtykket(fødselsnummer)
        context.json(Respons(harSamtykket))
        context.status(200)
    }
}

private val lagreSamtykke: (samtykkeRepository: SamtykkeRepository) -> (Context) -> Unit = { samtykkeRepository ->
    { context ->
        val fødselsnummer = context.hentFødselsnummer()

        val harSamtykketAllerede = samtykkeRepository.harSamtykket(fødselsnummer)
        if (!harSamtykketAllerede) {
            samtykkeRepository.lagre(fødselsnummer)
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
                kandidatliste.slettet -> context.json(KandidatlisteDto(kandidatliste, emptyList()))
                else -> {
                    val kandidater = repository.hentKandidater(kandidatliste.id!!)
                    val cver = opensearchKlient.hentCver(kandidater.map { it.aktørId })
                    val kandidatDtoer = kandidater.map { KandidatDto(it, cver[it.aktørId]) }

                    val antallKandidater = kandidater.size
                    val antallCver = cver.filter { cv -> cv.value != null }.size

                    if (antallKandidater != antallCver) {
                        log.info("Henter kandidater for stilling $stillingId. Listen har $antallKandidater kandidater, og OpenSearch returnerte $antallCver CV-er.")
                    }

                    context.json(KandidatlisteDto(kandidatliste, kandidatDtoer))
                }

            }
        }
    }
private val hentKandidatlisteVurderinger: (kandidatlisteRepository: KandidatlisteRepository) -> (Context) -> Unit =
    { repository ->
        { context ->
            val stillingId: String = context.pathParam("stillingId")
            val kandidatlisteId = repository.hentKandidatliste(stillingId.toUUID())?.id
            context.json(
                if (kandidatlisteId == null) emptyList()
                else repository.hentKandidater(kandidatlisteId).map {
                    object {
                        val aktørId = it.aktørId
                        val vurdering = it.arbeidsgiversVurdering
                    }
                }
            )
        }
    }

private val hentAntallKandidater: (kandidatlisteRepository: KandidatlisteRepository) -> (Context) -> Unit =
    { repository ->
        { context ->
            log.info("Henter kandidater for arbeidsgiver.")
            val virksomhetsnummer = context.queryParam("virksomhetsnummer") ?: throw BadRequestResponse()
            context.validerRekruttererRolleIOrganisasjon(virksomhetsnummer)
            val antallKandidater =
                repository.hentKandidatlisterSomIkkeErSlettetMedAntall(virksomhetsnummer).sumOf { it.antallKandidater }
            context.json(
                object {
                    val antallKandidater = antallKandidater
                }
            )
        }
    }

private val hentOrganisasjoner: (Context) -> Unit =
    { context ->
        log.info("Henter organisasjoner for bruker")
        context.json(
            context.hentOrganisasjoner()
        )
    }


private data class KandidatDto(
    val kandidat: Kandidat, val cv: Cv?,
)

private data class KandidatlisteDto(
    val kandidatliste: Kandidatliste, val kandidater: List<KandidatDto>,
)

fun Context.hentOrganisasjoner(): List<AltinnReportee> =
    attribute("organisasjoner") ?: error("Context har ikke organisasjoner")

fun Context.setOrganisasjoner(altinnReportee: List<AltinnReportee>) = attribute("organisasjoner", altinnReportee)

fun Context.hentFødselsnummer(): String =
    attribute("fødselsnummer") ?: error("Context har ikke fødselsnummer")

fun Context.setFødselsnummer(fødselsnummer: String) = attribute("fødselsnummer", fødselsnummer)
fun Context.setRepresenterteOrganisasjonerMedRettighetKandidater(altinnReportee: List<AltinnReportee>) =
    attribute("representerteOrganisasjonerMedRettighetKandidater", altinnReportee)

fun Context.hentRepresenterteOrganisasjonerMedRettighetKandidater(): List<AltinnReportee> =
    attribute("representerteOrganisasjonerMedRettighetKandidater") ?: error("Context har ikke organisasjoner for kandidater.")

fun Context.validerRekruttererRolleIOrganisasjon(virksomhetsnummer: String) {
    val representererVirksomhet =
        virksomhetsnummer in this.hentRepresenterteOrganisasjonerMedRettighetKandidater().map { it.organizationNumber }
    if (!representererVirksomhet) {
        this@validerRekruttererRolleIOrganisasjon.log.info("Bruker har ikke enkeltrettighet nav_rekruttering_kandidater for angitt virksomhet. Se virksomhetsnummer i SecureLog")
        secure(this@validerRekruttererRolleIOrganisasjon.log).info("Bruker har ikke enkeltrettighet nav_rekruttering_kandidater for virksomheten ${virksomhetsnummer}")
        throw ForbiddenResponse("Bruker har ikke enkeltrettighet nav_rekruttering_kandidater for angitt virksomhet")
    }
}
