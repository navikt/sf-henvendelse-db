package no.nav.sf.henvendelse.db

import io.mockk.every
import io.mockk.mockk
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.sf.henvendelse.db.database.PostgresDatabase
import no.nav.sf.henvendelse.db.handler.GuiHandler
import no.nav.sf.henvendelse.db.handler.HenvendelseHandler
import no.nav.sf.henvendelse.db.token.TokenValidator
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CacheMetricsTest {
    private val database = mockk<PostgresDatabase>()
    private val tokenValidator = mockk<TokenValidator>()
    private val app =
        Application(
            tokenValidator = tokenValidator,
            database = database,
            gui = mockk<GuiHandler>(relaxed = true),
            henvendelse = HenvendelseHandler(database, tokenValidator, gson),
        ).api()

    init {
        every { tokenValidator.firstValidToken(any()) } returns mockk<JwtToken>()
    }

    @Test
    fun `authorized GET and POST count effective defaults`() {
        every { database.cacheGet("123", 1, 50, "v1") } returns null
        every { database.cachePut("123", 1, 50, "[]", 345600, "v1") } returns true

        listOf(Method.GET, Method.POST).forEach { method ->
            val counter = Metrics.cacheCalls.labels(method.name, "1", "50", "v1")
            val before = counter.get()

            app(Request(method, "/cache/henvendelseliste?aktorId=123").body("[]"))

            assertEquals(before + 1, counter.get())
        }
    }

    @Test
    fun `exact labels count cache hits misses and failed writes`() {
        every { database.cacheGet("123", 3, 25, "v2") } returns PostgresDatabase.CachedValue("[]", null) andThen null
        every { database.cachePut("123", 3, 25, "[]", 345600, "v2") } returns false
        val getCounter = Metrics.cacheCalls.labels("GET", "3", "25", "v2")
        val postCounter = Metrics.cacheCalls.labels("POST", "3", "25", "v2")
        val getBefore = getCounter.get()
        val postBefore = postCounter.get()
        val url = "/cache/henvendelseliste?aktorId=123&page=3&pageSize=25&version=v2"

        assertEquals(Status.OK, app(Request(Method.GET, url)).status)
        assertEquals(Status.NO_CONTENT, app(Request(Method.GET, url)).status)
        assertEquals(Status.INTERNAL_SERVER_ERROR, app(Request(Method.POST, url).body("[]")).status)

        assertEquals(getBefore + 2, getCounter.get())
        assertEquals(postBefore + 1, postCounter.get())
    }

    @Test
    fun `unauthorized invalid and probe requests do not count as cache calls`() {
        val counter = Metrics.cacheCalls.labels("GET", "4", "15", "v2")
        val before = counter.get()
        val query = "?aktorId=123&page=4&pageSize=15&version=v2"
        every { tokenValidator.firstValidToken(any()) } returns null

        assertEquals(Status.UNAUTHORIZED, app(Request(Method.GET, "/cache/henvendelseliste$query")).status)

        every { tokenValidator.firstValidToken(any()) } returns mockk<JwtToken>()
        assertEquals(
            Status.BAD_REQUEST,
            app(Request(Method.GET, "/cache/henvendelseliste?page=4&pageSize=15&version=v2")).status,
        )
        every { database.cacheGet("123", 4, 15, "v2") } returns null
        assertEquals(Status.OK, app(Request(Method.GET, "/internal/cache/probe$query")).status)

        assertEquals(before, counter.get())
    }

    @Test
    fun `DELETE retains its existing counter without creating versioned cache calls`() {
        every { database.deleteCache("123") } returns 2
        every { tokenValidator.hasTokenFromSalesforce(any()) } returns false
        val deleteCounter = Metrics.cacheDelete.labels("proxy")
        val before = deleteCounter.get()

        assertEquals(
            Status.OK,
            app(Request(Method.DELETE, "/cache/henvendelseliste?aktorId=123&version=v2")).status,
        )

        assertEquals(before + 1, deleteCounter.get())
    }
}
