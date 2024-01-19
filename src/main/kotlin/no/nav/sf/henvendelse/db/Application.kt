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

class Application(
    val tokenValidator: TokenValidator = DefaultTokenValidator(),
    val database: PostgresDatabase = PostgresDatabase(),
    val gui: GuiHandler = GuiHandler(database, gson)
) {
    private val log = KotlinLogging.logger { }

    fun start() {
        log.info { "Starting" }
        apiServer(8080).start()
        // database.create()
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
        "/henvendelse" authbind Method.GET to fetchHenvendelseByIdHandler,
        "/henvendelser" authbind Method.GET to fetchHenvendelserByAktorIdHandler
    )

    val upsertHenvendelseHandler: HttpHandler = {
        try {
            val jsonObj = JsonParser.parseString(it.bodyString()) as JsonObject
            val id = jsonObj["id"]?.asString
            val aktorid = jsonObj["aktorId"]?.asString
            if (id == null) {
                Response(Status.BAD_REQUEST).body("Missing field id in json")
            } else if (aktorid == null) {
                Response(Status.BAD_REQUEST).body("Missing field aktorId in json")
            } else {
                val result = database.upsertHenvendelse(
                    id = id,
                    aktorid = aktorid,
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
        try {
            val jsonArray = JsonParser.parseString(it.bodyString()).asJsonArray
            log.info { "Batch PUT henvendelser called with ${jsonArray.size()} items" }
            val updatedIds: MutableList<String> = mutableListOf()
            if (jsonArray.any { e -> (e as JsonObject)["id"] == null }) {
                Response(Status.BAD_REQUEST).body("At least one item is missing field id in json")
            } else if (jsonArray.any { e -> (e as JsonObject)["aktorId"] == null }) {
                Response(Status.BAD_REQUEST).body("At least one item is missing field aktorId in json")
            } else {
                jsonArray.forEach { e ->
                    val jsonObj = e as JsonObject
                    val result = database.upsertHenvendelse(
                        id = jsonObj["id"].asString,
                        aktorid = jsonObj["aktorId"].asString,
                        json = jsonObj.toString(),
                        updateBySF = it.hasTokenFromSalesforce()
                    )
                    result?.let { updatedIds.add(result.id) }
                }
                log.info { "Upserted ${updatedIds.size} items" }
                Response(Status.OK).body("Upserted ${updatedIds.size} items")
            }
        } catch (_: JsonParseException) {
            Response(Status.BAD_REQUEST).body("Failed to parse request body as json array")
        }
    }

    val fetchHenvendelseByIdHandler: HttpHandler = {
        val id = it.query("id")
        if (id == null) {
            Response(Status.BAD_REQUEST).body("Missing parameter id")
        } else {
            val result = database.henteHenvendelse(id)
            Response(Status.OK).body(gson.toJson(result))
        }
    }

    val fetchHenvendelserByAktorIdHandler: HttpHandler = {
        val aktorid = it.query("aktorid")
        if (aktorid == null) {
            Response(Status.BAD_REQUEST).body("Missing parameter aktorid")
        } else {
            val result = database.henteHenvendelserByAktorid(aktorid)
            Response(Status.OK).body(gson.toJson(result))
        }
    }
}
