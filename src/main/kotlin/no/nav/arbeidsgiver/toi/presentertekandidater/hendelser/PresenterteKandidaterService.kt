package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser

import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.KandidatlisteRepository
import java.math.BigInteger
import java.time.ZonedDateTime
import java.util.*

class PresenterteKandidaterService(private val kandidatlisteRepository: KandidatlisteRepository) {

    fun lagreCvDeltHendelse(organisasjonsnummer: String, stillingsId: UUID, stillingstittel: String, aktørIder: List<String>) {
        val kandidatliste = kandidatlisteRepository.hentKandidatliste(stillingsId)

        if (kandidatliste == null) {
            kandidatlisteRepository.lagre(
                Kandidatliste.ny(
                    stillingId = stillingsId,
                    tittel = stillingstittel,
                    virksomhetsnummer = organisasjonsnummer
                )
            )
        } else {
            kandidatlisteRepository.oppdater(
                kandidatliste.copy(
                    tittel = stillingstittel,
                    status = Kandidatliste.Status.ÅPEN,
                    slettet = false
                )
            )
        }

        val kandidatlisteLagret = kandidatlisteRepository.hentKandidatliste(stillingsId)
            ?: throw RuntimeException("Alvorlig feil - kandidatliste skal ikke kunne være null")

        aktørIder.forEach {
            val kandidat = kandidatlisteRepository.hentKandidat(it, kandidatlisteLagret.id!!)
            if (kandidat == null) {

                kandidatlisteRepository.lagre(Kandidat(
                    aktørId = it,
                    kandidatlisteId = kandidatlisteLagret.id,
                    uuid = UUID.randomUUID(),
                    arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
                    sistEndret = ZonedDateTime.now()
                ))
            }
        }
    }

    fun markerKandidatlisteSomSlettet(stillingsId: UUID) {
        kandidatlisteRepository.markerKandidatlisteSomSlettet(stillingsId)
    }

    fun lukkKandidatliste(stillingsId: UUID) {
        kandidatlisteRepository.lukkKandidatliste(stillingsId)
    }

    fun slettKandidatFraKandidatliste(aktørId: String, stillingsId: UUID) {
        val kandidatliste = kandidatlisteRepository.hentKandidatliste(stillingsId)
        if (kandidatliste != null) {
            kandidatlisteRepository.slettKandidatFraKandidatliste(aktørId, kandidatliste.id!!)
        }
    }
}
