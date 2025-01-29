package no.nav.sf.henvendelse.db.token

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.sf.henvendelse.db.env
import no.nav.sf.henvendelse.db.env_VALKEY_PASSWORD_HENVENDELSER
import no.nav.sf.henvendelse.db.env_VALKEY_URI_HENVENDELSER
import no.nav.sf.henvendelse.db.env_VALKEY_USERNAME_HENVENDELSER
import org.redisson.Redisson
import org.redisson.api.RMapCache
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.redisson.config.SslProvider
import org.redisson.config.SslVerificationMode
import kotlin.system.measureTimeMillis

const val cacheName_HENVENDELSELISTE = "henvendelseliste"

object Valkey {

    private val log = KotlinLogging.logger { }

    var initialCheckPassed = false

    fun isReady(): Boolean {
        return if (initialCheckPassed) {
            true
        } else {
            var response: Long
            val queryTime = measureTimeMillis {
                val mapCache: RMapCache<String, String> = redissonClient.getMapCache(cacheName_HENVENDELSELISTE)
            }
            log.info { "Initial check query time $queryTime ms" }
            if (queryTime < 100) {
                initialCheckPassed = true
            }
            false
        }
    }

    fun connectToRedisson(): RedissonClient {
        val config = Config()

        log.info {
            "Attempt connection to ${env(env_VALKEY_URI_HENVENDELSER)}, username ${env(env_VALKEY_USERNAME_HENVENDELSER)}, password length ${env(
                env_VALKEY_PASSWORD_HENVENDELSER
            ).length}"
        }

        config.useSingleServer().apply {
            address = env(env_VALKEY_URI_HENVENDELSER) // Support for multiple nodes
            username = env(env_VALKEY_USERNAME_HENVENDELSER)
            password = env(env_VALKEY_PASSWORD_HENVENDELSER)
            sslVerificationMode = SslVerificationMode.NONE // Disable strict hostname verification
            sslProvider = SslProvider.JDK
        }

        return Redisson.create(config)
    }

    val redissonClient = connectToRedisson()

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
