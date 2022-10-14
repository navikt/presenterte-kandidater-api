package no.nav.arbeidsgiver.toi.presentertekandidater

import no.nav.helse.rapids_rivers.RapidApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import no.nav.arbeidsgiver.toi.Rolle
import no.nav.arbeidsgiver.toi.hentIssuerProperties
import no.nav.arbeidsgiver.toi.styrTilgang
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.security.token.support.core.configuration.IssuerProperties

fun startApp(
    javalin: Javalin,
    rapidsConnection: RapidsConnection,
    rapidIsAlive: () -> Boolean
) {
    javalin.routes {
        get("/isalive", isAlive(rapidIsAlive), Rolle.UNPROTECTED)
        get("/kandidater", hentKandidater(/*repository::hentKandidater*/), Rolle.ARBEIDSGIVER)
    }

    rapidsConnection.also {
        PresenterteKandidaterLytter(it)
    }.start()
}

val hentKandidater: () -> (Context) -> Unit = {
    { context ->
        context.json("heisann").status(200)
    }
}

private val isAlive: (() -> Boolean) -> (Context) -> Unit = { isAlive ->
    { context ->
        context.status(if (isAlive()) 200 else 500)
    }
}

fun opprettJavalinMedTilgangskontroll(
    issuerProperties: Map<Rolle, IssuerProperties>
): Javalin =
    Javalin.create {
        it.defaultContentType = "application/json"
        it.accessManager(styrTilgang(issuerProperties))
    }.start(9000)

fun main() {
    val env = System.getenv()
    //val datasource = DatabaseKonfigurasjon(env).lagDatasource()
    //val repository = Repository(datasource)
    val issuerProperties = hentIssuerProperties(System.getenv())
    val javalin = opprettJavalinMedTilgangskontroll(issuerProperties)

    lateinit var rapidIsAlive: () -> Boolean
    val rapidsConnection = RapidApplication.create(env, configure = { _, kafkarapid ->
        rapidIsAlive = kafkarapid::isRunning
    }).apply {
        this.register(object : RapidsConnection.StatusListener {
            override fun onStartup(rapidsConnection: RapidsConnection) {
                //repository.kjørFlywayMigreringer()
            }
        })
    }

    startApp(javalin, rapidsConnection, rapidIsAlive)
}

val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)

