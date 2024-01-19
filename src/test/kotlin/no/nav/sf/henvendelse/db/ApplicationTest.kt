package no.nav.sf.henvendelse.db
import com.nimbusds.jwt.JWTClaimsSet
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import no.nav.sf.henvendelse.api.proxy.token.TokenValidator
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
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

    @BeforeEach
    fun setup() {
        every { mockTokenValidator.firstValidToken(any()) } returns mockTokenOptional
        every { mockTokenOptional.isPresent } returns true
        every { mockTokenOptional.get() } returns mockToken
        every { mockToken.tokenAsString } returns "mockToken"
        every { mockToken.jwtTokenClaims } returns jwtTokenClaims
    }

    @Test
    fun `AuthRouteBuilder to method created handler - calling with valid token request should trigger action`() {
        val action = mockk<HttpHandler>()
        val request = mockk<Request>()
        val response = mockk<Response>()
        every { request.uri } returns Uri.of("/path")
        every { request.method } returns Method.GET
        every { action.invoke(any()) } returns response
        // Create an instance of AuthRouteBuilder
        val authRouteBuilder = Application.AuthRouteBuilder("/path", Method.GET, mockTokenValidator)

        val routeHandler = authRouteBuilder to action

        // Base setup with validToken:
        routeHandler.invoke(request)
        verify { action(request) }
    }

    @Test
    fun `AuthRouteBuilder to method created handler - calling with invalid token request should NOT trigger action`() {
        val action = mockk<HttpHandler>()
        val request = mockk<Request>()
        val response = mockk<Response>()
        every { request.uri } returns Uri.of("/path")
        every { request.method } returns Method.GET
        every { action.invoke(any()) } returns response

        every { mockTokenOptional.isPresent } returns false
        every { mockTokenValidator.firstValidToken(any()) } returns mockTokenOptional

        // Create an instance of AuthRouteBuilder
        val authRouteBuilder = Application.AuthRouteBuilder("/path", Method.GET, mockTokenValidator)

        val routeHandler = authRouteBuilder to action
        // Invalid token
        routeHandler.invoke(request)
        verify(exactly = 0) { action(any()) }
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
    fun `upsertHenvendelseHandler should call upsertHenvendelse with updated by SF if token has salesforce as source in azp_name`() {
        val request = Request(Method.POST, "/henvendelse")
            .body("""{ "id" : "test", "aktorId" : "aktor1", "data" : "test-data" }""")

        every { mockDatabase.upsertHenvendelse(any(), any(), any(), any()) } returns null

        jwtTokenClaims = JwtTokenClaims(JWTClaimsSet.Builder().claim("azp_name", "dev-external:teamcrm:salesforce").build())
        every { mockToken.jwtTokenClaims } returns jwtTokenClaims

        application.upsertHenvendelseHandler(request)

        verify {
            mockDatabase.upsertHenvendelse(
                id = "test",
                aktorid = "aktor1",
                json =
                    """{ "id" : "test", "aktorId" : "aktor1", "data" : "test-data" }""",
                updateBySF = true
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
