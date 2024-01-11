package no.nav.sf.henvendelse.db

import io.prometheus.client.exporter.common.TextFormat
import mu.KotlinLogging
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.ApacheServer
import org.http4k.server.Http4kServer
import org.http4k.server.asServer
import java.io.StringWriter

const val NAIS_DEFAULT_PORT = 8080
const val NAIS_ISALIVE = "/internal/isAlive"
const val NAIS_ISREADY = "/internal/isReady"
const val NAIS_METRICS = "/internal/metrics"

object Application {
    private val log = KotlinLogging.logger { }

    fun start() {
        log.info { "Starting" }
        apiServer(NAIS_DEFAULT_PORT).start()
        // postgresDatabase.create()
        val resultUpsert = postgresDatabase.upsertHenvendelse("test3b", "aktorid3", """{ "id" : "test3b", "data" : "3b" }""")
        log.info { "Result success 3 w 3b $resultUpsert" }
        postgresDatabase.henteHenvendelse("test3b")
        postgresDatabase.henteHenvendelserByAktorid("aktorid")
        postgresDatabase.henteHenvendelse("notthere")
    }

    fun apiServer(port: Int): Http4kServer = api().asServer(ApacheServer(port))

    fun api(): HttpHandler = routes(
        NAIS_ISALIVE bind Method.GET to { Response(Status.OK) },
        NAIS_ISREADY bind Method.GET to { Response(Status.OK) },
        NAIS_METRICS bind Method.GET to {
            runCatching {
                StringWriter().let { str ->
                    TextFormat.write004(str, Metrics.cRegistry.metricFamilySamples())
                    str
                }.toString()
            }
                .onFailure {
                    log.error { "/prometheus failed writing metrics  - ${it.localizedMessage}" }
                }
                .getOrDefault("").let {
                    if (it.isNotEmpty()) Response(Status.OK).body(it) else Response(Status.NO_CONTENT)
                }
        },
        "/hello" bind Method.GET to { Response(Status.OK).body("hi") }
    )
}
