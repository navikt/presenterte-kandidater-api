package no.nav.arbeidsgiver.toi.presentertekandidater

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
}
