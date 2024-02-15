package no.nav.sf.henvendelse.db
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.sf.henvendelse.db.token.TokenValidator
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Uri
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class ApplicationTest {
    private val mockTokenValidator = mockk<TokenValidator>()
    private val mockTokenOptional = mockk<Optional<JwtToken>>()

    @BeforeEach
    fun setup() {
        every { mockTokenValidator.firstValidToken(any()) } returns mockTokenOptional
        every { mockTokenOptional.isPresent } returns true
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
        every { mockTokenOptional.isPresent } returns false // No approved tokens
        every { mockTokenValidator.firstValidToken(any()) } returns mockTokenOptional

        // Create an instance of AuthRouteBuilder
        val authRouteBuilder = Application.AuthRouteBuilder("/path", Method.GET, mockTokenValidator)

        val routeHandler = authRouteBuilder to action
        // Invalid token
        routeHandler.invoke(request)
        verify(exactly = 0) { action(any()) }
    }
}
