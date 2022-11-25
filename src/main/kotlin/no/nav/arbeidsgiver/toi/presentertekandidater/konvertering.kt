package no.nav.arbeidsgiver.toi.presentertekandidater

import com.fasterxml.jackson.core.type.TypeReference
import io.javalin.http.Context
import no.nav.arbeidsgiver.toi.presentertekandidater.Kandidat.ArbeidsgiversVurdering
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*


val konverterFraArbeidsmarked: (repository: Repository, openSearchKlient: OpenSearchKlient) -> (Context) -> Unit =
    { repository, openSearchKlient ->
        { context ->

            try {
                log("konvertering").info("Starter konvertering fra arbeidsmarked")

                val kandidatlisterArbeidsmarked: List<KandidatlisterArbeidsmarked> =
                    defaultObjectMapper.readValue(
                        File("./src/test/resources/kandidatlister-test.json").readText(Charsets.UTF_8),
                        object : TypeReference<List<KandidatlisterArbeidsmarked>>() {})

                val kandidaterArbeidsmarked: List<KandidaterArbeidsmarked> =
                    defaultObjectMapper.readValue(
                        File("./src/test/resources/kandidater-test.json").readText(Charsets.UTF_8),
                        object : TypeReference<List<KandidaterArbeidsmarked>>() {})

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
                                    aktørId = aktørId ?: "",  // TODO: Hent fra opensearch fra kandidatnummer
                                    kandidatlisteId = listeId,
                                    arbeidsgiversVurdering = konverterVurdering(utfall = it.utfall, status = it.status),    // TODO mappes fra json
                                    sistEndret = ZonedDateTime.now()
                                )
                            }

                        arbeidsmarkedKandidaterForListe.forEach {
                            repository.lagre(it)
                        }
                    }
                }
            } catch (e: Exception) {
                log("konvertering").error("feil i konvertering", e)
                throw e
            }

            context.status(200)
        }
    }

fun konverterVurdering(utfall: String, status: String): ArbeidsgiversVurdering {
    if(utfall == "FATT_JOBBEN") return ArbeidsgiversVurdering.FÅTT_JOBBEN
    return when(status) {
        "NY","PAABEGYNT" -> ArbeidsgiversVurdering.TIL_VURDERING
        "AKTUELL" -> ArbeidsgiversVurdering.AKTUELL
        "IKKE_AKTUELL" -> ArbeidsgiversVurdering.IKKE_AKTUELL
        else -> ArbeidsgiversVurdering.TIL_VURDERING
    }
}

data class KandidaterArbeidsmarked(
    val kandidatnr: String,
    val lagt_til_tidspunkt: String,
    val stilling_id: String,
    val status: String,
    val utfall: String
)

data class KandidatlisterArbeidsmarked(
    val db_id: Int,
    val opprettet_tidspunkt: String,
    val organisasjon_referanse: String,
    val stilling_id: String,
    val tittel: String
)