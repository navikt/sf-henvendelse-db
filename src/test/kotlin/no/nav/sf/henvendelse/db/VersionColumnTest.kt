package no.nav.sf.henvendelse.db

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.sf.henvendelse.db.database.PostgresDatabase
import no.nav.sf.henvendelse.db.handler.GuiHandler
import no.nav.sf.henvendelse.db.handler.HenvendelseHandler
import no.nav.sf.henvendelse.db.token.TokenValidator
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VersionColumnTest {
    private val database = mockk<PostgresDatabase>()
    private val tokenValidator = mockk<TokenValidator>()
    private val handler = HenvendelseHandler(database, tokenValidator, gson)
    private val app =
        Application(
            tokenValidator = tokenValidator,
            database = database,
            gui = mockk<GuiHandler>(relaxed = true),
            henvendelse = handler,
        ).api()

    @Test
    fun `browser endpoint confirms migration and permits repeated calls`() {
        every { database.addVersionColumn() } returns Unit

        repeat(2) {
            val response = app(Request(Method.GET, "/internal/cache/addVersion"))

            assertEquals(Status.OK, response.status)
            assertEquals("no-store", response.header("Cache-Control"))
            assertEquals(
                "Confirmed: henvendelseliste.version exists. This endpoint does not change the primary key.",
                response.bodyString(),
            )
        }
        verify(exactly = 2) { database.addVersionColumn() }
    }

    @Test
    fun `browser endpoint reports migration failure`() {
        every { database.addVersionColumn() } throws IllegalStateException("Database unavailable")

        val response = app(Request(Method.GET, "/internal/cache/addVersion"))

        assertEquals(Status.INTERNAL_SERVER_ERROR, response.status)
        assertEquals("no-store", response.header("Cache-Control"))
        assertEquals(
            "Failed to add version column. Check application logs for details.",
            response.bodyString(),
        )
    }

    @Test
    fun `browser endpoint confirms primary key migration and permits repeated calls`() {
        every { database.migrateVersionPrimaryKey() } returns Unit

        repeat(2) {
            val response = app(Request(Method.GET, "/internal/cache/migrateVersionPrimaryKey"))

            assertEquals(Status.OK, response.status)
            assertEquals("no-store", response.header("Cache-Control"))
            assertEquals(
                "Confirmed: henvendelseliste primary key is (aktorId, page, pageSize, version).",
                response.bodyString(),
            )
        }
        verify(exactly = 2) { database.migrateVersionPrimaryKey() }
    }

    @Test
    fun `browser endpoint reports primary key migration failure`() {
        every { database.migrateVersionPrimaryKey() } throws IllegalStateException("Database unavailable")

        val response = app(Request(Method.GET, "/internal/cache/migrateVersionPrimaryKey"))

        assertEquals(Status.INTERNAL_SERVER_ERROR, response.status)
        assertEquals("no-store", response.header("Cache-Control"))
        assertEquals(
            "Failed to migrate primary key. Check application logs for details.",
            response.bodyString(),
        )
    }
}
