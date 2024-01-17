package no.nav.sf.henvendelse.api.proxy.token

import mu.KotlinLogging
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import no.nav.sf.henvendelse.db.toNavRequest
import org.http4k.core.Request
import java.io.File
import java.net.URL
import java.util.Optional

const val env_AZURE_APP_WELL_KNOWN_URL = "AZURE_APP_WELL_KNOWN_URL"
const val env_AZURE_APP_CLIENT_ID = "AZURE_APP_CLIENT_ID"
const val env_AUDIENCE_TOKEN_SERVICE_URL = "AUDIENCE_TOKEN_SERVICE_URL"
const val env_AUDIENCE_TOKEN_SERVICE_ALIAS = "AUDIENCE_TOKEN_SERVICE_ALIAS"
const val env_AUDIENCE_TOKEN_SERVICE = "AUDIENCE_TOKEN_SERVICE"

private val log = KotlinLogging.logger { }

interface TokenValidator {
    fun firstValidToken(request: Request): Optional<JwtToken>
}

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

    fun containsValidToken(request: Request): Boolean {
        val firstValidToken = jwtTokenValidationHandler.getValidatedTokens(request.toNavRequest()).firstValidToken
        return firstValidToken.isPresent
    }

    override fun firstValidToken(request: Request): Optional<JwtToken> {
        lateinit var result: Optional<JwtToken>
        result = jwtTokenValidationHandler.getValidatedTokens(request.toNavRequest()).firstValidToken
        if (!result.isPresent) {
            File("/tmp/novalidtoken").writeText(request.toMessage())
        }
        return result
    }
}

fun JwtToken.isFromSalesforce(): Boolean {
    return try {
        this.jwtTokenClaims.getStringClaim("azp_name").split(":")[2] == "salesforce"
    } catch (e: Exception) {
        log.error { "Failed to parse azp_name claim to perceive source app - will set modified by Salesforce to false" }
        false
    }
}
