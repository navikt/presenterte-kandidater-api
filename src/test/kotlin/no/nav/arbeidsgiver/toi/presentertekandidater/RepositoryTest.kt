package no.nav.arbeidsgiver.toi.presentertekandidater

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.assertFails

internal class RepositoryTest {
    companion object {
        val GYLDIG_KANDIDATLISTE = Kandidatliste(
            stillingId = UUID.randomUUID(),
            tittel = "Tittel",
            status = Kandidatliste.Status.ÅPEN,
            virksomhetsnummer = "123456789",
            uuid = UUID.fromString("7ea380f8-a0af-433f-8cbc-51c5788a7d29"),
            sistEndret = ZonedDateTime.now()
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

        val uuid = UUID.randomUUID()

        val kandidat = Kandidat(
            aktørId = "1234567891012",
            kandidatlisteId = kandidatliste!!.id!!,
            uuid = uuid
        )
        repository.lagre(kandidat)

        repository.hentKandidat(kandidat.aktørId, kandidatliste.id!!).apply {
            assertThat(this?.aktørId).isEqualTo(kandidat.aktørId)
            assertThat(this?.kandidatlisteId).isEqualTo(kandidat.kandidatlisteId)
            assertThat(this?.uuid).isEqualTo(uuid)
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
                uuid = UUID.randomUUID()
            ),
            Kandidat(
                aktørId = "2234567891012",
                kandidatlisteId = kandidatliste.id!!,
                uuid = UUID.randomUUID()
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
        val kandidatUUID = UUID.randomUUID()
        repository.lagre(Kandidat(
            aktørId = "test",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = kandidatUUID
        ))

        val listeMedKandidater = repository.hentKandidatlisteMedKandidater(GYLDIG_KANDIDATLISTE.stillingId)
        assertThat(listeMedKandidater).isNotNull
        assertThat(listeMedKandidater?.kandidater?.size).isEqualTo(1)
        assertThat(listeMedKandidater?.kandidater!![0].kandidatlisteId).isEqualTo(kandidatliste.id)
        assertThat(listeMedKandidater?.kandidater[0].uuid).isEqualTo(kandidatUUID)
    }

    @Test
    fun `Henting av kandidatliste med tom liste kandidater`() {
        repository.lagre(GYLDIG_KANDIDATLISTE)
        val listeMedKandidater = repository.hentKandidatlisteMedKandidater(GYLDIG_KANDIDATLISTE.stillingId)
        assertThat(listeMedKandidater).isNotNull
        assertThat(listeMedKandidater?.kandidater).isEmpty()
    }

    @Test
    fun `Skal ikke kunne lagre to kandidatlister med samme stillingsId`() {
        val fellesStillingid = UUID.randomUUID()

        val kandidatliste1 = Kandidatliste(
            stillingId = fellesStillingid,
            tittel = "Tittel",
            status = Kandidatliste.Status.ÅPEN,
            virksomhetsnummer = "123456789",
            uuid = UUID.fromString("7ea380f8-a0af-433f-8cbc-51c5788a7d29"),
            sistEndret = ZonedDateTime.now()
        )

        val kandidatliste2 = Kandidatliste(
            stillingId = fellesStillingid,
            tittel = "Tittel2",
            status = Kandidatliste.Status.LUKKET,
            virksomhetsnummer = "123456780",
            uuid = UUID.fromString("7ea380f8-a0af-433f-8cbc-51c5788a7d28"),
            sistEndret = ZonedDateTime.now()
        )


        repository.lagre(kandidatliste1)
 
        assertThatThrownBy{
            repository.lagre(kandidatliste2)
        }.isInstanceOf(PSQLException::class.java)

    }

    @Test
    fun `Skal kunne lagre samme kandidat i to lister`() {
        fail<String>("Ikke implementert")
    }
}
