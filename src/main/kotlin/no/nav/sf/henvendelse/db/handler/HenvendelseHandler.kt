package no.nav.sf.henvendelse.db.handler

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.nav.sf.henvendelse.db.Metrics
import no.nav.sf.henvendelse.db.database.PostgresDatabase
import no.nav.sf.henvendelse.db.token.TokenValidator
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import java.io.File

const val KJEDE_ID = "kjedeId"
const val AKTOR_ID = "aktorId"
const val FNR = "fnr"

class HenvendelseHandler(database: PostgresDatabase, tokenValidator: TokenValidator, gson: Gson) {
    private val TTLInSecondsPostgres: Int = 60 * 60 * 96 // 96h

    private val log = KotlinLogging.logger { }

    val upsertHenvendelseHandler: HttpHandler = {
        if (it.bodyString().isBlank()) {
            Response(BAD_REQUEST).body("Missing request body")
        } else {
            try {
                val jsonArray = JsonParser.parseString(it.bodyString()) as JsonArray
                val validationResult = validateJsonArray(jsonArray)

                if (validationResult.isInvalid()) {
                    Response(BAD_REQUEST).body(validationResult.errorMessage)
                } else {
                    val result = database.upsertHenvendelse(
                        kjedeId = validationResult.kjedeId,
                        aktorId = validationResult.aktorId,
                        fnr = validationResult.fnr,
                        json = jsonArray.toString(),
                        updateBySF = tokenValidator.hasTokenFromSalesforce(it)
                    )
                    Response(OK).body(gson.toJson(result))
                }
            } catch (_: JsonParseException) {
                Response(BAD_REQUEST).body("Failed to parse request body as json array")
            }
        }
    }

    data class ValidationResult(val errorMessage: String, val kjedeId: String = "", val aktorId: String = "", val fnr: String = "") {
        fun isInvalid() = errorMessage.isNotBlank()
    }

    private fun validateJsonArray(jsonArray: JsonArray): ValidationResult {
        if (jsonArray.size() < 1) {
            return ValidationResult("JSON array must contain at least one JSON object")
        }
        try {
            val firstObject = jsonArray[0].asJsonObject
            val kjedeId = firstObject[KJEDE_ID]?.asString ?: ""
            val aktorId = firstObject[AKTOR_ID]?.asString ?: ""
            val fnr = firstObject[FNR]?.asString ?: ""

            if (kjedeId.isBlank()) {
                return ValidationResult("Missing field kjedeId in json")
            } else if (aktorId.isBlank()) {
                return ValidationResult("Missing field aktorId in json")
            } else if (fnr.isBlank()) {
                return ValidationResult("Missing field fnr in json")
            }

            for (i in 1 until jsonArray.size()) {
                val obj = jsonArray[i].asJsonObject
                val currentKjedeId = obj[KJEDE_ID]?.asString
                val currentAktorId = obj[AKTOR_ID]?.asString
                val currentFnr = obj[FNR]?.asString

                if (currentKjedeId != kjedeId) {
                    return ValidationResult("Inconsistent kjedeId found in JSON array")
                } else if (currentAktorId != aktorId) {
                    return ValidationResult("Inconsistent aktorId found in JSON array")
                } else if (currentFnr != fnr) {
                    return ValidationResult("Inconsistent fnr found in JSON array")
                }
            }
            return ValidationResult("", kjedeId, aktorId, fnr)
        } catch (_: JsonParseException) {
            return ValidationResult("Failed to parse json array element as json object")
        }
    }

