package no.nav.sf.henvendelse.db.token

import mu.KotlinLogging
import no.nav.security.token.support.core.jwt.JwtToken

private val log = KotlinLogging.logger { }

fun JwtToken.isFromSalesforce(): Boolean {
    return try {
        this.jwtTokenClaims.getStringClaim("azp_name").split(":")[2] == "salesforce"
    } catch (e: Exception) {
        log.error { "Failed to parse azp_name claim to perceive source app - will set modified by Salesforce to false" }
        false
    }
}
