package no.nav.arbeidsgiver.toi.presentertekandidater

import java.math.BigInteger
import java.time.ZonedDateTime
import java.util.*

class PresenterteKandidaterService(private val repository: Repository) {

    fun lagreCvDeltHendelse(kandidathendelse: Kandidathendelse, stillingstittel: String) {
        val kandidatliste = repository.hentKandidatliste(kandidathendelse.stillingsId)

        if (kandidatliste == null) {
            val nyKandidatliste = Kandidatliste.ny(
                stillingId = kandidathendelse.stillingsId,
                tittel = stillingstittel,
                virksomhetsnummer = kandidathendelse.organisasjonsnummer
            )
            repository.lagre(nyKandidatliste)
        } else {
            val oppdatertKandidatliste = kandidatliste.copy(
                tittel = stillingstittel,
                status = Kandidatliste.Status.ÅPEN,
                slettet = false
            )
            repository.oppdater(oppdatertKandidatliste)
        }

        val kandidatlisteLagret = repository.hentKandidatliste(kandidathendelse.stillingsId)
            ?: throw RuntimeException("Alvorlig feil - kandidatliste skal ikke kunne være null")

        val kandidat = repository.hentKandidat(kandidathendelse.aktørId, kandidatlisteLagret.id!!)
        if (kandidat == null) {
            lagreKandidat(kandidathendelse, kandidatlisteLagret.id)
        }
    }

    fun markerKandidatlisteSomSlettet(stillingsId: UUID) {
        repository.markerKandidatlisteSomSlettet(stillingsId)
    }

    fun lukkKandidatliste(stillingsId: UUID) {
        repository.lukkKandidatliste(stillingsId)
    }

    fun slettKandidatFraKandidatliste(aktørId: String, stillingsId: UUID) {
        val kandidatliste = repository.hentKandidatliste(stillingsId)
        if (kandidatliste != null) {
            repository.slettKandidatFraKandidatliste(aktørId, kandidatliste.id!!)
        }
    }

    private fun lagreKandidat(kandidathendelse: Kandidathendelse, kandidatlisteId: BigInteger) {
        val kandidat = mapKandidathendelseTilNyKandidat(kandidathendelse, kandidatlisteId)
        repository.lagre(kandidat)
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
