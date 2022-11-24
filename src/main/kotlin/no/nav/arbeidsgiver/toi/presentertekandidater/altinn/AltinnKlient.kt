package no.nav.arbeidsgiver.toi.presentertekandidater.altinn

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.ProxyConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.SelvbetjeningToken
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.arbeidsgiver.toi.presentertekandidater.variable


class AltinnKlient(
    envs: Map<String, String>,
    private val hentExchangeToken: (fnr: String) -> String
) {
    private val consumerId = envs.variable("NAIS_APPLICATION_NAME")
    private val altinnProxyUrl = envs.variable("ALTINN_PROXY_URL")
    private val scope = envs.variable("ALTINN_PROXY_AUDIENCE")

    val config = AltinnrettigheterProxyKlientConfig(ProxyConfig(consumerId, altinnProxyUrl))
    val klient = AltinnrettigheterProxyKlient(config)

    fun hentOrganisasjoner(fnr: String) =
        klient.hentOrganisasjoner(
            SelvbetjeningToken(hentExchangeToken(fnr)),
            Subject(fnr),
            true
        )


}