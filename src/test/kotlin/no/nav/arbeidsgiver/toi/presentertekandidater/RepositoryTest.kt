package no.nav.arbeidsgiver.toi.presentertekandidater

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

internal class RepositoryTest {
    companion object {
        val GYLDIG_KANDIDATLISTE = Kandidatliste(
            stillingId = UUID.randomUUID(),
            tittel = "Tittel",
            status = Kandidatliste.Status.ÅPEN,
            virksomhetsnummer = "123456789"
        )
    }

    val repository = opprettTestRepositoryMedLokalPostgres()


    @Test
    fun `Persistering og henting av kandidatliste går OK`() {
        repository.lagre(GYLDIG_KANDIDATLISTE)
        repository.hentKandidatliste(GYLDIG_KANDIDATLISTE.stillingId).apply {
            assertThat(this?.id).isNotNull
            assertThat(this?.tittel).isEqualTo(GYLDIG_KANDIDATLISTE.tittel)
            assertThat(this?.status).isEqualTo(GYLDIG_KANDIDATLISTE.status)
            assertThat(this?.virksomhetsnummer).isEqualTo(GYLDIG_KANDIDATLISTE.virksomhetsnummer)
        }
    }

    @Test
    fun `Persistering og henting av kandidat går OK`() {
        repository.lagre(GYLDIG_KANDIDATLISTE)
        val kandidatliste = repository.hentKandidatliste(GYLDIG_KANDIDATLISTE.stillingId)

        val kandidat = Kandidat(
            aktørId = "1234567891012",
            kandidatlisteId = kandidatliste!!.id!!,
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.AKTUELL,
            hendelsestype = "Type",
        )
        repository.lagre(kandidat)

        repository.hentKandidat(kandidat.aktørId).apply {
            assertThat(this?.aktørId).isEqualTo(kandidat.aktørId)
            assertThat(this?.kandidatlisteId).isEqualTo(kandidat.kandidatlisteId)
            assertThat(this?.arbeidsgiversVurdering).isEqualTo(kandidat.arbeidsgiversVurdering)
            assertThat(this?.hendelsestidspunkt).isNotNull // Precision is different on server and locally
            assertThat(this?.hendelsestype).isEqualTo(kandidat.hendelsestype)
        }
    }

    @Test
    fun `Henting av kandidatlister med antall hvor listen har kandidater`() {
        repository.lagre(GYLDIG_KANDIDATLISTE)
        val kandidatliste = repository.hentKandidatliste(GYLDIG_KANDIDATLISTE.stillingId)
        val kandidater = listOf(
            Kandidat(
                aktørId = "1234567891012",
                kandidatlisteId = kandidatliste?.id!!,
                arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.AKTUELL,
                hendelsestype = "Type",
            ),
            Kandidat(
                aktørId = "2234567891012",
                kandidatlisteId = kandidatliste.id!!,
                arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.AKTUELL,
                hendelsestype = "Type",
            )
        )
        kandidater.forEach { repository.lagre(it)}

        val kandidatlister = repository.hentKandidatlisterMedAntall(GYLDIG_KANDIDATLISTE.virksomhetsnummer)
        assertThat(kandidatlister.size).isEqualTo(1)
        assertThat(kandidatlister[0].antallKandidater).isEqualTo(2)
    }

    @Test
    fun `Henting av kandidatlister med antall hvor listen IKKE har kandidater`() {
        repository.lagre(GYLDIG_KANDIDATLISTE)
        val kandidatlister = repository.hentKandidatlisterMedAntall(GYLDIG_KANDIDATLISTE.virksomhetsnummer)
        assertThat(kandidatlister.size).isEqualTo(1)
        assertThat(kandidatlister[0].antallKandidater).isEqualTo(0)
    }

    @Test
    fun `Henting av kandidatliste med kandidater`() {
        repository.lagre(GYLDIG_KANDIDATLISTE)
        val kandidatliste = repository.hentKandidatliste(GYLDIG_KANDIDATLISTE.stillingId)
        repository.lagre(Kandidat(
            aktørId = "test",
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.AKTUELL,
            kandidatlisteId = kandidatliste?.id!!,
            hendelsestype = "type"
        ))

        val listeMedKandidater = repository.hentKandidatlisteMedKandidater(GYLDIG_KANDIDATLISTE.stillingId)
        assertThat(listeMedKandidater).isNotNull
        assertThat(listeMedKandidater?.kandidater?.size).isEqualTo(1)
        assertThat(listeMedKandidater?.kandidater!![0].kandidatlisteId).isEqualTo(kandidatliste.id)
    }

    @Test
    fun `Henting av kandidatliste med tom liste kandidater`() {
        repository.lagre(GYLDIG_KANDIDATLISTE)
        val listeMedKandidater = repository.hentKandidatlisteMedKandidater(GYLDIG_KANDIDATLISTE.stillingId)
        assertThat(listeMedKandidater).isNotNull
        assertThat(listeMedKandidater?.kandidater).isEmpty()
    }
}
