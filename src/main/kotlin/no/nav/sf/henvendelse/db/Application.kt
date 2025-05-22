package no.nav.sf.henvendelse.db

import mu.KotlinLogging
import no.nav.sf.henvendelse.api.proxy.token.DefaultTokenValidator
import no.nav.sf.henvendelse.db.database.PostgresDatabase
import no.nav.sf.henvendelse.db.handler.GuiHandler
import no.nav.sf.henvendelse.db.handler.HenvendelseHandler
import no.nav.sf.henvendelse.db.token.TokenValidator
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.PathMethod
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.ApacheServer
import org.http4k.server.Http4kServer
import org.http4k.server.asServer

class Application(
    private val tokenValidator: TokenValidator = DefaultTokenValidator(),
    private val database: PostgresDatabase = PostgresDatabase(),
    private val gui: GuiHandler = GuiHandler(database, gson, tokenValidator),
    private val henvendelse: HenvendelseHandler = HenvendelseHandler(database, tokenValidator, gson)
) {
    private val log = KotlinLogging.logger { }

    fun start() {
        log.info { "Starting..." }
        apiServer(8080).start()
        // database.create(false)
        // database.createCache()
        // database.createKjedeToAktorCache()
    }

    fun apiServer(port: Int): Http4kServer = api().asServer(ApacheServer(port))

    fun api(): HttpHandler = routes(
        "/internal/isAlive" bind Method.GET to { Response(Status.OK) },
        "/internal/isReady" bind Method.GET to isReadyHttpHandler,
        "/internal/metrics" bind Method.GET to Metrics.metricsHandler,
        "/internal/swagger" bind static(ResourceLoader.Classpath("/swagger")),
        "/internal/gui" bind static(ResourceLoader.Classpath("/gui")),
        "/internal/view" authbind Method.GET to gui.viewHandler,
        "/henvendelse" authbind Method.POST to henvendelse.upsertHenvendelseHandler,
        "/henvendelser" authbind Method.PUT to henvendelse.batchUpsertHenvendelserHandler,
        "/henvendelse" authbind Method.GET to henvendelse.fetchHenvendelseByKjedeIdHandler,
        "/henvendelser" authbind Method.GET to henvendelse.fetchHenvendelserByAktorIdHandler,
        "/cache/henvendelseliste" authbind Method.POST to henvendelse.cacheHenvendelselistePost,
        "/cache/henvendelseliste" authbind Method.GET to henvendelse.cacheHenvendelselisteGet,
        "/cache/henvendelseliste" authbind Method.DELETE to henvendelse.cacheHenvendelselisteDelete,
        "/internal/cache/count" bind Method.GET to henvendelse.cachePostgresCount,
        "/internal/cache/clear" bind Method.GET to henvendelse.cachePostgresClear,
        "/internal/cache/probe" bind Method.GET to henvendelse.cacheProbe
    )

    /**
     * authbind: a variant of bind that takes care of authentication with use of tokenValidator
     */
    infix fun String.authbind(method: Method) = AuthRouteBuilder(this, method, tokenValidator)

    data class AuthRouteBuilder(
        val path: String,
        val method: Method,
        private val tokenValidator: TokenValidator
    ) {
        infix fun to(action: HttpHandler): RoutingHttpHandler =
            PathMethod(path, method) to { request ->
                Metrics.apiCalls.labels(path).inc()
                val token = tokenValidator.firstValidToken(request)
                if (token.isPresent) {
                    action(request)
                } else {
                    Response(Status.UNAUTHORIZED)
                }
            }
    }

    private val isReadyHttpHandler: HttpHandler = {
        if (database.cacheReady()) {
            Response(Status.OK)
        } else {
            Response(Status.SERVICE_UNAVAILABLE)
        }
    }
}
