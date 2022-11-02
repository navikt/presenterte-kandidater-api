package no.nav.arbeidsgiver.toi.presentertekandidater

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.LocalDate
import java.util.UUID

internal class RepositoryTest {

    val repository = opprettTestRepositoryMedLokalPostgres()

    @Test
    fun `Persistering av kandidatliste går OK`() {
        val kandidatliste = Kandidatliste(
            stillingId = UUID.randomUUID(),
            tittel = "Tittel",
            status = "Status",
            virksomhetsnummer = "123456789"
        )
        repository.lagre(kandidatliste)
        val kandidatlistePersistert = repository.hentKandidatliste(kandidatliste.stillingId)

        kandidatlistePersistert.apply {
            assertThat(this?.id).isNotNull
            assertThat(this?.tittel).isEqualTo(kandidatliste.tittel)
            assertThat(this?.status).isEqualTo(kandidatliste.status)
            assertThat(this?.virksomhetsnummer).isEqualTo(kandidatliste.virksomhetsnummer)
        }
    }

    @Test
    fun `Persistering av kandidat går OK`() {
        val kandidat = Kandidat(
            aktørId = "1234567891012",
            kandidatlisteId = BigInteger.ONE,
            arbeidsgiversStatus = "Status",
            hendelsestidspunkt = LocalDate.now(),
            hendelsestype = "Type",
        )

        repository.lagre(kandidat)

        repository.hentKandidat(kandidat.aktørId).apply {
            assertThat(this?.aktørId).isEqualTo(kandidat.aktørId)
            assertThat(this?.kandidatlisteId).isEqualTo(kandidat.kandidatlisteId)
            assertThat(this?.arbeidsgiversStatus).isEqualTo(kandidat.arbeidsgiversStatus)
            assertThat(this?.hendelsestidspunkt).isEqualTo(kandidat.hendelsestidspunkt)
            assertThat(this?.hendelsestype).isEqualTo(kandidat.hendelsestype)
        }
    }
}
