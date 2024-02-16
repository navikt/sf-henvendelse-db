package no.nav.sf.henvendelse.db.handler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.sf.henvendelse.db.database.PostgresDatabase
import no.nav.sf.henvendelse.db.gson
import no.nav.sf.henvendelse.db.token.TokenValidator
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class HenvendelseHandlerTest {
    val mockDatabase = mockk<PostgresDatabase>()
    val mockTokenValidator = mockk<TokenValidator>()
    val mockTokenOptional = mockk<Optional<JwtToken>>()
    val mockToken = mockk<JwtToken>()

    val henvendelseHandler = HenvendelseHandler(mockDatabase, mockTokenValidator, gson)

    @BeforeEach
    fun setup() {
        every { mockTokenValidator.firstValidToken(any()) } returns mockTokenOptional
        every { mockTokenValidator.hasTokenFromSalesforce(any()) } returns true
        every { mockTokenOptional.isPresent } returns true
        every { mockTokenOptional.get() } returns mockToken
    }

    @Test
    fun `upsertHenvendelseHandler should handle valid request`() {
        val request = Request(Method.POST, "/henvendelse")
            .body("""{ "kjedeId" : "test", "aktorId" : "aktor1", "data" : "test-data" }""")

        every { mockDatabase.upsertHenvendelse(any(), any(), any(), any()) } returns null

        henvendelseHandler.upsertHenvendelseHandler(request)

        verify {
            mockDatabase.upsertHenvendelse(
                kjedeId = "test",
                aktorId = "aktor1",
                json =
                    """{ "kjedeId" : "test", "aktorId" : "aktor1", "data" : "test-data" }""",
                updateBySF = true
            )
        }
    }

    @Test
    fun `upsertHenvendelseHandler should call upsertHenvendelse with updated by SF if token has salesforce as source in azp_name`() {
        val request = Request(Method.POST, "/henvendelse")
            .body("""{ "kjedeId" : "test", "aktorId" : "aktor1", "data" : "test-data" }""")

        every { mockDatabase.upsertHenvendelse(any(), any(), any(), any()) } returns null

        henvendelseHandler.upsertHenvendelseHandler(request)

        verify {
            mockDatabase.upsertHenvendelse(
                kjedeId = "test",
                aktorId = "aktor1",
                json =
                    """{ "kjedeId" : "test", "aktorId" : "aktor1", "data" : "test-data" }""",
                updateBySF = true
            )
        }
    }

    @Test
    fun `upsertHenvendelseHandler should handle invalid JSON`() {
        val request = Request(Method.POST, "/henvendelse").body("invalid json")

        val response = henvendelseHandler.upsertHenvendelseHandler(request)

        Assertions.assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `batchUpsertHenvendelserHandler should handle valid request`() {
        val request = Request(Method.PUT, "/henvendelser")
            .body("""[{"kjedeId": "test1", "aktorId": "aktor1", "data": "data1"}, {"kjedeId": "test2", "aktorId": "aktor2", "data": "data2"}]""")

        every { mockDatabase.upsertHenvendelse(any(), any(), any(), any()) } returns null

        henvendelseHandler.batchUpsertHenvendelserHandler(request)

        verify(exactly = 2) {
            mockDatabase.upsertHenvendelse(any(), any(), any(), any())
        }
    }

    @Test
    fun `batchUpsertHenvendelserHandler should handle missing kjedeId in one item`() {
        val request = Request(Method.PUT, "/henvendelser")
            .body("""[{"kjedeId": "test1", "aktorId": "aktor1", "data": "data1"}, {"aktorId": "aktor2", "data": "data2"}]""")

        val response = henvendelseHandler.batchUpsertHenvendelserHandler(request)

        Assertions.assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun jafa() {
        val body =
            """{"aktorId" : "1234567","kjedeId" : "teaspoon", "data" : "spoon2"},{"aktorId" : "789","kjedeId" : "furniture", "data" : "table"}"""
        val request = Request(Method.POST, "/henvendelse").body(body)
        val response = henvendelseHandler.upsertHenvendelseHandler(request)
        println(response.toMessage())
    }
}
