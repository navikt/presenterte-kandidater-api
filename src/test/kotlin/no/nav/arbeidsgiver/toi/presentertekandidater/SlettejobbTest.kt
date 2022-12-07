package no.nav.arbeidsgiver.toi.presentertekandidater

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlettejobbTest {
    private val repository = opprettTestRepositoryMedLokalPostgres()

    @Test
    fun `Slettejobb skal slette tomme kandidatlister som ikke er endret på 6mnd`() {
        var kandidatlisteSomSkalSlettes = Testdata.lagGyldigKandidatliste(UUID.randomUUID()).copy(
            sistEndret = ZonedDateTime.now().minusMonths(6)
        )
        var kandidatlisteSomIkkeSkalSlettes = Testdata.lagGyldigKandidatliste(UUID.randomUUID())
        repository.lagre(kandidatlisteSomSkalSlettes)
        repository.lagre(kandidatlisteSomIkkeSkalSlettes)

        kandidatlisteSomSkalSlettes = repository.hentKandidatliste(kandidatlisteSomSkalSlettes.stillingId)!!
        kandidatlisteSomIkkeSkalSlettes = repository.hentKandidatliste(kandidatlisteSomIkkeSkalSlettes.stillingId)!!
        assertFalse(kandidatlisteSomSkalSlettes.slettet)
        assertFalse(kandidatlisteSomIkkeSkalSlettes.slettet)

        slettKandidaterOgKandidatlister(repository)

        kandidatlisteSomSkalSlettes = repository.hentKandidatliste(kandidatlisteSomSkalSlettes.stillingId)!!
        kandidatlisteSomIkkeSkalSlettes = repository.hentKandidatliste(kandidatlisteSomIkkeSkalSlettes.stillingId)!!
        assertTrue(kandidatlisteSomSkalSlettes.slettet)
        assertFalse(kandidatlisteSomIkkeSkalSlettes.slettet)
    }

    @Test
    fun `Slettejobb skal slette kandidater som ikke er endret på 6mnd`() {
        var kandidatliste = Testdata.lagGyldigKandidatliste(UUID.randomUUID()).copy(
            sistEndret = ZonedDateTime.now()
        )
        repository.lagre(kandidatliste)
        kandidatliste = repository.hentKandidatliste(kandidatliste.stillingId)!!

        var kandidatSomSkalSlettes = Testdata.lagKandidatTilKandidatliste(kandidatliste.id!!).copy(sistEndret = ZonedDateTime.now().minusMonths(6))
        var kandidatSomIkkeSkalSlettes = Testdata.lagKandidatTilKandidatliste(kandidatliste.id!!)
        repository.lagre(kandidatSomSkalSlettes)
        repository.lagre(kandidatSomIkkeSkalSlettes)

        assertEquals(2, repository.hentKandidater(kandidatliste.id!!).size)

        slettKandidaterOgKandidatlister(repository)

        assertEquals(1, repository.hentKandidater(kandidatliste.id!!).size)
    }

    @Test
    fun `Slettejobb skal ikke slette gamle kandidatlister med kandidater`() {
        var kandidatliste = Testdata.lagGyldigKandidatliste(UUID.randomUUID()).copy(
            sistEndret = ZonedDateTime.now().minusMonths(8)
        )
        repository.lagre(kandidatliste)
        kandidatliste = repository.hentKandidatliste(kandidatliste.stillingId)!!

        var nyKandidat = Testdata.lagKandidatTilKandidatliste(kandidatliste.id!!)
        repository.lagre(nyKandidat)

        slettKandidaterOgKandidatlister(repository)

        assertEquals(1, repository.hentKandidater(kandidatliste.id!!).size)
        assertNotNull(repository.hentKandidatliste(kandidatliste.stillingId))
    }
}
