package no.nav.arbeidsgiver.toi.presentertekandidater

import java.math.BigInteger

class PresenterteKandidaterService(private val repository: Repository) {

    fun lagreKandidathendelse(kandidathendelse: Kandidathendelse, stillingstittel : String) {
        //Endringer på kandidatliste siden sist?
        //val kandidatliste = lagreKandidatliste(kandidathendelse, stillingstittel)
        //lagreKandidat(kandidathendelse, kandidatliste.id!!)
    }

    private fun lagreKandidatliste(kandidathendelse: Kandidathendelse, stillingstittel : String) : Kandidatliste {
        val kandidatliste = mapKandidathendelseToKandidatliste(kandidathendelse, stillingstittel)
        repository.lagre(kandidatliste) //returnere objektet for id?
        return kandidatliste
    }

    private fun lagreKandidat(kandidathendelse: Kandidathendelse, kandidatlisteId: BigInteger) {
        val kandidat = mapKandidathendelseToKandidat(kandidathendelse, kandidatlisteId)
        repository.lagre(kandidat)
    }

    private fun mapKandidathendelseToKandidat(hendelse: Kandidathendelse, kandidatlisteId : BigInteger) : Kandidat {
        return Kandidat(
            aktørId = hendelse.aktørId,
            kandidatlisteId = kandidatlisteId,
            hendelsestidspunkt = hendelse.tidspunkt.toLocalDateTime(),
            hendelsestype = hendelse.type.toString(),
            arbeidsgiversStatus = "dummy-IKKE_VURDERT"
        )
    }

    private fun mapKandidathendelseToKandidatliste(hendelse: Kandidathendelse, stillingstittel: String) : Kandidatliste {
        return Kandidatliste(
            stillingId = hendelse.stillingsId,
            tittel = stillingstittel,
            status = "dummy-PÅGÅENDE",
            slettet = hendelse.type.equals("dummy-SLETTET"),
            virksomhetsnummer = hendelse.organisasjonsnummer
        )
    }



}