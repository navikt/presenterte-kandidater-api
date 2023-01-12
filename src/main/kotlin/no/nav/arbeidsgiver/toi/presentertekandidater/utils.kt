package no.nav.arbeidsgiver.toi.presentertekandidater

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Exception

val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)

fun log(name: String): Logger = LoggerFactory.getLogger(name)

val secureLog: Logger
    get() = LoggerFactory.getLogger("secureLog")

fun Map<String, String>.variable(felt: String) = this[felt] ?: error("$felt er ikke angitt")

