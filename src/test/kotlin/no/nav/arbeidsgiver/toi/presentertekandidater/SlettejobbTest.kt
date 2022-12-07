package no.nav.arbeidsgiver.toi.presentertekandidater

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlettejobbTest {
    private val repository = opprettTestRepositoryMedLokalPostgres()

    @Test
    fun `Slettejobb skal slette tomme kandidatlister som ikke er endret på 6mnd`() {
        var kandidatliste = Testdata.lagGyldigKandidatliste(UUID.randomUUID()).copy(
            sistEndret = ZonedDateTime.now().minusMonths(6)
        )
        repository.lagre(kandidatliste)

        kandidatliste = repository.hentKandidatliste(kandidatliste.stillingId)!!
        assertFalse(kandidatliste.slettet)

        slettKandidaterOgKandidatlister(repository)

        kandidatliste = repository.hentKandidatliste(kandidatliste.stillingId)!!
        assertTrue(kandidatliste.slettet)
    }

    @Test
    @Disabled
    fun `Slettejobb skal slette kandidater som ikke er endret på 6mnd`() {
        var kandidatliste = Testdata.lagGyldigKandidatliste(UUID.randomUUID()).copy(
            sistEndret = ZonedDateTime.now()
        )
        repository.lagre(kandidatliste)
        kandidatliste = repository.hentKandidatliste(kandidatliste.stillingId)!!

        var kandidat = Testdata.lagKandidatTilKandidatliste(kandidatliste.id!!).copy(sistEndret = ZonedDateTime.now().minusMonths(6))
        repository.lagre(kandidat)

        assertEquals(repository.hentKandidater(kandidatliste.id!!), 1)

        slettKandidaterOgKandidatlister(repository)

        assertEquals(repository.hentKandidater(kandidatliste.id!!), 0)
    }
}
