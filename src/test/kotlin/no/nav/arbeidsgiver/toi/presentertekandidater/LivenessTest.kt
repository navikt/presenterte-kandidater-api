package no.nav.arbeidsgiver.toi.presentertekandidater

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LivenessTest {
    private val httpClient = HttpClient.newHttpClient()

    @BeforeAll
    fun init() {
        startLocalApplication()
    }

    @Test
    fun `Applikasjonen svarer 200 OK på isalive-endepunkt`() {
        val request = HttpRequest.newBuilder(URI("http://localhost:9000/isalive")).build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)
    }
}