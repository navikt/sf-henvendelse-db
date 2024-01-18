package no.nav.sf.henvendelse.db
import com.nimbusds.jwt.JWTClaimsSet
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import no.nav.sf.henvendelse.api.proxy.token.TokenValidator
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class ApplicationTest {
    val mockTokenValidator = mockk<TokenValidator>()
    val mockTokenOptional = mockk<Optional<JwtToken>>()
    val mockToken = mockk<JwtToken>()

    val mockDatabase = mockk<PostgresDatabase>()
    val mockGuiHandler = mockk<GuiHandler>()
    val application = Application(mockTokenValidator, mockDatabase, mockGuiHandler)
    var jwtTokenClaims: JwtTokenClaims = JwtTokenClaims(JWTClaimsSet.Builder().build())
/*
jwtTokenClaims = JwtTokenClaims(
            JWTClaimsSet.Builder()
                .claim(claim_azp_name, "azp-name")
                .build()
        )
 */

    @BeforeEach
    fun setup() {
        every { mockTokenValidator.firstValidToken(any()) } returns mockTokenOptional
        every { mockTokenOptional.isPresent } returns true
        every { mockTokenOptional.get() } returns mockToken
        every { mockToken.tokenAsString } returns "mockToken"
        every { mockToken.jwtTokenClaims } returns jwtTokenClaims
    }

    @Test
    fun `upsertHenvendelseHandler should handle valid request`() {
        val request = Request(Method.POST, "/henvendelse")
            .body("""{ "id" : "test", "aktorId" : "aktor1", "data" : "test-data" }""")

        every { mockDatabase.upsertHenvendelse(any(), any(), any(), any()) } returns null

        application.upsertHenvendelseHandler(request)

        verify {
            mockDatabase.upsertHenvendelse(
                id = "test",
                aktorid = "aktor1",
                json =
                    """{ "id" : "test", "aktorId" : "aktor1", "data" : "test-data" }""",
                updateBySF = false
            )
        }
    }

    @Test
    fun `upsertHenvendelseHandler should handle invalid JSON`() {
        val request = Request(Method.POST, "/henvendelse").body("invalid json")

        val response = application.upsertHenvendelseHandler(request)

        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `batchUpsertHenvendelserHandler should handle valid request`() {
        val request = Request(Method.PUT, "/henvendelser")
            .body("""[{"id": "test1", "aktorId": "aktor1", "data": "data1"}, {"id": "test2", "aktorId": "aktor2", "data": "data2"}]""")

        every { mockDatabase.upsertHenvendelse(any(), any(), any(), any()) } returns null

        application.batchUpsertHenvendelserHandler(request)

        verify(exactly = 2) {
            mockDatabase.upsertHenvendelse(any(), any(), any(), any())
        }
    }

    @Test
    fun `batchUpsertHenvendelserHandler should handle missing id in one item`() {
        val request = Request(Method.PUT, "/henvendelser")
            .body("""[{"id": "test1", "aktorId": "aktor1", "data": "data1"}, {"aktorId": "aktor2", "data": "data2"}]""")

        val response = application.batchUpsertHenvendelserHandler(request)

        assertEquals(Status.BAD_REQUEST, response.status)
    }
}
