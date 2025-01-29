package no.nav.sf.henvendelse.db.token

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.StaticCredentialsProvider
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import mu.KotlinLogging
import no.nav.sf.henvendelse.db.env
import no.nav.sf.henvendelse.db.env_REDIS_URI_HENVENDELSELISTE
import no.nav.sf.henvendelse.db.env_VALKEY_HOST_HENVENDELSELISTE
import no.nav.sf.henvendelse.db.env_VALKEY_PASSWORD_HENVENDELSELISTE
import no.nav.sf.henvendelse.db.env_VALKEY_PORT_HENVENDELSELISTE
import no.nav.sf.henvendelse.db.env_VALKEY_USERNAME_HENVENDELSELISTE
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import java.io.File
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
                val redissonClient = connectViaLettuce()
                log.info { "Past connection" }
                var response: Long
                val queryTime = measureTimeMillis {
                    // val mapCache: RMapCache<String, String> = redissonClient.getMapCache(cacheName_HENVENDELSELISTE)
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

    fun connectViaLettuce(): RedisCommands<String, String> {
        val staticCredentialsProvider = StaticCredentialsProvider(
            env(env_VALKEY_USERNAME_HENVENDELSELISTE),
            env(env_VALKEY_PASSWORD_HENVENDELSELISTE).toCharArray()
        )

        val redisURI = RedisURI.Builder.redis(env(env_VALKEY_HOST_HENVENDELSELISTE), env(env_VALKEY_PORT_HENVENDELSELISTE).toInt())
            .withSsl(true)
            .withAuthentication(env(env_VALKEY_USERNAME_HENVENDELSELISTE), env(env_VALKEY_PASSWORD_HENVENDELSELISTE).toCharArray())
            .build()

        // redisURI.credentialsProvider = staticCredentialsProvider
        // val redisURI = RedisURI.create(env(env_REDIS_URI_HENVENDELSER)).apply {
        //    this.credentialsProvider = staticCredentialsProvider
        // }

        File("/tmp/uri").writeText(redisURI.toURI().toString())

        val client: RedisClient = RedisClient.create(redisURI)

        val connection: StatefulRedisConnection<String, String> = client.connect()
        return connection.sync()
    }

    fun connectViaRedisson(): RedissonClient {
        val config = org.redisson.config.Config()
        // Example: "valkeys://valkey-teamnks-henvendelser-nav-dev.k.aivencloud.com:26483"

        val uri = env(env_REDIS_URI_HENVENDELSELISTE)
        val usernameEnv = env(env_VALKEY_USERNAME_HENVENDELSELISTE)
        val passwordEnv = env(env_VALKEY_PASSWORD_HENVENDELSELISTE)

        config.useSingleServer().apply {
            address = uri
            username = usernameEnv
            password = passwordEnv
            // sslVerificationMode = SslVerificationMode.NONE // Disable strict hostname verification
            // sslProvider = SslProvider.JDK
        }
        return Redisson.create(config)
    }
}
