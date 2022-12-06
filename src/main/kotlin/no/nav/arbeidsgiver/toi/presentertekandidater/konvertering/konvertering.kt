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


val konverterFraArbeidsmarked: (repository: Repository, openSearchKlient: OpenSearchKlient, konverteringsfilstier: KonverteringFilstier) -> (Context) -> Unit =
    { repository, openSearchKlient, konverteringsfilstier ->
        { context ->
            try {
                log("konvertering").info("Starter konvertering fra arbeidsmarked")

                val konverteringstidspunkt = ZonedDateTime.now()

                val kandidatlisterArbeidsmarked: List<Arbeidsmarked.Kandidatlister> =
                    defaultObjectMapper.readValue(
                        konverteringsfilstier.kandidatlistefil.readText(Charsets.UTF_8),
                        object : TypeReference<List<Arbeidsmarked.Kandidatlister>>() {})

                val kandidaterArbeidsmarked: List<Arbeidsmarked.Kandidater> =
                    defaultObjectMapper.readValue(
                        konverteringsfilstier.kandidatfil.readText(Charsets.UTF_8),
                        object : TypeReference<List<Arbeidsmarked.Kandidater>>() {})

                kandidatlisterArbeidsmarked.forEach { liste ->
                    val stillingId = UUID.fromString(liste.stilling_id)

                    val kandidaterForKandidatliste = kandidaterArbeidsmarked
                        .filter { it.stilling_id == liste.stilling_id }
                        .distinctBy { it.kandidatnr }

                    if (kandidaterForKandidatliste.isEmpty()) return@forEach

                    val opprettetTidspunkt = LocalDateTime.parse(
                        liste.opprettet_tidspunkt,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    ).atZone(ZoneId.of("Europe/Oslo"))

                    val kandidatliste = Kandidatliste(
                        id = null,
                        uuid = UUID.randomUUID(),
                        stillingId = stillingId,
                        tittel = liste.tittel,
                        status = Kandidatliste.Status.ÅPEN, // Obs! Listen kan være lukket i rekrutteringsbistand, men det er greit :)
                        slettet = false,
                        virksomhetsnummer = liste.organisasjon_referanse,
                        sistEndret = konverteringstidspunkt,
                        opprettet = opprettetTidspunkt
                    )

                    repository.lagre(kandidatliste)
                    val listeFraDb = repository.hentKandidatliste(stillingId)
                    val listeId = listeFraDb?.id

                    if (listeId == null) {
                        error("Kandidatliste med stillingsid $stillingId har ingen id, terminerer konvertering")
                    }

                    val arbeidsmarkedKandidaterForListe = kandidaterForKandidatliste
                        .map {
                            val aktørId = openSearchKlient.hentAktørid(it.kandidatnr)

                            if (aktørId == null) {
                                log("konvertering").info("Aktør med kandidatnr ${it.kandidatnr} for stilling ${it.stilling_id} ligger ikke som synlig i rekrutteringsbistand, konverteres ikke")
                                null
                            } else {
                                Kandidat(
                                    id = null,
                                    uuid = UUID.randomUUID(),
                                    aktørId = aktørId ?: "",
                                    kandidatlisteId = listeId,
                                    arbeidsgiversVurdering = konverterVurdering(status = it.kandidatstatus),
                                    sistEndret = LocalDateTime.parse(
                                        it.endret_tidspunkt,
                                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                    ).atZone(ZoneId.of("Europe/Oslo"))
                                )
                            }
                        }.filterNotNull()

                    arbeidsmarkedKandidaterForListe.forEach {
                        repository.lagre(it)
                    }

                }
            } catch (e: Exception) {
                log("konvertering").error("feil i konvertering", e)
                throw e
            }

            context.status(200)
        }
    }

fun konverterVurdering(status: String): ArbeidsgiversVurdering {
    return when (status) {
        "NY", "PAABEGYNT" -> ArbeidsgiversVurdering.TIL_VURDERING
        "AKTUELL" -> ArbeidsgiversVurdering.AKTUELL
        "IKKE_AKTUELL" -> ArbeidsgiversVurdering.IKKE_AKTUELL
        else -> ArbeidsgiversVurdering.TIL_VURDERING
    }
}

class KonverteringFilstier(envs: Map<String, String>) {
    val kandidatfil: File
    val kandidatlistefil: File

    init {
        val filområde = when (envs["NAIS_CLUSTER_NAME"]) {
            "prod-gcp", "dev-gcp" -> "./tmp"
            else -> "./src/test/resources"
        }
        kandidatlistefil = File("$filområde/kandidatlister-konvertering.json")
        kandidatfil = File("$filområde/kandidater-konvertering.json")
    }
}

class Arbeidsmarked {
    data class Kandidater(
        val kandidatnr: String,
        val lagt_til_tidspunkt: String,
        val endret_tidspunkt: String,
        val stilling_id: String,
        val kandidatstatus: String,
    )

    data class Kandidatlister(
        val db_id: Int,
        val opprettet_tidspunkt: String,
        val organisasjon_referanse: String,
        val stilling_id: String,
        val tittel: String,
    )
}