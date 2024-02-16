package no.nav.sf.henvendelse.db.handler

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import mu.KotlinLogging
import no.nav.sf.henvendelse.db.database.PostgresDatabase
import no.nav.sf.henvendelse.db.token.TokenValidator
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status

class HenvendelseHandler(database: PostgresDatabase, tokenValidator: TokenValidator, gson: Gson) {
    private val log = KotlinLogging.logger { }

    val upsertHenvendelseHandler: HttpHandler = {
        if (it.bodyString().isBlank()) {
            Response(Status.BAD_REQUEST).body("Missing request body")
        } else {
            try {
                val jsonObj = JsonParser.parseString(it.bodyString()) as JsonObject
                val kjedeId = jsonObj["kjedeId"]?.asString
                val aktorId = jsonObj["aktorId"]?.asString
                if (kjedeId == null) {
                    Response(Status.BAD_REQUEST).body("Missing field kjedeId in json")
                } else if (aktorId == null) {
                    Response(Status.BAD_REQUEST).body("Missing field aktorId in json")
                } else {
                    val result = database.upsertHenvendelse(
                        kjedeId = kjedeId,
                        aktorId = aktorId,
                        json = it.bodyString(),
                        updateBySF = tokenValidator.hasTokenFromSalesforce(it)
                    )
                    Response(Status.OK).body(gson.toJson(result))
                }
            } catch (_: JsonParseException) {
                Response(Status.BAD_REQUEST).body("Failed to parse request body as json object")
            }
        }
    }

    val batchUpsertHenvendelserHandler: HttpHandler = {
        if (it.bodyString().isBlank()) {
            Response(Status.BAD_REQUEST).body("Missing request body")
        } else {
            try {
                val jsonArray = JsonParser.parseString(it.bodyString()).asJsonArray
                log.info { "Batch PUT henvendelser called with ${jsonArray.size()} items" }
                val updatedKjedeIds: MutableList<String> = mutableListOf()
                if (jsonArray.any { e -> (e as JsonObject)["kjedeId"] == null }) {
                    Response(Status.BAD_REQUEST).body("At least one item is missing field kjedeId in json")
                } else if (jsonArray.any { e -> (e as JsonObject)["aktorId"] == null }) {
                    Response(Status.BAD_REQUEST).body("At least one item is missing field aktorId in json")
                } else {
                    jsonArray.forEach { e ->
                        val jsonObj = e as JsonObject
                        val result = database.upsertHenvendelse(
                            kjedeId = jsonObj["kjedeId"].asString,
                            aktorId = jsonObj["aktorId"].asString,
                            json = jsonObj.toString(),
                            updateBySF = tokenValidator.hasTokenFromSalesforce(it)
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
    }

    val fetchHenvendelseByKjedeIdHandler: HttpHandler = {
        val kjedeId = it.query("kjedeId")
        if (kjedeId == null) {
            Response(Status.BAD_REQUEST).body("Missing parameter kjedeId")
        } else {
            val result = database.henteHenvendelse(kjedeId)
            Response(Status.OK).body(gson.toJson(result))
        }
    }

    val fetchHenvendelserByAktorIdHandler: HttpHandler = {
        val aktorId = it.query("aktorId")
        if (aktorId == null) {
            Response(Status.BAD_REQUEST).body("Missing parameter aktorId")
        } else {
            val result = database.henteHenvendelserByAktorId(aktorId)
            Response(Status.OK).body(gson.toJson(result))
        }
    }
}
