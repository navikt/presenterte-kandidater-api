package no.nav.arbeidsgiver.toi.presentertekandidater

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class RepositoryTest {
    companion object {
        val GYLDIG_KANDIDATLISTE = Kandidatliste(
            stillingId = UUID.randomUUID(),
            tittel = "Tittel",
            status = "Status",
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
            arbeidsgiversStatus = "Status",
            hendelsestidspunkt = LocalDateTime.now(),
            hendelsestype = "Type",
        )
        repository.lagre(kandidat)

        repository.hentKandidat(kandidat.aktørId).apply {
            assertThat(this?.aktørId).isEqualTo(kandidat.aktørId)
            assertThat(this?.kandidatlisteId).isEqualTo(kandidat.kandidatlisteId)
            assertThat(this?.arbeidsgiversStatus).isEqualTo(kandidat.arbeidsgiversStatus)
            assertThat(this?.hendelsestidspunkt).isNotNull // Precision is different on server and locally
            assertThat(this?.hendelsestype).isEqualTo(kandidat.hendelsestype)
        }
    }

    @Test
    fun `Henting av kandidatlister utifra virksomhetsnummer`() {
        listOf(
            GYLDIG_KANDIDATLISTE,
            GYLDIG_KANDIDATLISTE.copy(stillingId = UUID.randomUUID(), virksomhetsnummer = "2"),
            GYLDIG_KANDIDATLISTE.copy(stillingId = UUID.randomUUID()),
        ).forEach { repository.lagre(it) }

        val kandidatlister = repository.hentKandidatlister(GYLDIG_KANDIDATLISTE.virksomhetsnummer)
        assertThat(kandidatlister.size).isEqualTo(2)
    }

    @Test
    fun `Henting av kandidatlister med antall hvor listen har kandidater`() {
        repository.lagre(GYLDIG_KANDIDATLISTE)
        val kandidatliste = repository.hentKandidatliste(GYLDIG_KANDIDATLISTE.stillingId)
        val kandidater = listOf(
            Kandidat(
                aktørId = "1234567891012",
                kandidatlisteId = kandidatliste?.id!!,
                arbeidsgiversStatus = "Status",
                hendelsestidspunkt = LocalDateTime.now(),
                hendelsestype = "Type",
            ),
            Kandidat(
                aktørId = "2234567891012",
                kandidatlisteId = kandidatliste?.id!!,
                arbeidsgiversStatus = "Status",
                hendelsestidspunkt = LocalDateTime.now(),
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
}
