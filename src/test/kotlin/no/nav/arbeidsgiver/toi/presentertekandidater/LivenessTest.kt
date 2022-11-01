package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.kittinunf.fuel.Fuel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LivenessTest {

    @Test
    fun `Applikasjonen skal svare 200 OK på isalive-endepunkt`() {
        startLocalApplication()

        val (_, response) = Fuel.get("http://localhost:9000/isalive").response()

        assertThat(response.statusCode).isEqualTo(200)
    }
}