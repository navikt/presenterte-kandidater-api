package no.nav.arbeidsgiver.toi.presentertekandidater

import java.math.BigInteger
import java.time.ZonedDateTime
import java.util.*

class PresenterteKandidaterService(private val repository: Repository) {

    fun lagreKandidathendelse(kandidathendelse: Kandidathendelse, stillingstittel: String) {
        val kandidatliste = repository.hentKandidatliste(kandidathendelse.kandidatlisteId)
        if (kandidatliste == null) {
            repository.lagre(
                Kandidatliste(
                    stillingId = kandidathendelse.stillingsId,
                    uuid = UUID.randomUUID(),
                    status = Kandidatliste.Status.ÅPEN,
                    virksomhetsnummer = kandidathendelse.organisasjonsnummer,
                    tittel = stillingstittel,
                    sistEndret = ZonedDateTime.now()
                )
            )
            lagreKandidatliste(kandidathendelse, stillingstittel)
            val kandidatlisteLagret = repository.hentKandidatliste(kandidathendelse.stillingsId)

            val kandidat = repository.hentKandidat(kandidathendelse.aktørId)
            if (kandidat == null && kandidatlisteLagret?.id != null) {
                lagreKandidat(kandidathendelse, kandidatlisteLagret.id)
            }
        }

    }

    private fun lagreKandidatliste(kandidathendelse: Kandidathendelse, stillingstittel: String): Kandidatliste {
        val kandidatliste = mapKandidathendelseToKandidatliste(kandidathendelse, stillingstittel)
        repository.lagre(kandidatliste) //returnere objektet for id?
        return kandidatliste
    }

    private fun lagreKandidat(kandidathendelse: Kandidathendelse, kandidatlisteId: BigInteger) {
        val kandidat = mapKandidathendelseToKandidat(kandidathendelse, kandidatlisteId)
        repository.lagre(kandidat)
    }

    private fun mapKandidathendelseToKandidat(hendelse: Kandidathendelse, kandidatlisteId: BigInteger): Kandidat {
        return Kandidat(
            aktørId = hendelse.aktørId,
            kandidatlisteId = kandidatlisteId,
            uuid = UUID.randomUUID()
        )
    }

    private fun mapKandidathendelseToKandidatliste(hendelse: Kandidathendelse, stillingstittel: String): Kandidatliste {
        return Kandidatliste(
            stillingId = hendelse.stillingsId,
            uuid = UUID.randomUUID(),
            tittel = stillingstittel,
            status = Kandidatliste.Status.ÅPEN,
            slettet = hendelse.type.equals("dummy-SLETTET"),
            virksomhetsnummer = hendelse.organisasjonsnummer,
            sistEndret = ZonedDateTime.now()
        )
    }
}
