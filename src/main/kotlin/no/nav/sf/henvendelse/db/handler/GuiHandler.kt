package no.nav.sf.henvendelse.db.handler

import com.google.gson.Gson
import no.nav.sf.henvendelse.db.config_VIEW_PAGE_SIZE
import no.nav.sf.henvendelse.db.database.HenvendelseRecord
import no.nav.sf.henvendelse.db.database.PostgresDatabase
import no.nav.sf.henvendelse.db.token.TokenValidator
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import java.io.File

class GuiHandler(database: PostgresDatabase, gson: Gson, tokenValidator: TokenValidator) {
    private val viewPageSize = System.getenv(config_VIEW_PAGE_SIZE).toInt()

    private data class ViewData(
        val page: Long,
        val pageCount: Long,
        val pageSize: Int,
        val count: Long,
        val records: List<HenvendelseRecord>,
        val username: String,
        val expireTime: Long
    )

    private fun pageCount(count: Long): Long {
        return (count + viewPageSize - 1) / viewPageSize
    }

    val viewHandler: HttpHandler = {
        val page = it.query("page")!!.toLong()
        val count = database.count()
        val viewData = ViewData(
            page = page,
            pageCount = pageCount(count),
            pageSize = viewPageSize,
            count = count,
            records = database.view(page, viewPageSize),
            username = tokenValidator.nameClaim(it),
            expireTime = tokenValidator.expireTime(it)
        )
        File("/tmp/latestviewtoken").writeText(tokenValidator.firstValidToken(it)?.encodedToken ?: "null")
        Response(Status.OK).body(gson.toJson(viewData))
    }
}
