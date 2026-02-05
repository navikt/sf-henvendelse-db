package no.nav.sf.henvendelse.db.handler

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import mu.KotlinLogging
import no.nav.sf.henvendelse.db.Metrics
import no.nav.sf.henvendelse.db.database.PostgresDatabase
import no.nav.sf.henvendelse.db.token.TokenValidator
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import java.io.ByteArrayInputStream
import java.io.File

const val AKTOR_ID = "aktorId"
const val PAGE = "page"
const val PAGE_SIZE = "pageSize"

class HenvendelseHandler(
    database: PostgresDatabase,
    tokenValidator: TokenValidator,
    gson: Gson,
) {
    private val timeToLiveInSecondsPostgres: Int = 60 * 60 * 96 // 96h

    private val log = KotlinLogging.logger { }

    data class ValidationResult(
        val errorMessage: String,
        val kjedeId: String = "",
        val aktorId: String = "",
        val fnr: String = "",
    ) {
        fun isInvalid() = errorMessage.isNotBlank()
    }

    /**
     * POSTGRES variants
     */
    val cacheHenvendelselistePost: HttpHandler = {
        val aktorIdParam = it.query(AKTOR_ID)
        val pageParam = it.query(PAGE)?.toInt() ?: 1
        val pageSizeParam = it.query(PAGE_SIZE)?.toInt() ?: 50
        if (aktorIdParam == null) {
            Response(BAD_REQUEST).body("Missing $AKTOR_ID param")
        } else {
            val success = database.cachePut(aktorIdParam, pageParam, pageSizeParam, it.bodyString(), timeToLiveInSecondsPostgres)
            if (success) {
                Response(OK)
            } else {
                Response(Status.INTERNAL_SERVER_ERROR).body("Failed to perform postgres put")
            }
        }
    }

    val cacheHenvendelselisteGet: HttpHandler = {
        val aktorIdParam = it.query(AKTOR_ID)
        val pageParam = it.query(PAGE)?.toInt() ?: 1
        val pageSizeParam = it.query(PAGE_SIZE)?.toInt() ?: 50
        if (aktorIdParam == null) {
            Response(BAD_REQUEST).body("Missing $AKTOR_ID param")
        } else {
            val result = database.cacheGet(aktorIdParam, pageParam, pageSizeParam)
            if (result == null) {
                Response(NO_CONTENT)
            } else {
                val expiresAt = result.expiresAt
                val lastModified = expiresAt?.minusSeconds(timeToLiveInSecondsPostgres.toLong())
                val formattedLastModified = lastModified?.toString() ?: ""
                // Wrap the JSON string as InputStream to stream body
                val inputStream = ByteArrayInputStream(result.json.toByteArray(Charsets.UTF_8))

                Response(OK)
                    .header("Content-Type", "application/json")
                    .header("cache_last_modified", formattedLastModified)
                    .body(inputStream)
            }
        }
    }

    val cacheHenvendelselisteDelete: HttpHandler = {
        val aktorIdParam = it.query(AKTOR_ID)
        if (aktorIdParam == null) {
            Response(BAD_REQUEST).body("Missing $AKTOR_ID param")
        } else {
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
        val deleted = database.deleteExpiredRows()
        Response(OK).body("$result, deleted $deleted")
    }

    val cachePostgresClear: HttpHandler = {
        val deleted = database.deleteAllRows()
        Response(OK).body("Deleted $deleted")
    }

    val cacheProbe: HttpHandler = {
        val aktorIdParam = it.query(AKTOR_ID)
        val pageParam = it.query(PAGE)?.toInt() ?: 1
        val pageSizeParam = it.query(PAGE_SIZE)?.toInt() ?: 50
        if (aktorIdParam == null) {
            Response(BAD_REQUEST).body("Missing $AKTOR_ID param")
        } else {
            val result = database.cacheGet(aktorIdParam, pageParam, pageSizeParam)
            if (result == null) {
                Response(OK).body("$aktorIdParam Not in cache")
            } else {
                val expiresAt = result.expiresAt
                val lastModified = expiresAt?.minusSeconds(timeToLiveInSecondsPostgres.toLong())
                val formattedLastModified = lastModified?.toString() ?: ""
                val wouldBeResponse =
                    Response(OK)
                        .header("cache_last_modified", formattedLastModified)
                        .body(result.json)
                File("/tmp/probe-$aktorIdParam").writeText(wouldBeResponse.toMessage())
                Response(OK).body("Found cached entry on $aktorIdParam - cache_last_modified $formattedLastModified")
            }
        }
    }

    val clearDbHandler: HttpHandler = {
        database.createCache(true)
        Response(OK).body("Recreated db")
    }

    val initDbHandler: HttpHandler = {
        database.createCache(false)
        Response(OK).body("Db created")
    }

    val purgeOldHandler: HttpHandler = {
        database.purgeOld()
        Response(OK).body("Purged")
    }
}
