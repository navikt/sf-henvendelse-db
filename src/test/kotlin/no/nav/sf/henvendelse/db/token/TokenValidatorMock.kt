package no.nav.sf.henvendelse.db.token

import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.sf.henvendelse.api.proxy.token.TokenValidator
import org.http4k.core.Request
import java.util.Optional

class TokenValidatorMock : TokenValidator {
    override fun firstValidToken(request: Request): Optional<JwtToken> {
        return Optional.empty()
    }
}
