package no.nav.arbeidsgiver.toi.presentertekandidater.hendelser

import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.KandidatlisteRepository
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import java.math.BigInteger
import java.time.ZonedDateTime
import java.util.*

class PresenterteKandidaterService(private val kandidatlisteRepository: KandidatlisteRepository) {

    fun lagreCvDeltHendelse(kandidathendelse: Kandidathendelse, stillingstittel: String) {
        val kandidatliste = kandidatlisteRepository.hentKandidatliste(kandidathendelse.stillingsId)

        if (kandidatliste == null) {
            val nyKandidatliste = Kandidatliste.ny(
                stillingId = kandidathendelse.stillingsId,
                tittel = stillingstittel,
                virksomhetsnummer = kandidathendelse.organisasjonsnummer
            )
            kandidatlisteRepository.lagre(nyKandidatliste)
        } else {
            val oppdatertKandidatliste = kandidatliste.copy(
                tittel = stillingstittel,
                status = Kandidatliste.Status.ÅPEN,
                slettet = false
            )
            kandidatlisteRepository.oppdater(oppdatertKandidatliste)
        }

        val kandidatlisteLagret = kandidatlisteRepository.hentKandidatliste(kandidathendelse.stillingsId)
            ?: throw RuntimeException("Alvorlig feil - kandidatliste skal ikke kunne være null")

        val kandidat = kandidatlisteRepository.hentKandidat(kandidathendelse.aktørId, kandidatlisteLagret.id!!)
        if (kandidat == null) {
            lagreKandidat(kandidathendelse, kandidatlisteLagret.id)
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

    private fun lagreKandidat(kandidathendelse: Kandidathendelse, kandidatlisteId: BigInteger) {
        val kandidat = mapKandidathendelseTilNyKandidat(kandidathendelse, kandidatlisteId)
        kandidatlisteRepository.lagre(kandidat)
    }

    private fun mapKandidathendelseTilNyKandidat(hendelse: Kandidathendelse, kandidatlisteId: BigInteger): Kandidat {
        return Kandidat(
            aktørId = hendelse.aktørId,
            kandidatlisteId = kandidatlisteId,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = ZonedDateTime.now()
        )
    }
}
