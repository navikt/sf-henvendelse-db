package no.nav.sf.henvendelse.db.token

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.sf.henvendelse.db.env
import no.nav.sf.henvendelse.db.env_VALKEY_PASSWORD_HENVENDELSER
import no.nav.sf.henvendelse.db.env_VALKEY_USERNAME_HENVENDELSER
import org.redisson.Redisson
import org.redisson.api.RMapCache
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import kotlin.system.measureTimeMillis

const val cacheName_HENVENDELSELISTE = "henvendelseliste"

object Valkey {

    private val log = KotlinLogging.logger { }

    var initialCheckPassed = false

    fun isReady(): Boolean {
        return if (initialCheckPassed) {
            true
        } else {
            try {
                val redissonClient = connectToRedisson()
                log.info { "Past connection" }
                var response: Long
                val queryTime = measureTimeMillis {
                    val mapCache: RMapCache<String, String> = redissonClient.getMapCache(cacheName_HENVENDELSELISTE)
                }
                log.info { "Initial check query time $queryTime ms" }
                if (queryTime < 100) {
                    initialCheckPassed = true
                }
                false
            } catch (e: java.lang.Exception) {
                log.error { e.printStackTrace() }
                false
            }
        }
    }

    fun connectToRedisson(): RedissonClient {
        /*
        val config = Config()

        val uri = env(env_VALKEY_URI_HENVENDELSER) // Example: "valkeys://valkey-teamnks-henvendelser-nav-dev.k.aivencloud.com:26483"
        val username = env(env_VALKEY_USERNAME_HENVENDELSER)
        val password = env(env_VALKEY_PASSWORD_HENVENDELSER)

        log.info { "Attempt connection to $uri, username $username, password length ${password.length}" }

        val modifiedUri = uri.replace("valkeys://", "valkeys://$username:$password@")

        File("/tmp/modifiedURI").writeText(modifiedUri)

        config.useSingleServer().apply {
            address = modifiedUri
            // username = env(env_VALKEY_USERNAME_HENVENDELSER)
            // password = env(env_VALKEY_PASSWORD_HENVENDELSER)
            // sslVerificationMode = SslVerificationMode.NONE // Disable strict hostname verification
            // sslProvider = SslProvider.JDK
        }

         */
        val config = Config()
        config.useSingleServer().apply {
            // Manually handle the connection string and SSL
            address = "rediss://valkey-teamnks-henvendelser-nav-dev.k.aivencloud.com:26483"
            username = env(env_VALKEY_USERNAME_HENVENDELSER)
            password = env(env_VALKEY_PASSWORD_HENVENDELSER)
            sslProvider = org.redisson.config.SslProvider.JDK
            sslVerificationMode = org.redisson.config.SslVerificationMode.NONE // Optional, based on your server's SSL setup
        }
        return Redisson.create(config)
    }

    // val redissonClient = connectToRedisson()

    tailrec fun cacheQueryLoop() {
        runBlocking { delay(60000) } // 1 min
        try {
            // Metrics.cacheSize.set(Valkey.dbSize().toDouble())
        } catch (e: Exception) {

            log.warn { "Failed to query Redis dbSize" }
        }
        runBlocking { delay(840000) } // 14 min
        cacheQueryLoop()
    }
}
