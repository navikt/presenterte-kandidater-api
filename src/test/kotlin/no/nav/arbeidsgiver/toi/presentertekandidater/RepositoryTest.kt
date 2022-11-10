package no.nav.arbeidsgiver.toi.presentertekandidater

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.math.BigInteger
import java.time.ZonedDateTime
import java.util.UUID

internal class RepositoryTest {
    
    val repository = opprettTestRepositoryMedLokalPostgres()

    @Test
    fun `Persistering og henting av kandidatliste går OK`() {
        repository.lagre(lagKandidatliste())
        repository.hentKandidatliste(lagKandidatliste().stillingId).apply {
            assertThat(this?.id).isNotNull
            assertThat(this?.tittel).isEqualTo(lagKandidatliste().tittel)
            assertThat(this?.status).isEqualTo(lagKandidatliste().status)
            assertThat(this?.virksomhetsnummer).isEqualTo(lagKandidatliste().virksomhetsnummer)
        }
    }

    @Test
    fun `Persistering og henting av kandidat går OK`() {
        repository.lagre(lagKandidatliste())
        val kandidatliste = repository.hentKandidatliste(lagKandidatliste().stillingId)

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
        repository.lagre(lagKandidatliste())
        val kandidatliste = repository.hentKandidatliste(lagKandidatliste().stillingId)
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

        val kandidatlister = repository.hentKandidatlisterMedAntall(lagKandidatliste().virksomhetsnummer)
        assertThat(kandidatlister.size).isEqualTo(1)
        assertThat(kandidatlister[0].antallKandidater).isEqualTo(2)
    }

    @Test
    fun `Henting av kandidatlister med antall hvor listen IKKE har kandidater`() {
        repository.lagre(lagKandidatliste())
        val kandidatlister = repository.hentKandidatlisterMedAntall(lagKandidatliste().virksomhetsnummer)
        assertThat(kandidatlister.size).isEqualTo(1)
        assertThat(kandidatlister[0].antallKandidater).isEqualTo(0)
    }

    @Test
    fun `Henting av kandidatliste med kandidater`() {
        repository.lagre(lagKandidatliste())
        val kandidatliste = repository.hentKandidatliste(lagKandidatliste().stillingId)
        val kandidatUUID = UUID.randomUUID()
        repository.lagre(Kandidat(
            aktørId = "test",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = kandidatUUID
        ))

        val listeMedKandidater = repository.hentKandidatlisteMedKandidater(lagKandidatliste().stillingId)
        assertThat(listeMedKandidater).isNotNull
        assertThat(listeMedKandidater?.kandidater?.size).isEqualTo(1)
        assertThat(listeMedKandidater?.kandidater!![0].kandidatlisteId).isEqualTo(kandidatliste.id)
        assertThat(listeMedKandidater?.kandidater[0].uuid).isEqualTo(kandidatUUID)
    }

    @Test
    fun `Henting av kandidatliste med tom liste kandidater`() {
        repository.lagre(lagKandidatliste())
        val listeMedKandidater = repository.hentKandidatlisteMedKandidater(lagKandidatliste().stillingId)
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
        val aktørID = "123456789"
        val kandidatliste1 = lagKandidatliste()
        val kandidatliste2 = lagKandidatliste()
        repository.lagre(kandidatliste1)
        repository.lagre(kandidatliste2)
        val lagretKandidatliste1 = repository.hentKandidatliste(kandidatliste1.stillingId)
        val lagretKandidatliste2 = repository.hentKandidatliste(kandidatliste2.stillingId)
        val kandidatPåListe1 = lagKandidat(lagretKandidatliste1?.id!!, aktørID)
        val kandidatPåListe2 = lagKandidat(lagretKandidatliste2?.id!!, aktørID)

        repository.lagre(kandidatPåListe1)
        repository.lagre(kandidatPåListe2)

        val kandidatliste1MedKandidater = repository.hentKandidatlisteMedKandidater(kandidatliste1.stillingId)
        val kandidatliste2MedKandidater = repository.hentKandidatlisteMedKandidater(kandidatliste2.stillingId)
        assertThat(kandidatliste1MedKandidater!!.kandidater).hasSize(1)
        assertThat(kandidatliste2MedKandidater!!.kandidater).hasSize(1)
        assertThat(kandidatliste1MedKandidater.kandidater.first().aktørId).isEqualTo(aktørID)
        assertThat(kandidatliste2MedKandidater.kandidater.first().aktørId).isEqualTo(aktørID)
    }
    
    private fun lagKandidatliste() = Kandidatliste(
        stillingId = UUID.randomUUID(),
        tittel = "Tittel",
        status = Kandidatliste.Status.ÅPEN,
        virksomhetsnummer = "123456789",
        uuid = UUID.fromString("7ea380f8-a0af-433f-8cbc-51c5788a7d29"),
        sistEndret = ZonedDateTime.now()
    )

    private fun lagKandidat(kandidatlisteId: BigInteger, aktørId: String) = Kandidat(
        uuid = UUID.randomUUID(),
        aktørId = aktørId,
        kandidatlisteId = kandidatlisteId
    )
}
