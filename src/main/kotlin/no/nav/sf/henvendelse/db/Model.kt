package no.nav.sf.henvendelse.db

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * Max limit of postgres varchar of 10 MB (10485760 chars)
 * Note that postgres do not pre-allocate space, so a large varchar will not affect footprint in db
 */
const val MAX_LIMIT_VARCHAR = 10485760

object Henvendelser : Table() {
    val id = varchar("id", 18).uniqueIndex()
    val aktorid = varchar("aktorid", 20).index()
    val json = varchar("json", MAX_LIMIT_VARCHAR)

    // Record metadata
    val lastModified = datetime("last_modified").index()
    val lastModifiedBySF = bool("last_modified_by_sf")
}

data class Henvendelse(val id: String, val aktorid: String, val json: String)

data class HenvendelseRecord(val id: String, val aktorid: String, val json: String, val lastModified: LocalDateTime, val lastModifiedBySF: Boolean)

fun ResultRow.toHenvendelseRecord() =
    HenvendelseRecord(
        id = this[Henvendelser.id],
        aktorid = this[Henvendelser.aktorid],
        json = this[Henvendelser.json],
        lastModified = this[Henvendelser.lastModified],
        lastModifiedBySF = this[Henvendelser.lastModifiedBySF]
    )