    val batchUpsertHenvendelserHandler: HttpHandler = {
        if (it.bodyString().isBlank()) {
            Response(BAD_REQUEST).body("Missing request body")
        } else {
            try {
                val jsonArray = JsonParser.parseString(it.bodyString()).asJsonArray
                if (jsonArray.size() < 1) {
                    Response(BAD_REQUEST).body("Batch JSON array must contain at least one JSON array")
                } else {
                    jsonArray
                        .map { e -> validateJsonArray(e as JsonArray) }
                        .firstOrNull { r -> r.isInvalid() }
                        ?.let { r ->
                            Response(BAD_REQUEST).body("Request contains json array with error: ${r.errorMessage}")
                        } ?: run {
                        val updatedKjedeIds: MutableList<String> = mutableListOf()
                        jsonArray.forEach { e ->
                            val array = e as JsonArray
                            val firstObject = array.get(0) as JsonObject
                            val result = database.upsertHenvendelse(
                                kjedeId = firstObject[KJEDE_ID].asString,
                                aktorId = firstObject[AKTOR_ID].asString,
                                fnr = firstObject[FNR].asString,
                                json = array.toString(),
                                updateBySF = tokenValidator.hasTokenFromSalesforce(it)
                            )
                            result?.let { updatedKjedeIds.add(result.kjedeId) }
                        }
                        // log.info { "Upserted ${updatedKjedeIds.size} items" }
                        Response(OK).body("Upserted ${updatedKjedeIds.size} items")
                    }
                }
            } catch (_: JsonParseException) {
                Response(BAD_REQUEST).body("Failed to parse request body as json array")
            }
        }
    }

    val fetchHenvendelseByKjedeIdHandler: HttpHandler = {
        val kjedeId = it.query(KJEDE_ID)
        if (kjedeId == null) {
            Response(BAD_REQUEST).body("Missing parameter kjedeId")
        } else {
            val result = database.henteHenvendelse(kjedeId)
            Response(OK).body(gson.toJson(result))
        }
    }

    val fetchHenvendelserByAktorIdHandler: HttpHandler = {
        val aktorId = it.query(AKTOR_ID)
        if (aktorId == null) {
            Response(BAD_REQUEST).body("Missing parameter aktorId")
        } else {
            val result = database.henteHenvendelserByAktorId(aktorId)
            Response(OK).body(gson.toJson(result))
        }
    }

    /**
     * POSTGRES variants
     */
    val cacheHenvendelselistePost: HttpHandler = {
        val aktorIdParam = it.query(AKTOR_ID)
        if (aktorIdParam == null) {
            Response(BAD_REQUEST).body("Missing $AKTOR_ID param")
        } else {
            // log.info { "Postgres Cache PUT on aktorId $aktorIdParam" }
            // loggedCacheRequests++
            // if (loggedCacheRequests <= loggedCacheRequestsLimit) {
            //    File("/tmp/cache-request-${String.format("%03d", loggedCacheRequests)}-$aktorIdParam").writeText(it.toMessage())
            // }
            val success = database.cachePut(aktorIdParam, it.bodyString(), TTLInSecondsPostgres)
            if (success) {
                GlobalScope.launch {
                    updateKjedeToAktorAssociations(it.bodyString(), database)
                }
                Response(OK)
            } else {
                Response(Status.INTERNAL_SERVER_ERROR).body("Failed to perform postgres put")
            }
        }
    }

    fun updateKjedeToAktorAssociations(json: String, dbRef: PostgresDatabase) {
        dbRef.bulkKjedeToAktorIdPut(findKjedeToAktorAssociations(json), TTLInSecondsPostgres)
    }

    fun findKjedeToAktorAssociations(json: String): Set<Pair<String, String>> {
        val jsonElement = JsonParser.parseString(json)
        val associations = mutableSetOf<Pair<String, String>>()

        fun findAssociations(element: JsonElement) {
            when {
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    if (obj.has("kjedeId") && obj.has("aktorId")) {
                        val kjedeId = obj["kjedeId"].asString
                        val aktorId = obj["aktorId"].asString
                        associations.add(kjedeId to aktorId)
                    }
                    obj.entrySet().forEach { findAssociations(it.value) }
                }
                element.isJsonArray -> element.asJsonArray.forEach { findAssociations(it) }
            }
        }

