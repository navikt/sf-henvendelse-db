package no.nav.sf.henvendelse.db

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import mu.KotlinLogging
import no.nav.sf.henvendelse.api.proxy.token.DefaultTokenValidator
import no.nav.sf.henvendelse.api.proxy.token.TokenValidator
import no.nav.sf.henvendelse.api.proxy.token.isFromSalesforce
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
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
import java.io.File

class Application(
    val tokenValidator: TokenValidator = DefaultTokenValidator(),
    val database: PostgresDatabase = PostgresDatabase(),
    val gui: GuiHandler = GuiHandler(database, gson)
) {
    private val log = KotlinLogging.logger { }

    fun start() {
        log.info { "Starting" }
        apiServer(8080).start()
        // database.create(false)
    }

    /**
     * authbind: an extention of bind that takes care of authentication with use of tokenValidator
     */
    infix fun String.authbind(method: Method) = AuthRouteBuilder(this, method, tokenValidator)

    data class AuthRouteBuilder(val path: String, val method: Method, private val tokenValidator: TokenValidator) {
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

    fun Request.hasTokenFromSalesforce() = tokenValidator.firstValidToken(this).get().isFromSalesforce()

    fun apiServer(port: Int): Http4kServer = api().asServer(ApacheServer(port))

    fun api(): HttpHandler = routes(
        "/internal/isAlive" bind Method.GET to { Response(Status.OK) },
        "/internal/isReady" bind Method.GET to { Response(Status.OK) },
        "/internal/metrics" bind Method.GET to Metrics.metricsHandler,
        "/internal/swagger" bind static(ResourceLoader.Classpath("/swagger")),
        "/internal/gui" bind static(ResourceLoader.Classpath("/gui")),
        "/internal/view" authbind Method.GET to gui.viewHandler,
        "/henvendelse" authbind Method.POST to upsertHenvendelseHandler,
        "/henvendelser" authbind Method.PUT to batchUpsertHenvendelserHandler,
        "/henvendelse" authbind Method.GET to fetchHenvendelseByKjedeIdHandler,
        "/henvendelser" authbind Method.GET to fetchHenvendelserByAktorIdHandler
    )

    val upsertHenvendelseHandler: HttpHandler = {
        File("/tmp/latestRequestUpsertHenvendlse").writeText(it.toMessage())
        try {
            val jsonObj = JsonParser.parseString(it.bodyString()) as JsonObject
            val kjedeId = jsonObj["kjedeId"]?.asString
            val aktorId = jsonObj["aktorId"]?.asString
            if (kjedeId == null) {
                File("/tmp/latestBadRequestK").writeText(it.toMessage())
                Response(Status.BAD_REQUEST).body("Missing field kjedeId in json")
            } else if (aktorId == null) {
                File("/tmp/latestBadRequestA").writeText(it.toMessage())
                Response(Status.BAD_REQUEST).body("Missing field aktorId in json")
            } else {
                val result = database.upsertHenvendelse(
                    kjedeId = kjedeId,
                    aktorId = aktorId,
                    json = it.bodyString(),
                    updateBySF = it.hasTokenFromSalesforce()
                )
                Response(Status.OK).body(gson.toJson(result))
            }
        } catch (_: JsonParseException) {
            Response(Status.BAD_REQUEST).body("Failed to parse request body as json object")
        }
    }

    val batchUpsertHenvendelserHandler: HttpHandler = {
        File("/tmp/latestRequestBatchUpsertHenvendlser").writeText(it.toMessage())
        try {
            val jsonArray = JsonParser.parseString(it.bodyString()).asJsonArray
            log.info { "Batch PUT henvendelser called with ${jsonArray.size()} items" }
            val updatedKjedeIds: MutableList<String> = mutableListOf()
            if (jsonArray.any { e -> (e as JsonObject)["kjedeId"] == null }) {
                File("/tmp/latestBadRequestKs").writeText(it.toMessage())
                Response(Status.BAD_REQUEST).body("At least one item is missing field kjedeId in json")
            } else if (jsonArray.any { e -> (e as JsonObject)["aktorId"] == null }) {
                File("/tmp/latestBadRequestAs").writeText(it.toMessage())
                Response(Status.BAD_REQUEST).body("At least one item is missing field aktorId in json")
            } else {
                jsonArray.forEach { e ->
                    val jsonObj = e as JsonObject
                    val result = database.upsertHenvendelse(
                        kjedeId = jsonObj["kjedeId"].asString,
                        aktorId = jsonObj["aktorId"].asString,
                        json = jsonObj.toString(),
                        updateBySF = it.hasTokenFromSalesforce()
                    )
                    result?.let { updatedKjedeIds.add(result.kjedeId) }
                }
                log.info { "Upserted ${updatedKjedeIds.size} items" }
                Response(Status.OK).body("Upserted ${updatedKjedeIds.size} items")
            }
        } catch (_: JsonParseException) {
            Response(Status.BAD_REQUEST).body("Failed to parse request body as json array")
        }
    }

    val fetchHenvendelseByKjedeIdHandler: HttpHandler = {
        File("/tmp/latestRequestFetchHenvendlseByKjedeId").writeText(it.toMessage())
        val kjedeId = it.query("kjedeId")
        if (kjedeId == null) {
            Response(Status.BAD_REQUEST).body("Missing parameter kjedeId")
        } else {
            val result = database.henteHenvendelse(kjedeId)
            Response(Status.OK).body(gson.toJson(result))
        }
    }

    val fetchHenvendelserByAktorIdHandler: HttpHandler = {
        File("/tmp/latestRequestFetchHenvendlserByAktorId").writeText(it.toMessage())
        val aktorId = it.query("aktorId")
        if (aktorId == null) {
            Response(Status.BAD_REQUEST).body("Missing parameter aktorId")
        } else {
            val result = database.henteHenvendelserByAktorId(aktorId)
            Response(Status.OK).body(gson.toJson(result))
        }
    }
}
