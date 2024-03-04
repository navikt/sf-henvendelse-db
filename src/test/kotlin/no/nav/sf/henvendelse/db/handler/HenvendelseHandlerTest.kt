package no.nav.sf.henvendelse.db.handler

import com.google.gson.JsonParser
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
        every { mockTokenValidator.hasTokenFromSalesforce(any()) } returns false
        every { mockTokenOptional.isPresent } returns true
        every { mockTokenOptional.get() } returns mockToken
        every { mockDatabase.upsertHenvendelse(any(), any(), any(), any()) } returns null
    }

    @Test
    fun `upsertHenvendelseHandler should handle valid request`() {
        val request = Request(Method.POST, "/henvendelse")
            .body("""[{ "kjedeId" : "test", "aktorId" : "aktor1", "data" : "test-data" }]""")

        henvendelseHandler.upsertHenvendelseHandler(request)

        verify {
            mockDatabase.upsertHenvendelse(
                kjedeId = "test",
                aktorId = "aktor1",
                json =
                    """[{"kjedeId":"test","aktorId":"aktor1","data":"test-data"}]""",
                updateBySF = false
            )
        }
    }

    @Test
    fun `upsertHenvendelseHandler should call upsertHenvendelse with updated by SF if validator says it has token from salesforce`() {
        val request = Request(Method.POST, "/henvendelse")
            .body("""[{ "kjedeId" : "test", "aktorId" : "aktor1", "data" : "test-data" }, { "kjedeId" : "test", "aktorId" : "aktor1", "data" : "test-data2" }]""")

        every { mockTokenValidator.hasTokenFromSalesforce(any()) } returns true

        henvendelseHandler.upsertHenvendelseHandler(request)

        verify {
            mockDatabase.upsertHenvendelse(
                kjedeId = "test",
                aktorId = "aktor1",
                json =
                    """[{"kjedeId":"test","aktorId":"aktor1","data":"test-data"},{"kjedeId":"test","aktorId":"aktor1","data":"test-data2"}]""",
                updateBySF = true
            )
        }
    }

    @Test
    fun `upsertHenvendelseHandler should reject invalid JSON`() {
        val request = Request(Method.POST, "/henvendelse").body("invalid json")

        val response = henvendelseHandler.upsertHenvendelseHandler(request)

        Assertions.assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `upsertHenvendelseHandler should reject empty json array`() {
        val request = Request(Method.POST, "/henvendelse").body("[]")

        val response = henvendelseHandler.upsertHenvendelseHandler(request)

        Assertions.assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `upsertHenvendelseHandler should reject missing kjedeId`() {
        val request = Request(Method.POST, "/henvendelse").body("""[{ "aktorId" : "aktor1", "data" : "test-data" }]""")

        val response = henvendelseHandler.upsertHenvendelseHandler(request)

        Assertions.assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `upsertHenvendelseHandler should reject missing aktorId`() {
        val request = Request(Method.POST, "/henvendelse").body("""[{ "kjedeId" : "test", "data" : "test-data" }]""")

        val response = henvendelseHandler.upsertHenvendelseHandler(request)

        Assertions.assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `upsertHenvendelseHandler should reject inconsistent aktorid between json objects`() {
        val request = Request(Method.POST, "/henvendelse")
            .body("""[{ "kjedeId" : "test", "aktorId" : "aktor1", "data" : "test-data" }, { "kjedeId" : "test", "aktorId" : "aktor1-deviant", "data" : "test-data2" }]""")

        val response = henvendelseHandler.upsertHenvendelseHandler(request)

        Assertions.assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `upsertHenvendelseHandler should reject inconsistent kjedeId between json objects`() {
        val request = Request(Method.POST, "/henvendelse")
            .body("""[{ "kjedeId" : "test", "aktorId" : "aktor1", "data" : "test-data" }, { "kjedeId" : "test-deviant", "aktorId" : "aktor1", "data" : "test-data2" }]""")

        val response = henvendelseHandler.upsertHenvendelseHandler(request)

        Assertions.assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `batchUpsertHenvendelserHandler should handle valid request with two henvendelse chains`() {
        val request = Request(Method.PUT, "/henvendelser")
            .body(
                """[[{"kjedeId": "test1", "aktorId": "aktor1", "data": "data1"}], 
                [{"kjedeId": "test2", "aktorId": "aktor2", "data": "data2"}, {"kjedeId": "test2", "aktorId": "aktor2", "data": "data3"}]]"""
            )

        println(request.body)
        println(JsonParser.parseString(request.body.toString()))
        henvendelseHandler.batchUpsertHenvendelserHandler(request)

        verify {
            mockDatabase.upsertHenvendelse(
                kjedeId = "test1",
                aktorId = "aktor1",
                json =
                    """[{"kjedeId":"test1","aktorId":"aktor1","data":"data1"}]""",
                updateBySF = false
            )
        }

        verify {
            mockDatabase.upsertHenvendelse(
                kjedeId = "test2",
                aktorId = "aktor2",
                json =
                    """[{"kjedeId":"test2","aktorId":"aktor2","data":"data2"},{"kjedeId":"test2","aktorId":"aktor2","data":"data3"}]""",
                updateBySF = false
            )
        }
    }

    @Test
    fun `batchUpsertHenvendelserHandler should reject empty json array`() {
        val request = Request(Method.PUT, "/henvendelser")
            .body("""[]""")

        val response = henvendelseHandler.batchUpsertHenvendelserHandler(request)

        Assertions.assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `batchUpsertHenvendelserHandler should reject empty json array as one element`() {
        val request = Request(Method.PUT, "/henvendelser")
            .body(
                """[[{"kjedeId": "test1", "aktorId": "aktor1", "data": "data1"}], 
                []"""
            )

        val response = henvendelseHandler.batchUpsertHenvendelserHandler(request)

        Assertions.assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `batchUpsertHenvendelserHandler should reject missing aktorId in one element`() {
        val request = Request(Method.PUT, "/henvendelser")
            .body(
                """[[{"kjedeId": "test1", "aktorId": "aktor1", "data": "data1"}], 
                [{"kjedeId": "test2", "aktorId": "aktor2", "data": "data2"}, {"kjedeId": "test2", "data": "data3"}]]"""
            )

        val response = henvendelseHandler.batchUpsertHenvendelserHandler(request)

        Assertions.assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `batchUpsertHenvendelserHandler should reject inconsistent kjedeId in one element`() {
        val request = Request(Method.PUT, "/henvendelser")
            .body(
                """[[{"kjedeId": "test1", "aktorId": "aktor1", "data": "data1"}], 
                [{"kjedeId": "test2", "aktorId": "aktor2", "data": "data2"}, {"kjedeId": "test2-inconsistent", "aktorId": "aktor2", "data": "data3"}]]"""
            )

        val response = henvendelseHandler.batchUpsertHenvendelserHandler(request)

        Assertions.assertEquals(Status.BAD_REQUEST, response.status)
    }
}
