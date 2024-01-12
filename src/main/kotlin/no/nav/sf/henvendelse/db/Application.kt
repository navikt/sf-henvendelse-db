package no.nav.sf.henvendelse.db

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.prometheus.client.exporter.common.TextFormat
import mu.KotlinLogging
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.ApacheServer
import org.http4k.server.Http4kServer
import org.http4k.server.asServer
import java.io.StringWriter
import java.time.LocalDateTime

const val NAIS_DEFAULT_PORT = 8080
const val NAIS_ISALIVE = "/internal/isAlive"
const val NAIS_ISREADY = "/internal/isReady"
const val NAIS_METRICS = "/internal/metrics"

object Application {
    private val log = KotlinLogging.logger { }

    val gson = GsonBuilder().registerTypeAdapter(
        LocalDateTime::class.java,
        LocalDateTimeTypeAdapter()
    ).create()

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
        "/hello" bind Method.GET to { Response(Status.OK).body("hi") },
        "/internal/swagger" bind static(ResourceLoader.Classpath("/static")),
        "/henvendelse" bind Method.POST to {
            val jsonObj = JsonParser.parseString(it.bodyString()) as JsonObject
            val aktorid = jsonObj["aktorId"].asString
            val id = jsonObj["id"].asString
            val json = it.bodyString()
            // val henvendelse = gson.fromJson(it.bodyString(), Henvendelse::class.java)
            // postgresDatabase.upsertHenvendelse()
            // Response(Status.OK).body("Parsed aktorid: $aktorid, id: $id, json $json")
            val result = postgresDatabase.upsertHenvendelse(id, aktorid, json)
            Response(Status.OK).body(gson.toJson(result))
        },
        "/henvendelser" bind Method.PUT to {
            val jsonArray = JsonParser.parseString(it.bodyString()).asJsonArray
            log.info { "Batch henvendelser called with ${jsonArray.size()} items" }
            val updatedIds: MutableList<String> = mutableListOf()
            jsonArray.forEach { e ->
                val jsonObj = e as JsonObject
                val aktorid = jsonObj["aktorId"].asString
                val id = jsonObj["id"].asString
                val json = jsonObj.toString()
                val result = postgresDatabase.upsertHenvendelse(id, aktorid, json)
                result?.let { updatedIds.add(result.id) }
            }
            log.info { "Upserted ${updatedIds.size} items" }
            Response(Status.OK).body("Upserted ${updatedIds.size} items")
        },
        "/henvendelse" bind Method.GET to {
            val id = it.query("id")!!
            val result = postgresDatabase.henteHenvendelse(id)
            Response(Status.OK).body(gson.toJson(result))
        },
        "/henvendelser" bind Method.GET to {
            val aktorid = it.query("aktorid")!!
            val result = postgresDatabase.henteHenvendelserByAktorid(aktorid)
            Response(Status.OK).body(gson.toJson(result))
        },
        "/view" bind Method.GET to {
            val page = it.query("page")!!.toLong()
            val pageSize = 2
            val count = postgresDatabase.count()
            val result = postgresDatabase.view(page, pageSize)
            Response(Status.OK).body("Page $page of ${pageCount(pageSize, count)} (size $pageSize)\n\n" + gson.toJson(result))
        }
    )

    private fun pageCount(pageSize: Int, count: Long): Long {
        return (count + pageSize - 1) / pageSize
    }
}
