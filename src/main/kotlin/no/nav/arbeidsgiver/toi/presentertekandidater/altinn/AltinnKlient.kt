package no.nav.arbeidsgiver.toi.presentertekandidater.altinn

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.javalin.http.HttpCode
import no.nav.arbeidsgiver.toi.presentertekandidater.SecureLogLogger.Companion.secure
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.Cache.AltinnFiltrering.ENKELTRETTIGHET_REKRUTTERING
import no.nav.arbeidsgiver.toi.presentertekandidater.altinn.Cache.AltinnFiltrering.INGEN
import no.nav.arbeidsgiver.toi.presentertekandidater.log
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.TokendingsKlient
import no.nav.arbeidsgiver.toi.presentertekandidater.variable
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration


class AltinnKlient(
    envs: Map<String, String>,
    private val tokendingsKlient: TokendingsKlient,
) {
    companion object {
        val mapper = jacksonObjectMapper()
        val httpKlient: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    }

    private val altinnProxyUrl = envs.variable("ALTINN_PROXY_URL")
    private val scope = envs.variable("ALTINN_PROXY_AUDIENCE")
    private val altinn2Tilganger = listOf("5078:1") // Rekrutteringsrettighet Altinn2. TODO: Erstattes av altinn3-rettighet
    private val altinn3Tilganger = emptyList<String>() // TODO: Legg til Altinn3-rettighet når den er klar
    private val cacheLevetid = Duration.ofMinutes(15)
    private val cache = Cache(levetid = cacheLevetid)

    fun hentOrganisasjonerMedRettighetRekrutteringFraAltinn(fnr: String, accessToken: String): List<AltinnReportee> {
        log.info("Skal hente organisasjoner hvor innlogget person har rekrutteringsrettighet")

        val cachetOrganisasjoner = cache.hentFraCache(fnr, ENKELTRETTIGHET_REKRUTTERING)
        if (cachetOrganisasjoner != null) return cachetOrganisasjoner

        val exchangeToken = tokendingsKlient.veksleInnToken(accessToken, scope)

        val altinnTilganger = hentAltinnTilganger(exchangeToken, altinn2Tilganger, altinn3Tilganger)
        val organisasjoner = mapAltinnTilgangTilAltinnReportee(altinnTilganger, kunUnderenheter = true)

        return organisasjoner.also {
            if (it.isEmpty()) {
                log.info("Innlogget person representerer ingen organisasjoner")
            } else {
                log.info("Innlogget person representerer ${it.size} organisasjoner")
                cache.leggICache(fnr, it, ENKELTRETTIGHET_REKRUTTERING)
            }
        }
    }

    fun hentOrganisasjoner(fnr: String, accessToken: String): List<AltinnReportee> {
        log.info("Skal hente alle organisasjoner hvor innlogget person har en Altinnrettighet")

        val cachetOrganisasjoner = cache.hentFraCache(fnr, INGEN)
        if (cachetOrganisasjoner != null) return cachetOrganisasjoner

        val exchangeToken = tokendingsKlient.veksleInnToken(accessToken, scope)

        val altinnTilganger = hentAltinnTilganger(exchangeToken)
        val organisasjoner = mapAltinnTilgangTilAltinnReportee(altinnTilganger, kunUnderenheter = false)

        return organisasjoner.also {
            if (it.isEmpty()) {
                log.info("Innlogget person representerer ingen organisasjoner")
            } else {
                log.info("Innlogget person representerer ${it.size} organisasjoner")
                cache.leggICache(fnr, it, INGEN)
            }
        }
    }

    private fun hentAltinnTilganger(
        token: String,
        altinn2Tilganger: List<String> = emptyList(),
        altinn3Tilganger: List<String> = emptyList()
    ): AltinnTilgangerResponse {
        val request = HttpRequest.newBuilder()
            .uri(URI(altinnProxyUrl))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    mapper.writeValueAsString(
                        AltinnTilgangRequest.opprett(altinn2Tilganger, altinn3Tilganger)
                    )
                )
            ).build()

        val response = hentAltinnTilgangerMedRetry(httpKlient, request, maksForsøk = 3)

        if (response.statusCode() != HttpCode.OK.status) {
            secure(log).warn(
                "Mottok ${response.statusCode()}-respons fra arbeidsgiver-altinn-tilganger: ${response.body()}"
            )
            throw AltinnServiceException("Klarte ikke hente Altinn-tilganger, statuskode: ${response.statusCode()}")
        }

        val altinnTilganger: AltinnTilgangerResponse = mapper.readValue(response.body())
        return altinnTilganger
    }

    /**
     * Henter Altinn-tilganger med retry-mekanisme for midlertidige 5XX-responser og nettverksfeil.
     * I tillegg gjøres det retry dersom responsen inneholder `isError=true` og ingen tilganger.
     *
     * TODO: Fjern retry-mekanisme for 200-responser når arbeidsgiver-altinn-tilganger ikke lenger gjør spørringer mot Altinn2.
     */
    private fun hentAltinnTilgangerMedRetry(
        httpKlient: HttpClient,
        request: HttpRequest,
        maksForsøk: Int
    ): HttpResponse<String> {
        var sisteException: Exception? = null
        for (forsøk in 1..maksForsøk) {
            try {
                val respons = httpKlient.send(request, HttpResponse.BodyHandlers.ofString())
                val altinnTilganger =
                    if (respons.statusCode() == 200) mapper.readValue<AltinnTilgangerResponse>(respons.body()) else null
                val altinnIsError = altinnTilganger?.isError == true && altinnTilganger.hierarki.isEmpty()

                if (respons.statusCode() in 500..504) {
                    log.warn("Mottok ${respons.statusCode()} statuskode fra Altinn, prøver igjen (forsøk $forsøk av $maksForsøk)")
                } else if (altinnIsError) {
                    log.warn("Mottok isError=true og tom tilgangsliste fra Altinn, prøver igjen (forsøk $forsøk av $maksForsøk)")
                    sisteException = AltinnTilgangException("Mottok isError=true og tom tilgangsliste")
                } else {
                    return respons.also {
                        if (forsøk > 1) {
                            log.info("Hentet Altinn-tilganger etter $forsøk forsøk")
                        }
                    }
                }
            } catch (e: InterruptedException) {
                sisteException = e
                log.error("Avbrutt ved kall til Altinn: ${e.message}", e)
                Thread.currentThread().interrupt()
                break
            } catch (e: IOException) {
                sisteException = e
                log.warn("Nettverksfeil ved kall mot Altinn: ${e.message}, prøver igjen (forsøk $forsøk av $maksForsøk)", e)
            }
            if (forsøk < maksForsøk) {
                Thread.sleep(500L) // Vent litt før nytt forsøk
            }
        }
        log.error("Klarte ikke hente Altinn-tilganger etter $maksForsøk forsøk")
        if (sisteException is AltinnException) throw sisteException
        else throw AltinnServiceException("Klarte ikke hente Altinn-tilganger", sisteException)
    }

    /**
     * Flater ut organisasjonshierarkiet fra Altinn-tilgangene til en liste av [AltinnReportee]-objekter.
     * Bevarer relasjonen mellom underenheter og overordnet enhet ved å sette parentOrganizationNumber for underenheter.
     *
     * @param altinnTilganger Responsen fra arbeidsgiver-altinn-tilganger API.
     * @param kunUnderenheter Hvis true, returneres kun underenheter (overordnede enheter ekskluderes).
     * @return En liste av AltinnReportee-objekter som representerer organisasjonene brukeren har tilgang til, filtrert basert på `kunUnderenheter`-flagget.
     */
    private fun mapAltinnTilgangTilAltinnReportee(
        altinnTilganger: AltinnTilgangerResponse,
        kunUnderenheter: Boolean
    ): List<AltinnReportee> {
        fun flatUtHierarki(tilgang: AltinnTilgang, parentOrgnr: String? = null): List<AltinnReportee> {
            val nåværendeOrganisasjon = AltinnReportee(
                name = tilgang.navn,
                parentOrganizationNumber = parentOrgnr,
                organizationNumber = tilgang.orgnr,
                organizationForm = tilgang.organisasjonsform,
            )
            val underenheter = tilgang.underenheter.flatMap { flatUtHierarki(it, tilgang.orgnr) }
            return listOf(nåværendeOrganisasjon) + underenheter
        }

        return if (kunUnderenheter) {
            altinnTilganger.hierarki.flatMap { tilgang ->
                tilgang.underenheter.flatMap { flatUtHierarki(it, tilgang.orgnr) }
            }
        } else {
            altinnTilganger.hierarki.flatMap { flatUtHierarki(it) }
        }
    }

    fun tømCache() {
        cache.tømCache()
    }
}

