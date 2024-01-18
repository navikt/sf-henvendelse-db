package no.nav.sf.henvendelse.db
import io.mockk.mockk
import no.nav.sf.henvendelse.api.proxy.token.TokenValidator
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplicationTest {
    val tokenValidatorMock = mockk<TokenValidator>()
    val databaseMock = mockk<PostgresDatabase>()
    val guiHandlerMock = mockk<GuiHandler>()
    val application = Application(tokenValidatorMock, databaseMock, guiHandlerMock)

    /*
    @Test
    fun `upsertHenvendelseHandler should handle valid request`() {
        val request = Request(Method.POST, "/henvendelse")
            .body("""{ "id" : "test", "aktorId" : "aktor1", "data" : "test-data" }""")

        val response = application.upsertHenvendelseHandler(request)

        assertEquals(Status.OK, response.status)
    }
     */

    @Test
    fun `upsertHenvendelseHandler should handle invalid JSON`() {
        val request = Request(Method.POST, "/henvendelse").body("invalid json")

        val response = application.upsertHenvendelseHandler(request)

        assertEquals(Status.BAD_REQUEST, response.status)
        // Add more assertions based on expected behavior
    }

    /*
    @Test
    fun `batchUpsertHenvendelserHandler should handle valid request`() {
        val request = Request(Method.PUT, "/henvendelser")
            .body("""[{"id": "test1", "aktorId": "aktor1", "data": "data1"}, {"id": "test2", "aktorId": "aktor2", "data": "data2"}]""")

        val response = application.batchUpsertHenvendelserHandler(request)

        assertEquals(Status.OK, response.status)
    }

     */

    @Test
    fun `batchUpsertHenvendelserHandler should handle missing id in one item`() {
        val request = Request(Method.PUT, "/henvendelser")
            .body("""[{"id": "test1", "aktorId": "aktor1", "data": "data1"}, {"aktorId": "aktor2", "data": "data2"}]""")

        val response = application.batchUpsertHenvendelserHandler(request)

        assertEquals(Status.BAD_REQUEST, response.status)
    }
}
