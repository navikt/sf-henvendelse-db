package no.nav.sf.henvendelse.db.token

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import mu.KotlinLogging
import no.nav.sf.henvendelse.db.env
import no.nav.sf.henvendelse.db.env_VALKEY_HOST_HENVENDELSELISTE
import no.nav.sf.henvendelse.db.env_VALKEY_PASSWORD_HENVENDELSELISTE
import no.nav.sf.henvendelse.db.env_VALKEY_PORT_HENVENDELSELISTE
import no.nav.sf.henvendelse.db.env_VALKEY_USERNAME_HENVENDELSELISTE
import java.io.File
import kotlin.system.measureTimeMillis

object Valkey {

    private const val useTTL: Boolean = true

    private const val TTLInSeconds: Long = 60 * 60 * 48 // 48h

    private val log = KotlinLogging.logger { }

    var initialCheckPassed = false

    fun isReady(): Boolean {
        return if (initialCheckPassed) {
            true
        } else {
            try {
                var response: Long
                val queryTime = measureTimeMillis {
                    commands.get("dummy")
                    val info = commands.info("memory")
                    File("/tmp/infomem").writeText(info)
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

    fun connect(): RedisCommands<String, String> {
        val redisURI = RedisURI.Builder.redis(env(env_VALKEY_HOST_HENVENDELSELISTE), env(env_VALKEY_PORT_HENVENDELSELISTE).toInt())
            .withSsl(true)
            .withAuthentication(env(env_VALKEY_USERNAME_HENVENDELSELISTE), env(env_VALKEY_PASSWORD_HENVENDELSELISTE).toCharArray())
            .build()

        File("/tmp/uri").writeText(redisURI.toURI().toString())

        val client: RedisClient = RedisClient.create(redisURI)

        val connection: StatefulRedisConnection<String, String> = client.connect()
        return connection.sync()
    }

    fun get(key: String): String? = commands.get(key)

    fun put(key: String, value: String?) {
        if (useTTL) {
            commands.setex(key, TTLInSeconds, value)
        } else {
            commands.set(key, value)
        }
    }

    fun clearCache(key: String) {
        val deletedCount = commands.del(key)
        if (deletedCount > 0) {
            log.info { "Successfully deleted key: $key" }
        } else {
            log.warn { "Delete - key not found: $key" }
        }
    }

    fun flushDb() { // Clear the whole cache
        commands.flushdb()
    }

    val commands = connect()
}