data class AltinnTilgangRequest(
    val filter: Filter
) {
    companion object {
        fun opprett(
            altinn2Tilganger: List<String> = listOf(),
            altinn3Tilganger: List<String> = listOf()
        ): AltinnTilgangRequest {
            val filter = Filter(
                altinn2Tilganger = altinn2Tilganger,
                altinn3Tilganger = altinn3Tilganger,
                inkluderSlettede = false
            )
            return AltinnTilgangRequest(filter)
        }
    }
}

data class Filter(
    val altinn2Tilganger: List<String>,
    val altinn3Tilganger: List<String>,
    val inkluderSlettede: Boolean
)

data class AltinnTilgang(
    val orgnr: String,
    val altinn3Tilganger: Set<String>,
    val altinn2Tilganger: Set<String>,
    val underenheter: List<AltinnTilgang>,
    val navn: String,
    val organisasjonsform: String,
    val erSlettet: Boolean
)

/**
 * Respons fra arbeidsgiver-altinn-tilganger API.
 *
 * Responsen representerer en brukers Altinn-tilganger presentert på tre forskjellige måter:
 * - `hierarki`: Organisasjonshierarkiet med tilganger.
 * - `orgNrTilTilganger`: En liste over tilganger per organisasjonsnummer.
 * - `tilgangTilOrgNr`: En liste over organisasjonsnummer per tilgang.
 */
data class AltinnTilgangerResponse(
    @JsonProperty("isError")
    val isError: Boolean,
    val hierarki: List<AltinnTilgang>,
    val orgNrTilTilganger: Map<String, Set<String>>,
    val tilgangTilOrgNr: Map<String, Set<String>>
)
