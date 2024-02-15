package no.nav.sf.henvendelse.db.token

import com.nimbusds.jwt.JWTClaimsSet
import io.mockk.every
import io.mockk.mockk
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class JwtTokenTest {

    val mockToken = mockk<JwtToken>()

    @Test
    fun `token with salesforce as azp_name app should be correctly identified as from salesforce`() {
        val jwtTokenClaims = JwtTokenClaims(JWTClaimsSet.Builder().claim("azp_name", "dev-external:teamcrm:salesforce").build())

        every { mockToken.jwtTokenClaims } returns jwtTokenClaims

        Assertions.assertEquals(true, mockToken.isFromSalesforce())
    }

    @Test
    fun `token with platypus as azp_name app should be correctly identified as not from salesforce`() {
        val jwtTokenClaims = JwtTokenClaims(JWTClaimsSet.Builder().claim("azp_name", "dev-external:teamcrm:platypus").build())

        every { mockToken.jwtTokenClaims } returns jwtTokenClaims

        Assertions.assertEquals(false, mockToken.isFromSalesforce())
    }
}
