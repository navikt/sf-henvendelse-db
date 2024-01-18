package no.nav.sf.henvendelse.db

import com.google.gson.Gson
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status

class GuiHandler(database: PostgresDatabase, gson: Gson) {
    private val viewPageSize = System.getenv("VIEW_PAGE_SIZE").toInt()

    private data class ViewData(
        val page: Long,
        val pageCount: Long,
        val pageSize: Int,
        val count: Long,
        val records: List<HenvendelseRecord>
    )

    private fun pageCount(count: Long): Long {
        return (count + viewPageSize - 1) / viewPageSize
    }

    val viewHandler: HttpHandler = {
        val page = it.query("page")!!.toLong()
        val count = database.count()
        val result = database.view(page, viewPageSize)
        val viewData = ViewData(page, pageCount(count), viewPageSize, count, result)
        Response(Status.OK).body(gson.toJson(viewData))
    }
}
