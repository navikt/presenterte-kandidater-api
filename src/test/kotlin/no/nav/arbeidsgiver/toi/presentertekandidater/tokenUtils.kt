package no.nav.arbeidsgiver.toi.presentertekandidater

import no.nav.security.mock.oauth2.MockOAuth2Server

fun hentToken(mockOAuth2Server: MockOAuth2Server, fødselsnummer: String = "01838699827"): String {
    return mockOAuth2Server.issueToken(claims = mapOf("pid" to fødselsnummer)).serialize()
}

fun hentUgyldigToken(mockOAuth2Server: MockOAuth2Server): String {
    return mockOAuth2Server.issueToken(issuerId = "feilissuer").serialize()
}
