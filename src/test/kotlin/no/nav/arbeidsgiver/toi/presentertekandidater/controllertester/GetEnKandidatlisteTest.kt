package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.util.*
import kotlin.test.assertNull
import tools.jackson.databind.JsonNode

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetEnKandidatlisteTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val httpClient = HttpClient.newHttpClient()
    private val openSearchKlient = openSearchKlient()
    private val wiremockServer = hentWiremock()

    @BeforeAll
    fun init() {
        startLocalApplication()
    }

    @Test
    fun `Skal returnere en kandidatliste og kandidater med CV`() {
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
        val vintertid = ZonedDateTime.parse("2024-01-15T12:30:00+01:00[Europe/Oslo]")
        val sommertid = ZonedDateTime.parse("2024-07-15T12:30:00+02:00[Europe/Oslo]")
        val virksomhetsnummer = "111111111"
        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummer),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        repository.lagre(
            kandidatliste().copy(
                stillingId = stillingId,
                virksomhetsnummer = virksomhetsnummer,
                sistEndret = vintertid,
                opprettet = sommertid
            )
        )

        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat1 = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = vintertid
        )
        val kandidat2 = Kandidat(
            aktørId = "666",
            kandidatlisteId = kandidatliste.id,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = sommertid
        )

        repository.lagre(kandidat1)
        repository.lagre(kandidat2)

        val aktør1 = Pair(Testdata.AktørId(kandidat1.aktørId), Testdata.Fødselsdato("1982-02-02"))
        val aktør2 = Pair(Testdata.AktørId(kandidat2.aktørId), Testdata.Fødselsdato("1983-03-03"))
        val esRespons = Testdata.flereKandidaterFraES(aktør1, aktør2)
        val esResponseCver = defaultObjectMapper.readTree(esRespons)["hits"]["hits"]
        stubHentingAvKandidater(
            requestBody = openSearchKlient.lagBodyForHentingAvCver(
                listOf(
                    kandidat1.aktørId,
                    kandidat2.aktørId
                )
            ), responsBody = esRespons
        )

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI(endepunkt))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)

        val jsonbody = response.body()
        val kandidatlisteMedKandidaterJson = defaultObjectMapper.readTree(jsonbody)
        val kandidatlisteJson = kandidatlisteMedKandidaterJson["kandidatliste"]
        val kandidaterJson = kandidatlisteMedKandidaterJson["kandidater"]

        assertThat(kandidatlisteMedKandidaterJson.propertyNames().toSet())
            .containsExactlyInAnyOrder("kandidatliste", "kandidater")
        assertThat(kandidatlisteJson.propertyNames().toSet())
            .containsExactlyInAnyOrder(
                "uuid", "stillingId", "tittel", "status", "slettet",
                "virksomhetsnummer", "sistEndret", "opprettet"
            )
        assertThat(kandidatlisteJson["sistEndret"].asString()).isEqualTo("2024-01-15T12:30:00+01:00")
        assertThat(kandidatlisteJson["opprettet"].asString()).isEqualTo("2024-07-15T12:30:00+02:00")

        assertNull(kandidatlisteJson["id"])
        assertThat(kandidatlisteJson["status"].asString()).isEqualTo(Kandidatliste.Status.ÅPEN.toString())
        assertThat(kandidatlisteJson["tittel"].asString()).isEqualTo(kandidatliste.tittel)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["sistEndret"].asString()))
            .isEqualTo(kandidatliste.sistEndret)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["opprettet"].asString()))
            .isEqualTo(kandidatliste.opprettet)
        assertThat(kandidatlisteJson["slettet"].asBoolean()).isFalse
        assertThat(UUID.fromString(kandidatlisteJson["stillingId"].asString())).isEqualTo(stillingId)
        assertThat(UUID.fromString(kandidatlisteJson["uuid"].asString())).isEqualTo(kandidatliste.uuid)
        assertThat(kandidatlisteJson["virksomhetsnummer"].asString())
            .isEqualTo(kandidatliste.virksomhetsnummer)

        assertThat(kandidaterJson).hasSize(2)
        assertThat(kandidaterJson[0].propertyNames().toSet())
            .containsExactlyInAnyOrder("kandidat", "cv")
        assertThat(kandidaterJson[0]["kandidat"].propertyNames().toSet())
            .containsExactlyInAnyOrder("uuid", "arbeidsgiversVurdering", "sistEndret", "opprettet")
        assertThat(kandidaterJson[0]["kandidat"]["sistEndret"].asString())
            .isEqualTo("2024-01-15T12:30:00+01:00")
        assertThat(kandidaterJson[1]["kandidat"]["sistEndret"].asString())
            .isEqualTo("2024-07-15T12:30:00+02:00")
        val cvJson = kandidaterJson[0]["cv"]
        assertThat(cvJson.propertyNames().toSet())
            .containsExactlyInAnyOrder(
                "fornavn", "etternavn", "bosted", "mobiltelefonnummer", "telefonnummer", "epost",
                "alder", "kompetanse", "arbeidserfaring", "sammendrag", "utdanning", "språk",
                "førerkort", "fagdokumentasjon", "godkjenninger", "andreGodkjenninger", "kurs",
                "andreErfaringer"
            )
        assertThat(cvJson["arbeidserfaring"][0].propertyNames().toSet())
            .containsExactlyInAnyOrder("fraDato", "tilDato", "arbeidsgiver", "sted", "stillingstittel", "beskrivelse")
        assertThat(cvJson["utdanning"][0].propertyNames().toSet())
            .containsExactlyInAnyOrder("utdanningsretning", "beskrivelse", "utdannelsessted", "fra", "til")
        assertThat(cvJson["språk"][0].propertyNames().toSet())
            .containsExactlyInAnyOrder("navn", "muntlig", "skriftlig")
        assertThat(cvJson["førerkort"][0].propertyNames().toSet())
            .containsExactlyInAnyOrder("førerkortKodeKlasse")
        assertThat(kandidaterJson[1]["cv"]["andreGodkjenninger"][0].propertyNames().toSet())
            .containsExactlyInAnyOrder("tittel", "dato")
        assertThat(cvJson["kurs"][0].propertyNames().toSet())
            .containsExactlyInAnyOrder("tittel", "omfangVerdi", "omfangEnhet", "tilDato")
        assertThat(cvJson["andreErfaringer"][0].propertyNames().toSet())
            .containsExactlyInAnyOrder("rolle", "beskrivelse", "fraDato", "tilDato")
        assertKandidat(kandidaterJson[0], kandidat1)
        assertKandidat(kandidaterJson[1], kandidat2)
        assertCv(kandidaterJson[0]["cv"], esResponseCver[0])
        assertCv(kandidaterJson[1]["cv"], esResponseCver[1])
    }

    @Test
    fun `Skal ikke returnere kandidatliste og kandidater med CV hvis samtykke mangler`() {
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
        val nå = ZonedDateTime.now()
        val virksomhetsnummer = "111111111"
        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummer),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        repository.lagre(kandidatliste().copy(stillingId = stillingId, virksomhetsnummer = virksomhetsnummer))

        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat1 = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = nå
        )
        val kandidat2 = Kandidat(
            aktørId = "666",
            kandidatlisteId = kandidatliste.id,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = nå
        )

        repository.lagre(kandidat1)
        repository.lagre(kandidat2)

        val aktør1 = Pair(Testdata.AktørId(kandidat1.aktørId), Testdata.Fødselsdato("1982-02-02"))
        val aktør2 = Pair(Testdata.AktørId(kandidat2.aktørId), Testdata.Fødselsdato("1983-03-03"))
        val esRespons = Testdata.flereKandidaterFraES(aktør1, aktør2)
        stubHentingAvKandidater(
            requestBody = openSearchKlient.lagBodyForHentingAvCver(
                listOf(
                    kandidat1.aktørId,
                    kandidat2.aktørId
                )
            ), responsBody = esRespons
        )

        val fødselsnummer = tilfeldigFødselsnummer()
        val request = HttpRequest.newBuilder(URI(endepunkt))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(451)
    }

    @Test
    fun `Skal returnere 403 når man ikke representerer virksomheten kandidatlista tilhører`() {
        val virksomhetsnummerManRepresenterer = "987654321"
        val virksomhetsnummerTilkandidatlista = "123456789"
        val stillingId = UUID.fromString("4bd2c240-92d2-4166-ac54-ba3d21bfbc07")
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
        val nå = ZonedDateTime.now()
        stubHentingAvTilgangerFraAltinnProxy(
            wiremockServer,
            listOf(Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummerManRepresenterer))
        )
        repository.lagre(
            kandidatliste().copy(
                stillingId = stillingId,
                virksomhetsnummer = virksomhetsnummerTilkandidatlista
            )
        )

        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat1 = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = nå
        )
        val kandidat2 = Kandidat(
            aktørId = "666",
            kandidatlisteId = kandidatliste.id,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = nå
        )
        repository.lagre(kandidat1)
        repository.lagre(kandidat2)


        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI(endepunkt))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(403)

        val jsonbody = response.body()
        assertThat(jsonbody.isEmpty())
    }

    @Test
    fun `Skal returnere tom kandidatliste for en kandidatliste som er slettet`() {
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
        val virksomhetsnummer = "123456789"
        val organisasjoner = listOf(
            Testdata.lagAltinnTilgangMedRettighetKandidater("Et Navn", virksomhetsnummer),
        )
        stubHentingAvTilgangerFraAltinnProxy(wiremockServer, organisasjoner)

        repository.lagre(
            kandidatliste().copy(
                stillingId = stillingId,
                slettet = true,
                virksomhetsnummer = virksomhetsnummer
            )
        )

        val nå = Instant.now().atZone(ZoneId.of(("Europe/Oslo")))
        val kandidatliste = repository.hentKandidatliste(stillingId)
        val kandidat1 = Kandidat(
            aktørId = "1234",
            kandidatlisteId = kandidatliste?.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = nå
        )
        val kandidat2 = Kandidat(
            aktørId = "666",
            kandidatlisteId = kandidatliste.id,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = nå
        )

        repository.lagre(kandidat1)
        repository.lagre(kandidat2)

        val aktør1 = Pair(Testdata.AktørId(kandidat1.aktørId), Testdata.Fødselsdato("1982-02-02"))
        val aktør2 = Pair(Testdata.AktørId(kandidat2.aktørId), Testdata.Fødselsdato("1983-03-03"))
        val esRespons = Testdata.flereKandidaterFraES(aktør1, aktør2)
        stubHentingAvKandidater(
            requestBody = openSearchKlient.lagBodyForHentingAvCver(
                listOf(
                    kandidat1.aktørId,
                    kandidat2.aktørId
                )
            ), responsBody = esRespons
        )

        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val request = HttpRequest.newBuilder(URI(endepunkt))
            .header("Authorization", "Bearer ${hentToken(fødselsnummer)}")
            .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)

        val jsonbody = response.body()
        val kandidatlisteMedKandidaterJson = defaultObjectMapper.readTree(jsonbody)
        val kandidatlisteJson = kandidatlisteMedKandidaterJson["kandidatliste"]
        val kandidaterJson = kandidatlisteMedKandidaterJson["kandidater"]

        assertNull(kandidatlisteJson["id"])
        assertThat(kandidatlisteJson["status"].asString()).isEqualTo(Kandidatliste.Status.ÅPEN.toString())
        assertThat(kandidatlisteJson["tittel"].asString()).isEqualTo(kandidatliste.tittel)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["sistEndret"].asString()))
            .isEqualTo(kandidatliste.sistEndret)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["opprettet"].asString()))
            .isEqualTo(kandidatliste.opprettet)
        assertThat(kandidatlisteJson["slettet"].asBoolean()).isTrue
        assertThat(UUID.fromString(kandidatlisteJson["stillingId"].asString())).isEqualTo(stillingId)
        assertThat(UUID.fromString(kandidatlisteJson["uuid"].asString())).isEqualTo(kandidatliste.uuid)
        assertThat(kandidatlisteJson["virksomhetsnummer"].asString())
            .isEqualTo(kandidatliste.virksomhetsnummer)

        assertThat(kandidaterJson).hasSize(0)
    }

    private fun assertKandidat(fraRespons: JsonNode, fraDatabasen: Kandidat) {
        assertThat(fraRespons["kandidat"]).isNotEmpty
        assertNull(fraRespons["kandidat"]["id"])
        assertThat(UUID.fromString(fraRespons["kandidat"]["uuid"].asString())).isEqualTo(fraDatabasen.uuid)
        assertThat(
            fraRespons["kandidat"]["arbeidsgiversVurdering"].asString()
                .equals(fraDatabasen.arbeidsgiversVurdering.name)
        )
        assertThat(ZonedDateTime.parse(fraRespons["kandidat"]["sistEndret"].asString()) == fraDatabasen.sistEndret)
        assertThat(ZonedDateTime.parse(fraRespons["kandidat"]["opprettet"].asString()) != null)
    }

    private fun assertCv(fraRespons: JsonNode, openSearchCvRespons: JsonNode) {
        val openSearchCv = openSearchCvRespons["_source"]
        assertThat(fraRespons["mobiltelefonnummer"].asString()).isEqualTo(openSearchCv["mobiltelefon"].asString())
        assertThat(fraRespons["telefonnummer"].asString()).isEqualTo(openSearchCv["telefon"].asString())
        assertThat(fraRespons["epost"].asString()).isEqualTo(openSearchCv["epostadresse"].asString())
        assertThat(fraRespons["fornavn"].asString()).isEqualTo(openSearchCv["fornavn"].asString())
        assertThat(fraRespons["etternavn"].asString()).isEqualTo(openSearchCv["etternavn"].asString())
        assertThat(fraRespons["sammendrag"].asString()).isEqualTo(openSearchCv["beskrivelse"].asString())
        assertThat(fraRespons["bosted"].asString()).isEqualTo(openSearchCv["poststed"].asString())
    }

    private fun stubHentingAvKandidater(requestBody: String, responsBody: String) {
        wiremockServer.stubFor(
            WireMock.post("/kandidater/_search")
                .withBasicAuth("gunnar", "xyz")
                .withRequestBody(WireMock.containing(requestBody))
                .willReturn(WireMock.ok(responsBody))
        )
    }
}
