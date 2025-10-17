package no.nav.arbeidsgiver.toi.presentertekandidater.altinn;

class AltinnException : RuntimeException {
    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)
}
