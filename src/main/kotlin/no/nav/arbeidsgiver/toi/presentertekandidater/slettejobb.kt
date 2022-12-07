package no.nav.arbeidsgiver.toi.presentertekandidater

import java.util.Timer
import java.util.TimerTask

private const val antallMillisekunderIMinutt = 60000L
private const val tidTilFørsteKjøring = antallMillisekunderIMinutt
private const val tidMellomHverKjøring = antallMillisekunderIMinutt * 60

fun settOppPeriodiskSlettingAvKandidaterOgKandidatlister(repository: Repository) {
    val jobb = object : TimerTask() {
        override fun run() {
            slettKandidater(repository)
            slettKandidatlister(repository)
        }
    }

    Timer().scheduleAtFixedRate(jobb, tidTilFørsteKjøring, tidMellomHverKjøring)
}

private fun slettKandidater(repository: Repository) {
    // Slett kandidater som er sistEndret for over 6mnd siden.
}

private fun slettKandidatlister(repository: Repository) {
    // Slett kandidatlister uten kandidater og som er sistEndret for over 6mnd siden
}
