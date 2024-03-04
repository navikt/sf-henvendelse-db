package no.nav.sf.henvendelse.api.proxy.token

import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import no.nav.sf.henvendelse.db.env_AZURE_APP_CLIENT_ID
import no.nav.sf.henvendelse.db.env_AZURE_APP_WELL_KNOWN_URL
import no.nav.sf.henvendelse.db.token.TokenValidator
import no.nav.sf.henvendelse.db.token.isFromSalesforce
import org.http4k.core.Request
import java.io.File
import java.net.URL
import java.util.Optional

class DefaultTokenValidator : TokenValidator {
    private val azureAlias = "azure"
    private val azureUrl = System.getenv(env_AZURE_APP_WELL_KNOWN_URL)
    private val azureAudience = System.getenv(env_AZURE_APP_CLIENT_ID)?.split(',') ?: listOf()

    private val multiIssuerConfiguration = MultiIssuerConfiguration(
        mapOf(
            azureAlias to IssuerProperties(URL(azureUrl), azureAudience)
        )
    )

    private val jwtTokenValidationHandler = JwtTokenValidationHandler(multiIssuerConfiguration)

    override fun firstValidToken(request: Request): Optional<JwtToken> {
        val result: Optional<JwtToken> = jwtTokenValidationHandler.getValidatedTokens(request.toNavRequest()).firstValidToken
        if (!result.isPresent) {
            File("/tmp/novalidtoken").writeText(request.toMessage())
        }
        return result
    }

    override fun hasTokenFromSalesforce(request: Request) = this.firstValidToken(request).get().isFromSalesforce()

    private fun Request.toNavRequest(): HttpRequest {
        val req = this
        return object : HttpRequest {
            override fun getHeader(headerName: String): String {
                return req.header(headerName) ?: ""
            }
            override fun getCookies(): Array<HttpRequest.NameValue> {
                return arrayOf()
            }
        }
    }
}
