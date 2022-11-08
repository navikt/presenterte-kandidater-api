package no.nav.arbeidsgiver.toi.presentertekandidater

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class RepositoryTest {

    val repository = opprettTestRepositoryMedLokalPostgres()

    val GYLDIG_KANDIDATLISTE = Kandidatliste(
        stillingId = UUID.randomUUID(),
        tittel = "Tittel",
        status = "Status",
        virksomhetsnummer = "123456789"
    )

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
}