        findAssociations(jsonElement)
        return associations
    }

    val cacheHenvendelselisteGet: HttpHandler = {
        val aktorIdParam = it.query(AKTOR_ID)
        if (aktorIdParam == null) {
            Response(BAD_REQUEST).body("Missing $AKTOR_ID param")
        } else {
            val result = database.cacheGet(aktorIdParam)
            if (result == null) {
                Response(NO_CONTENT)
            } else {
                val expiresAt = result.expiresAt
                val lastModified = expiresAt?.minusSeconds(TTLInSecondsPostgres.toLong())
                val formattedLastModified = lastModified?.toString() ?: ""
                Response(OK)
                    .header("cache_last_modified", formattedLastModified)
                    .body(result.json)
            }
        }
    }

    val cacheHenvendelselisteDeleteByKjedeId: HttpHandler = {
        val kjedeIdParam = it.query(KJEDE_ID)
        if (kjedeIdParam == null) {
            Response(BAD_REQUEST).body("Missing $KJEDE_ID param")
        } else {
            try {
                val aktorId = database.kjedeToAktorIdGet(kjedeIdParam)
                if (aktorId != null) {
                    // File("/tmp/latestLookup").writeText("$kjedeIdParam to $aktorId")
                    database.deleteCache(aktorId)
                }
                Response(OK)
            } catch (e: Exception) {
                File("/tmp/failedDeleteByKjedeId").writeText(e.stackTraceToString())
                Response(INTERNAL_SERVER_ERROR)
            }
        }
    }
    val cacheHenvendelselisteDelete: HttpHandler = {
        val aktorIdParam = it.query(AKTOR_ID)
        if (aktorIdParam == null) {
            Response(BAD_REQUEST).body("Missing $AKTOR_ID param")
        } else {
            // log.info { "Postgres Cache DELETE on aktorIds $aktorIdParam" }
            val aktorIds = aktorIdParam.split(",")
            aktorIds.forEach { aktorId ->
                database.deleteCache(aktorId)
            }

            try {
                if (tokenValidator.hasTokenFromSalesforce(it)) {
                    Metrics.cacheDelete.labels("salesforce").inc()
                    log.info { "Cache DELETE from SF ($aktorIdParam)" }
                } else {
                    Metrics.cacheDelete.labels("proxy").inc()
                    log.info { "Cache DELETE from proxy ($aktorIdParam)" }
                }
            } catch (e: Exception) {
                log.error { "Failed to register cache delete metric: " + e.stackTraceToString() }
            }
            Response(OK)
        }
    }

    val cachePostgresCount: HttpHandler = {
        val result = database.cacheCountRows()
        Metrics.cacheSize.set(result.toDouble())
        log.info { "Cache size check result $result" }
        val assDel = database.kjedeToAktorIdDeleteExpiredRows()
        val deleted = database.deleteExpiredRows()
        Response(OK).body("$result, deleted $deleted, ass $assDel")
    }

    val cachePostgresClear: HttpHandler = {
        val assDel = database.kjedeToAktorIdDeleteAllRows()
        val deleted = database.deleteAllRows()
        Response(OK).body("Deleted $deleted, ass $assDel")
    }

    val cacheProbe: HttpHandler = {
        val aktorIdParam = it.query(AKTOR_ID)
        if (aktorIdParam == null) {
            Response(BAD_REQUEST).body("Missing $AKTOR_ID param")
        } else {
            val result = database.cacheGet(aktorIdParam)
            if (result == null) {
                Response(OK).body("$aktorIdParam Not in cache")
            } else {
                val expiresAt = result.expiresAt
                val lastModified = expiresAt?.minusSeconds(TTLInSecondsPostgres.toLong())
                val formattedLastModified = lastModified?.toString() ?: ""
                val wouldBeResponse = Response(OK)
                    .header("cache_last_modified", formattedLastModified)
                    .body(result.json)
                File("/tmp/probe-$aktorIdParam").writeText(wouldBeResponse.toMessage())
                Response(OK).body("Found cached entry on $aktorIdParam - cache_last_modified $formattedLastModified")
            }
        }
    }
}
