package no.nav.sf.henvendelse.db.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sf.henvendelse.db.config_CONTEXT
import no.nav.sf.henvendelse.db.env
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis

const val NAIS_DB_PREFIX = "NAIS_DATABASE_SF_HENVENDELSE_DB_SF_HENVENDELSE_"

class PostgresDatabase {
    private val log = KotlinLogging.logger { }

    private val context = env(config_CONTEXT)

    private val dbUrl = env("$NAIS_DB_PREFIX${context}_URL")
    private val dbHost = env("$NAIS_DB_PREFIX${context}_HOST")
    private val dbPort = env("$NAIS_DB_PREFIX${context}_PORT")
    private val dbName = env("$NAIS_DB_PREFIX${context}_DATABASE")
    private val dbUsername = env("$NAIS_DB_PREFIX${context}_USERNAME")
    private val dbPassword = env("$NAIS_DB_PREFIX${context}_PASSWORD")

    // Note: exposed Database connect prepares for connections but does not actually open connections
    // That is handled via transaction {} ensuring connections are opened and closed properly
    val database = Database.connect(HikariDataSource(hikariConfig()))

    private fun hikariConfig(): HikariConfig =
        HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://localhost:$dbPort/$dbName" // This is where the cloud db proxy is located in the pod
            driverClassName = "org.postgresql.Driver"
            addDataSourceProperty("serverName", dbHost)
            addDataSourceProperty("port", dbPort)
            addDataSourceProperty("databaseName", dbName)
            addDataSourceProperty("user", dbUsername)
            addDataSourceProperty("password", dbPassword)
            minimumIdle = 1
            maxLifetime = 26000
            maximumPoolSize = 10
            connectionTimeout = 250
            idleTimeout = 10000
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            // This sets the transaction isolation level to READ COMMITTED, ensuring that each query sees only committed data.
            // It prevents dirty reads while allowing non-repeatable reads and phantom reads, reducing contention compared to SERIALIZABLE.
            // This is useful for avoiding serialization conflicts in high-concurrency delete operations.
        }

    fun purgeOld() {
        transaction {
            val dropStatement =
                TransactionManager.current().connection.prepareStatement("DROP TABLE henvendelser", false)
            dropStatement.executeUpdate()
            val dropStatement2 =
                TransactionManager.current().connection.prepareStatement("DROP TABLE kjedetoaktor", false)
            dropStatement2.executeUpdate()
        }
    }

    fun createCache(dropFirst: Boolean = false) {
        transaction {
            if (dropFirst) {
                log.info { "Dropping table Henvendelseliste" }
                val dropStatement =
                    TransactionManager.current().connection.prepareStatement("DROP TABLE henvendelseliste", false)
                dropStatement.executeUpdate()
                log.info { "Drop performed Henvendelseliste" }
            }

            log.info { "Creating table Henvendelseliste" }
            SchemaUtils.create(Henvendelseliste)
        }
    }

    data class CachedValue(
        val json: String,
        val expiresAt: LocalDateTime?,
    )

    fun cacheGet(
        aktorId: String,
        page: Int,
        pageSize: Int,
    ): CachedValue? =
        transaction {
            Henvendelseliste
                .selectAll()
                .where {
                    run {
                        (Henvendelseliste.aktorId eq aktorId) and
                            (Henvendelseliste.page eq page) and
                            (Henvendelseliste.pageSize eq pageSize)
                    }
                }.limit(1) // small optimization
                .map {
                    CachedValue(
                        json = it[Henvendelseliste.json],
                        expiresAt = it[Henvendelseliste.expiresAt],
                    )
                }.firstOrNull()
        }

    fun cachePut(
        aktorId: String,
        page: Int,
        pageSize: Int,
        value: String,
        ttlInSeconds: Int?,
    ): Boolean {
        try {
            val expiresAt = ttlInSeconds?.let { LocalDateTime.now().plusSeconds(it.toLong()) }

            transaction {
                Henvendelseliste.upsert(
                    keys =
                        arrayOf(
                            Henvendelseliste.aktorId,
                            Henvendelseliste.page,
                            Henvendelseliste.pageSize,
                        ),
                ) {
                    it[Henvendelseliste.aktorId] = aktorId
                    it[Henvendelseliste.page] = page
                    it[Henvendelseliste.pageSize] = pageSize
                    it[Henvendelseliste.json] = value
                    it[Henvendelseliste.expiresAt] = expiresAt
                }
            }
            return true
        } catch (e: Exception) {
            log.error { e.stackTraceToString() }
            return false
        }
    }

    fun deleteCache(aktorId: String) =
        transaction {
            Henvendelseliste.deleteWhere { Henvendelseliste.aktorId eq aktorId }
        }

    fun deleteAllRows(): Int =
        transaction {
            Henvendelseliste.deleteAll()
        }

    fun deleteExpiredRows(): Int =
        transaction {
            Henvendelseliste.deleteWhere {
                Henvendelseliste.expiresAt lessEq LocalDateTime.now()
            }
        }

    fun cacheCountRows(): Long =
        transaction {
            Henvendelseliste.selectAll().count()
        }

    var initialCheckPassed = false

    fun cacheReady(): Boolean =
        if (initialCheckPassed) {
            true
        } else {
            try {
                val queryTime =
                    measureTimeMillis {
                        cacheGet("dummy", 1, 50)
                    }
                log.info { "Initial cache check query time $queryTime ms" }
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
