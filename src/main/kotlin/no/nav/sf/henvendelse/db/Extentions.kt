package no.nav.sf.henvendelse.db

import no.nav.security.token.support.core.http.HttpRequest
import org.http4k.core.Request

fun Request.toNavRequest(): HttpRequest {
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
