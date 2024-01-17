package no.nav.sf.henvendelse.db

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.prometheus.client.exporter.common.TextFormat
import mu.KotlinLogging
import no.nav.sf.henvendelse.api.proxy.token.DefaultTokenValidator
import no.nav.sf.henvendelse.api.proxy.token.TokenValidator
import no.nav.sf.henvendelse.api.proxy.token.isFromSalesforce
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
import java.io.File
import java.io.StringWriter
import java.lang.Exception
import java.time.LocalDateTime

const val NAIS_DEFAULT_PORT = 8080
const val NAIS_ISALIVE = "/internal/isAlive"
const val NAIS_ISREADY = "/internal/isReady"
const val NAIS_METRICS = "/internal/metrics"

class Application(val tokenValidator: TokenValidator = DefaultTokenValidator()) {
    private val log = KotlinLogging.logger { }

    private val gson = GsonBuilder().registerTypeAdapter(
        LocalDateTime::class.java,
        LocalDateTimeTypeAdapter()
    ).create()

    private val viewPageSize = System.getenv("VIEW_PAGE_SIZE").toInt()

    fun start() {
        log.info { "Starting" }
        apiServer(NAIS_DEFAULT_PORT).start()
        // postgresDatabase.create()
//        val resultUpsert = postgresDatabase.upsertHenvendelse("test3b", "aktorid3", """{ "id" : "test3b", "data" : "3b" }""")
//        log.info { "Result 3 w 3b (again) $resultUpsert" }
//        postgresDatabase.henteAlle()
//        postgresDatabase.henteHenvendelse("test3b")
//        postgresDatabase.henteHenvendelserByAktorid("aktorid3")
//        postgresDatabase.henteHenvendelse("notthere")
    }

    /**
     * authbind: a DSL extention of bind that takes care of authentication with use of tokenValidator
     */
    infix fun String.authbind(method: Method): AuthRouteBuilder {
        return AuthRouteBuilder(this, method, tokenValidator)
    }

    data class AuthRouteBuilder(val path: String, val method: Method, private val tokenValidator: TokenValidator) {
        infix fun to(action: HttpHandler): RoutingHttpHandler {
            return PathMethod(path, method) to { request ->
                val token = tokenValidator.firstValidToken(request)
                if (token.isPresent) {
                    action(request)
                } else {
                    Response(Status.UNAUTHORIZED)
                }
            }
        }
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
        "/internal/swagger" bind static(ResourceLoader.Classpath("/swagger")),
        "/internal/gui" bind static(ResourceLoader.Classpath("/gui")),
        "/tryit" authbind Method.GET to {
            Response(Status.OK).body(tokenValidator.firstValidToken(it).get().isFromSalesforce().toString() + " azp_name " + tokenValidator.firstValidToken(it).get().jwtTokenClaims["azp_name"])
        },
        "/henvendelse" bind Method.POST to {
            val firstValidToken = tokenValidator.firstValidToken(it)
            if (firstValidToken.isPresent) {
                try {
                    val jsonObj = JsonParser.parseString(it.bodyString()) as JsonObject
                    val id = jsonObj["id"]?.asString
                    val aktorid = jsonObj["aktorId"]?.asString
                    val json = it.bodyString()
                    if (id == null) {
                        Response(Status.BAD_REQUEST).body("Missing field id in json")
                    } else if (aktorid == null) {
                        Response(Status.BAD_REQUEST).body("Missing field aktorId in json")
                    } else {
                        val result = postgresDatabase.upsertHenvendelse(id, aktorid, json, firstValidToken.get().isFromSalesforce())
                        Response(Status.OK).body(gson.toJson(result))
                    }
                } catch (_: Exception) {
                    Response(Status.BAD_REQUEST).body("Failed to parse request body as json object")
                }
            } else {
                Response(Status.UNAUTHORIZED)
            }
        },
        "/henvendelser" bind Method.PUT to {
            val firstValidToken = tokenValidator.firstValidToken(it)
            if (firstValidToken.isPresent) {
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
                            val json = jsonObj.toString()
                            val result = postgresDatabase.upsertHenvendelse(
                                jsonObj["id"].asString,
                                jsonObj["aktorId"].asString,
                                json,
                                firstValidToken.get().isFromSalesforce()
                            )
                            result?.let { updatedIds.add(result.id) }
                        }
                        log.info { "Upserted ${updatedIds.size} items" }
                        Response(Status.OK).body("Upserted ${updatedIds.size} items")
                    }
                } catch (_: Exception) {
                    Response(Status.BAD_REQUEST).body("Failed to parse request body as json array")
                }
            } else {
                Response(Status.UNAUTHORIZED)
            }
        },
        "/henvendelse" bind Method.GET to {
            if (tokenValidator.firstValidToken(it).isPresent) {
                val id = it.query("id")
                if (id == null) {
                    Response(Status.BAD_REQUEST).body("Missing parameter id")
                } else {
                    val result = postgresDatabase.henteHenvendelse(id)
                    Response(Status.OK).body(gson.toJson(result))
                }
            } else {
                Response(Status.UNAUTHORIZED)
            }
        },
        "/henvendelser" bind Method.GET to {
            if (tokenValidator.firstValidToken(it).isPresent) {
                val aktorid = it.query("aktorid")
                if (aktorid == null) {
                    Response(Status.BAD_REQUEST).body("Missing parameter aktorid")
                } else {
                    val result = postgresDatabase.henteHenvendelserByAktorid(aktorid)
                    Response(Status.OK).body(gson.toJson(result))
                }
            } else {
                Response(Status.UNAUTHORIZED)
            }
        },
        "/internal/view" bind Method.GET to {
            if (tokenValidator.firstValidToken(it).isPresent) {
                val page = it.query("page")!!.toLong()
                val count = postgresDatabase.count()
                val result = postgresDatabase.view(page, viewPageSize)
                val viewData = ViewData(page, pageCount(count), viewPageSize, count, result)
                Response(Status.OK).body(gson.toJson(viewData))
            } else {
                Response(Status.UNAUTHORIZED)
            }
        }
    )

    private data class ViewData(val page: Long, val pageCount: Long, val pageSize: Int, val count: Long, val records: List<HenvendelseRecord>)

    private fun pageCount(count: Long): Long {
        return (count + viewPageSize - 1) / viewPageSize
    }
}
