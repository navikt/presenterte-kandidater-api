package no.nav.arbeidsgiver.toi.presentertekandidater

import java.util.Timer
import java.util.TimerTask

private val antallMillisekunderIMinutt = 60000L
private val tidTilFørsteKjøring = antallMillisekunderIMinutt
private val tidMellomHverKjøring = antallMillisekunderIMinutt * 60

fun settOppPeriodiskSlettingAvKandidaterOgKandidatlister() {
    val jobb = object : TimerTask() {
        override fun run() {
            slettKandidater()
            slettKandidatlister()
        }
    }

    Timer().scheduleAtFixedRate(jobb, tidTilFørsteKjøring, tidMellomHverKjøring)
}

private fun slettKandidatlister() {

}

private fun slettKandidater() {

}


