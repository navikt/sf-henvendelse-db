package no.nav.sf.henvendelse.db.handler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.sf.henvendelse.db.database.PostgresDatabase
import no.nav.sf.henvendelse.db.gson
import no.nav.sf.henvendelse.db.token.TokenValidator
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CacheVersionTest {
    private val database = mockk<PostgresDatabase>()
    private val tokenValidator = mockk<TokenValidator>()
    private val handler = HenvendelseHandler(database, tokenValidator, gson)

    @Test
    fun `GET defaults to v1 and forwards explicit versions and pagination`() {
        val expiresAt = LocalDateTime.of(2026, 10, 22, 12, 0)
        listOf("" to "v1", "&version=v1" to "v1", "&version=v2" to "v2", "&version=${"a".repeat(32)}" to "a".repeat(32))
            .forEach { (query, version) ->
                every { database.cacheGet("123", 2, 10, version) } returns
                    PostgresDatabase.CachedValue("""{"version":"$version"}""", expiresAt)

                val response =
                    handler.cacheHenvendelselisteGet(
                        Request(Method.GET, "/cache/henvendelseliste?aktorId=123&page=2&pageSize=10$query"),
                    )

                assertEquals(Status.OK, response.status)
                assertEquals("""{"version":"$version"}""", response.bodyString())
                assertEquals("application/json", response.header("Content-Type"))
                assertEquals(expiresAt.minusHours(96).toString(), response.header("cache_last_modified"))
            }
    }

    @Test
    fun `POST defaults to v1 and forwards explicit versions body pagination and ttl`() {
        listOf("" to "v1", "&version=v1" to "v1", "&version=v2" to "v2", "&version=${"a".repeat(32)}" to "a".repeat(32))
            .forEach { (query, version) ->
                every { database.cachePut("123", 2, 10, "[]", 345600, version) } returns true

                val response =
                    handler.cacheHenvendelselistePost(
                        Request(Method.POST, "/cache/henvendelseliste?aktorId=123&page=2&pageSize=10$query").body("[]"),
                    )

                assertEquals(Status.OK, response.status)
                verify { database.cachePut("123", 2, 10, "[]", 345600, version) }
            }
    }

    @Test
    fun `GET retains default pagination and returns no content on cache miss`() {
        every { database.cacheGet("123", 1, 50, "v1") } returns null

        val response = handler.cacheHenvendelselisteGet(Request(Method.GET, "/cache/henvendelseliste?aktorId=123"))

        assertEquals(Status.NO_CONTENT, response.status)
        verify { database.cacheGet("123", 1, 50, "v1") }
    }

    @Test
    fun `POST retains default pagination and reports write failure`() {
        every { database.cachePut("123", 1, 50, "[]", 345600, "v1") } returns false

        val response = handler.cacheHenvendelselistePost(Request(Method.POST, "/cache/henvendelseliste?aktorId=123").body("[]"))

        assertEquals(Status.INTERNAL_SERVER_ERROR, response.status)
        verify { database.cachePut("123", 1, 50, "[]", 345600, "v1") }
    }

    @Test
    fun `GET POST and probe reject empty blank and overlong versions without database calls`() {
        listOf("", "%20", "a".repeat(33)).forEach { version ->
            val request = Request(Method.GET, "/cache/henvendelseliste?aktorId=123&version=$version")

            assertEquals(Status.BAD_REQUEST, handler.cacheHenvendelselisteGet(request).status)
            assertEquals(Status.BAD_REQUEST, handler.cacheHenvendelselistePost(request.method(Method.POST)).status)
            assertEquals(Status.BAD_REQUEST, handler.cacheProbe(request).status)
        }
        verify(exactly = 0) { database.cacheGet(any(), any(), any(), any()) }
        verify(exactly = 0) { database.cachePut(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `GET POST and probe still require actor id`() {
        val request = Request(Method.GET, "/cache/henvendelseliste?version=v2")

        assertEquals(Status.BAD_REQUEST, handler.cacheHenvendelselisteGet(request).status)
        assertEquals(Status.BAD_REQUEST, handler.cacheHenvendelselistePost(request.method(Method.POST)).status)
        assertEquals(Status.BAD_REQUEST, handler.cacheProbe(request).status)
    }

    @Test
    fun `probe defaults to v1 and forwards explicit version`() {
        listOf("" to "v1", "&version=v2" to "v2").forEach { (query, version) ->
            every { database.cacheGet("123", 1, 50, version) } returns null

            val response = handler.cacheProbe(Request(Method.GET, "/internal/cache/probe?aktorId=123$query"))

            assertEquals(Status.OK, response.status)
            verify { database.cacheGet("123", 1, 50, version) }
        }
    }

    @Test
    fun `DELETE ignores version and deletes all cache entries for each actor`() {
        every { database.deleteCache(any()) } returns 3
        every { tokenValidator.hasTokenFromSalesforce(any()) } returns false

        listOf("", "&version=v2", "&version=", "&version=${"a".repeat(33)}").forEach { query ->
            val response = handler.cacheHenvendelselisteDelete(Request(Method.DELETE, "/cache/henvendelseliste?aktorId=123,456$query"))

            assertEquals(Status.OK, response.status)
        }
        verify(exactly = 4) { database.deleteCache("123") }
        verify(exactly = 4) { database.deleteCache("456") }
    }
}
