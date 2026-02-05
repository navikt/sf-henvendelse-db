package no.nav.sf.henvendelse.db.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Henvendelseliste : Table() {
    val aktorId = varchar("aktorid", 20)
    val page = integer("page")
    val pageSize = integer("pageSize")
    val json = text("json")
    val expiresAt = datetime("expires_at").nullable().index()

    override val primaryKey = PrimaryKey(aktorId, page, pageSize)
}
