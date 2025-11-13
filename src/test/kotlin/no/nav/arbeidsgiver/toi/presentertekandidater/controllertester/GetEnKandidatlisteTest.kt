package no.nav.arbeidsgiver.toi.presentertekandidater.controllertester

import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.arbeidsgiver.toi.presentertekandidater.*
import no.nav.arbeidsgiver.toi.presentertekandidater.Testdata.kandidatliste
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidat
import no.nav.arbeidsgiver.toi.presentertekandidater.kandidatliste.Kandidatliste
import no.nav.security.mock.oauth2.http.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetEnKandidatlisteTest {
    private val repository = kandidatlisteRepositoryMedLokalPostgres()
    private val fuel = FuelManager()
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
        val nå = ZonedDateTime.now()
        val virksomhetsnummer = "111111111"
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer),
        )
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

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
            kandidatlisteId = kandidatliste.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = nå
        )

        repository.lagre(kandidat1)
        repository.lagre(kandidat2)

        val aktør1 = Pair(Testdata.AktørId(kandidat1.aktørId), Testdata.Fødselsdato("1982-02-02"))
        val aktør2 = Pair(Testdata.AktørId(kandidat2.aktørId), Testdata.Fødselsdato("1983-03-03"))
        val esRespons = Testdata.flereKandidaterFraES(aktør1, aktør2)
        val esResponseCver = objectMapper.readTree(esRespons)["hits"]["hits"]
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
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(200)

        val jsonbody = response.body().asString("application/json;charset=utf-8")
        val kandidatlisteMedKandidaterJson = defaultObjectMapper.readTree(jsonbody)
        val kandidatlisteJson = kandidatlisteMedKandidaterJson["kandidatliste"]
        val kandidaterJson = kandidatlisteMedKandidaterJson["kandidater"]

        assertNull(kandidatlisteJson["id"])
        assertThat(kandidatlisteJson["status"].textValue()).isEqualTo(Kandidatliste.Status.ÅPEN.toString())
        assertThat(kandidatlisteJson["tittel"].textValue()).isEqualTo(kandidatliste.tittel)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["sistEndret"].textValue()))
            .isEqualTo(kandidatliste.sistEndret)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["opprettet"].textValue()))
            .isEqualTo(kandidatliste.opprettet)
        assertThat(kandidatlisteJson["slettet"].asBoolean()).isFalse
        assertThat(UUID.fromString(kandidatlisteJson["stillingId"].textValue())).isEqualTo(stillingId)
        assertThat(UUID.fromString(kandidatlisteJson["uuid"].textValue())).isEqualTo(kandidatliste.uuid)
        assertThat(kandidatlisteJson["virksomhetsnummer"].textValue())
            .isEqualTo(kandidatliste.virksomhetsnummer)

        assertThat(kandidaterJson).hasSize(2)
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
            Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer),
        )
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

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
            kandidatlisteId = kandidatliste.id!!,
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
        val (_, response, _) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(451)
    }

    @Test
    fun `Skal returnere 403 når man ikke representerer virksomheten kandidatlista tilhører`() {
        val virksomhetsnummerManRepresenterer = "987654321"
        val virksomhetsnummerTilkandidatlista = "123456789"
        val stillingId = UUID.fromString("4bd2c240-92d2-4166-ac54-ba3d21bfbc07")
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
        val nå = ZonedDateTime.now()
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(
            wiremockServer,
            listOf(Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummerManRepresenterer))
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
            kandidatlisteId = kandidatliste.id!!,
            uuid = UUID.randomUUID(),
            arbeidsgiversVurdering = Kandidat.ArbeidsgiversVurdering.TIL_VURDERING,
            sistEndret = nå
        )
        repository.lagre(kandidat1)
        repository.lagre(kandidat2)


        val fødselsnummer = tilfeldigFødselsnummer()
        lagreSamtykke(fødselsnummer)
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(403)

        val jsonbody = response.body().asString("application/json;charset=utf-8")
        assertThat(jsonbody.isEmpty())
    }

    @Test
    fun `Skal returnere tom kandidatliste for en kandidatliste som er slettet`() {
        val stillingId = UUID.randomUUID()
        val endepunkt = "http://localhost:9000/kandidatliste/$stillingId"
        val virksomhetsnummer = "123456789"
        val organisasjoner = listOf(
            Testdata.lagAltinnOrganisasjon("Et Navn", virksomhetsnummer),
        )
        stubHentingAvOrganisasjonerFraAltinnProxyFiltrertPåRekruttering(wiremockServer, organisasjoner)

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
            kandidatlisteId = kandidatliste.id!!,
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
        val (_, response) = fuel
            .get(endepunkt)
            .authentication().bearer(hentToken(fødselsnummer))
            .response()

        assertThat(response.statusCode).isEqualTo(200)

        val jsonbody = response.body().asString("application/json;charset=utf-8")
        val kandidatlisteMedKandidaterJson = defaultObjectMapper.readTree(jsonbody)
        val kandidatlisteJson = kandidatlisteMedKandidaterJson["kandidatliste"]
        val kandidaterJson = kandidatlisteMedKandidaterJson["kandidater"]

        assertNull(kandidatlisteJson["id"])
        assertThat(kandidatlisteJson["status"].textValue()).isEqualTo(Kandidatliste.Status.ÅPEN.toString())
        assertThat(kandidatlisteJson["tittel"].textValue()).isEqualTo(kandidatliste.tittel)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["sistEndret"].textValue()))
            .isEqualTo(kandidatliste.sistEndret)
        assertThat(ZonedDateTime.parse(kandidatlisteJson["opprettet"].textValue()))
            .isEqualTo(kandidatliste.opprettet)
        assertThat(kandidatlisteJson["slettet"].asBoolean()).isTrue
        assertThat(UUID.fromString(kandidatlisteJson["stillingId"].textValue())).isEqualTo(stillingId)
        assertThat(UUID.fromString(kandidatlisteJson["uuid"].textValue())).isEqualTo(kandidatliste.uuid)
        assertThat(kandidatlisteJson["virksomhetsnummer"].textValue())
            .isEqualTo(kandidatliste.virksomhetsnummer)

        assertThat(kandidaterJson).hasSize(0)
    }

    private fun assertKandidat(fraRespons: JsonNode, fraDatabasen: Kandidat) {
        assertThat(fraRespons["kandidat"]).isNotEmpty
        assertNull(fraRespons["kandidat"]["id"])
        assertThat(UUID.fromString(fraRespons["kandidat"]["uuid"].textValue())).isEqualTo(fraDatabasen.uuid)
        assertThat(
            fraRespons["kandidat"]["arbeidsgiversVurdering"].textValue()
                .equals(fraDatabasen.arbeidsgiversVurdering.name)
        )
        assertThat(ZonedDateTime.parse(fraRespons["kandidat"]["sistEndret"].textValue()) == fraDatabasen.sistEndret)
    }

    private fun assertCv(fraRespons: JsonNode, openSearchCvRespons: JsonNode) {
        val openSearchCv = openSearchCvRespons["_source"]
        assertThat(fraRespons["mobiltelefonnummer"].asText()).isEqualTo(openSearchCv["mobiltelefon"].asText())
        assertThat(fraRespons["telefonnummer"].asText()).isEqualTo(openSearchCv["telefon"].asText())
        assertThat(fraRespons["epost"].asText()).isEqualTo(openSearchCv["epostadresse"].asText())
        assertThat(fraRespons["fornavn"].asText()).isEqualTo(openSearchCv["fornavn"].asText())
        assertThat(fraRespons["etternavn"].asText()).isEqualTo(openSearchCv["etternavn"].asText())
        assertThat(fraRespons["sammendrag"].asText()).isEqualTo(openSearchCv["beskrivelse"].asText())
        assertThat(fraRespons["bosted"].asText()).isEqualTo(openSearchCv["poststed"].asText())
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
