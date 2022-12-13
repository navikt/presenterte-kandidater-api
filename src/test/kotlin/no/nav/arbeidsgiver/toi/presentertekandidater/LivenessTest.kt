package no.nav.arbeidsgiver.toi.presentertekandidater

import com.github.kittinunf.fuel.Fuel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LivenessTest {

    @BeforeAll
    fun init() {
        startLocalApplication()
    }

    @Test
    fun `Applikasjonen svarer 200 OK på isalive-endepunkt`() {
        val (_, response) = Fuel.get("http://localhost:9000/isalive").response()

        assertThat(response.statusCode).isEqualTo(200)
    }
}