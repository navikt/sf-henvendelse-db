package no.nav.sf.henvendelse.db.token

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.StaticCredentialsProvider
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.sf.henvendelse.db.Metrics
import no.nav.sf.henvendelse.db.env
import no.nav.sf.henvendelse.db.env_VALKEY_PASSWORD_HENVENDELSER
import no.nav.sf.henvendelse.db.env_VALKEY_URI_HENVENDELSER
import no.nav.sf.henvendelse.db.env_VALKEY_USERNAME_HENVENDELSER
import kotlin.system.measureTimeMillis

object Valkey {

    private val log = KotlinLogging.logger { }

    var initialCheckPassed = false

    fun isReady(): Boolean {
        return if (initialCheckPassed) {
            true
        } else {
            var response: Long
            val queryTime = measureTimeMillis {
                response = dbSize()
            }
            log.info { "Initial check query time $queryTime ms (got count $response)" }
            if (queryTime < 100) {
                initialCheckPassed = true
            }
            false
        }
    }

    fun connectToRedis(): RedisCommands<String, String> {
        val staticCredentialsProvider = StaticCredentialsProvider(
            env(env_VALKEY_USERNAME_HENVENDELSER),
            env(env_VALKEY_PASSWORD_HENVENDELSER).toCharArray()
        )

        val redisURI = RedisURI.create(env(env_VALKEY_URI_HENVENDELSER)).apply {
            this.credentialsProvider = staticCredentialsProvider
        }

        val client: RedisClient = RedisClient.create(redisURI)
        val connection: StatefulRedisConnection<String, String> = client.connect()
        return connection.sync()
    }

    val commands = connectToRedis()

    fun dbSize(): Long = commands.dbsize()

    tailrec fun cacheQueryLoop() {
        runBlocking { delay(60000) } // 1 min
        try {
            Metrics.cacheSize.set(Valkey.dbSize().toDouble())
        } catch (e: Exception) {
            log.warn { "Failed to query Redis dbSize" }
        }
        runBlocking { delay(840000) } // 14 min
        cacheQueryLoop()
    }
}
