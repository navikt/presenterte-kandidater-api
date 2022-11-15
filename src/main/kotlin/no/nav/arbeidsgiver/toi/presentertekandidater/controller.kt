package no.nav.arbeidsgiver.toi.presentertekandidater

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.http.Context
import no.nav.arbeidsgiver.toi.presentertekandidater.sikkerhet.Rolle
import java.util.*

fun startKandidatlisteController(javalin: Javalin, repository: Repository, opensearchKlient: OpenSearchKlient) {
    javalin.routes {
        get("/kandidater", hentKandidater(/*repository::hentKandidater*/), Rolle.ARBEIDSGIVER)
        get(
            "/kandidatliste/{stillingId}",
            hentKandidatlisteMedKandidater(repository, opensearchKlient),
            Rolle.ARBEIDSGIVER
        )
        get("/kandidatlister/{virksomhetsnummer}", hentKandidatlister(repository), Rolle.ARBEIDSGIVER)
    }
}

private val hentKandidatlister: (repository: Repository) -> (Context) -> Unit = { repository ->
    { context ->
        val virksomhetsnummer = context.queryParam("virksomhetsnummer")
        if (virksomhetsnummer.isNullOrBlank()) {
            context.status(400)
        } else {
            val lister = repository.hentKandidatlisterMedAntall(virksomhetsnummer)
            context.json(lister).status(200)
        }
    }
}

private val hentKandidatlisteMedKandidater: (repository: Repository, opensearchKlient: OpenSearchKlient) -> (Context) -> Unit =
    { repository, opensearchKlient ->
    { context ->
        val stillingId = context.pathParam("stillingId")
        if (stillingId.isNullOrBlank()) {
            context.status(400)
        } else {
            val liste = repository.hentKandidatlisteMedKandidater(UUID.fromString(stillingId))
            val aktørider = liste?.kandidater?.map { it.aktørId }
            if(aktørider != null) {
                opensearchKlient.hentKandidater(aktørider)
            }
            if (liste == null) {
                context.status(404)
            } else {
                context.json(liste).status(200)
            }
        }
    }
}

private val hentKandidater: () -> (Context) -> Unit = {
    { context ->
        context.json("heisann").status(200)
    }
}
