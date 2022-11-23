package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.put
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.Context
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.AltinnKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.Rolle
import java.io.File
import java.util.UUID
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

private val objectMapper: ObjectMapper = jacksonObjectMapper()

fun startController(
    javalin: Javalin,
    repository: Repository,
    openSearchKlient: OpenSearchKlient,
    altinnKlient: AltinnKlient
) {
    javalin.routes {
        get("/organisasjoner", hentOrganisasjoner(altinnKlient), Rolle.ARBEIDSGIVER)
        get("/kandidatlister", hentKandidatlister(repository), Rolle.ARBEIDSGIVER)
        get("/kandidatliste/{stillingId}", hentKandidatliste(repository, openSearchKlient), Rolle.ARBEIDSGIVER)
        put("/kandidat/{uuid}/vurdering", oppdaterArbeidsgiversVurdering(repository), Rolle.ARBEIDSGIVER)
        post("/internal/konverterdata", konverterFraArbeidsmarker(repository, openSearchKlient), Rolle.UNPROTECTED)
    }.exception(IllegalArgumentException::class.java) { e, ctx ->
        log("controller").warn("Kall mot ${ctx.path()} feiler på grunn av ugyldig input.", e)
        ctx.status(400)
    }
}

private val konverterFraArbeidsmarker: (repository: Repository, openSearchKlient: OpenSearchKlient) -> (Context) -> Unit =
    { repository, openSearchKlient ->
        { context ->
            log("konvertering").info("Starter konvertering fra arbeidsmarked")

            val kandidatlisterArbeidsmarked: List<KandidatlisterArbeidsmarked> =
                objectMapper.readValue(
                    File("./src/test/resources/kandidatlister-test.json").readText(Charsets.UTF_8),
                    object : TypeReference<List<KandidatlisterArbeidsmarked>>() {})

            val kandidaterArbeidsmarked: List<KandidaterArbeidsmarked> =
                objectMapper.readValue(
                    File("./src/test/resources/kandidater-test.json").readText(Charsets.UTF_8),
                    object : TypeReference<List<KandidaterArbeidsmarked>>() {})

            log("konvertering").info("lister: $kandidatlisterArbeidsmarked")
            log("konvertering").info("kandiater: $kandidaterArbeidsmarked")


            kandidatlisterArbeidsmarked.forEach { liste ->
                val stillingId = UUID.fromString(liste.stilling_id)

                val opprettetTidspunkt = LocalDateTime.parse(
                    liste.opprettet_tidspunkt,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                ).atZone(ZoneId.of("Europe/Oslo"))

                val kandidatliste = Kandidatliste(
                    id = null,
                    uuid = UUID.randomUUID(),
                    stillingId = stillingId,
                    tittel = liste.tittel,
                    status = Kandidatliste.Status.ÅPEN,
                    slettet = false,
                    virksomhetsnummer = liste.organisasjon_referanse,
                    sistEndret = ZonedDateTime.now(),
                    opprettet = opprettetTidspunkt
                )
                log("konvertering").info("lister for lagring: $kandidatliste")
                repository.lagre(kandidatliste)
                val listeFraDb = repository.hentKandidatliste(stillingId)
                val listeId = listeFraDb?.id

                if (listeId != null) {

                    val arbeidsmarkedKandidaterForListe = kandidaterArbeidsmarked
                        .filter { it.stilling_id == liste.stilling_id }
                        .distinctBy { it.kandidatnr }
                        .map {
                           val aktørId = openSearchKlient.hentAktørid(it.kandidatnr)

                            Kandidat(
                                id = null,
                                uuid = UUID.randomUUID(),
                                aktørId = aktørId?:"",  // TODO: Hent fra opensearch fra kandidatnummer
                                kandidatlisteId = listeId,
                                arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.IKKE_AKTUELL,    // TODO mappes fra json
                                sistEndret = ZonedDateTime.now()
                            )
                        }

                    arbeidsmarkedKandidaterForListe.forEach {
                        repository.lagre(it)
                    }
                }
            }

            context.status(200)
        }
    }

data class KandidaterArbeidsmarked(
    val kandidatnr: String,
    val lagt_til_tidspunkt: String,
    val stilling_id: String
)

data class KandidatlisterArbeidsmarked(
    val db_id: Int,
    val opprettet_tidspunkt: String,
    val organisasjon_referanse: String,
    val stilling_id: String,
    val tittel: String
)

private val oppdaterArbeidsgiversVurdering: (repository: Repository) -> (Context) -> Unit = { repository ->
    { context ->
        val kandidatUuid = UUID.fromString(context.pathParam("uuid"))
        val jsonBody = objectMapper.readTree(context.body())
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

        if (virksomhetsnummer.isNullOrBlank()) {
            context.status(400)
        } else {
            val lister: KandidatlisterDto = repository.hentKandidatlisterMedAntall(virksomhetsnummer)
            context.json(lister)
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

                if (kandidatliste == null) {
                    context.status(404)
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
            context.json(altinnKlient.hentOrganisasjoner(context.hentFødselsnummer()))
        }
    }

data class KandidatDto(
    val kandidat: Kandidat, val cv: Cv?
)

data class KandidatlisteDto(
    val kandidatliste: Kandidatliste, val kandidater: List<KandidatDto>
)

typealias KandidatlisterDto = List<KandidatlisteMedAntallKandidater>
