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
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.batchUpsert
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

    private fun hikariConfig(): HikariConfig = HikariConfig().apply {
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

    fun create(dropFirst: Boolean = false) {
        transaction {
            if (dropFirst) {
                log.info { "Dropping table Henvendelser" }
                val dropStatement =
                    TransactionManager.current().connection.prepareStatement("DROP TABLE henvendelser", false)
                dropStatement.executeUpdate()
                log.info { "Drop performed of Henvendelser" }
            }

            log.info { "Creating table Henvendelser" }
            SchemaUtils.create(Henvendelser)
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

    fun createKjedeToAktorCache(dropFirst: Boolean = false) {
        transaction {
            if (dropFirst) {
                log.info { "Dropping table KjedeToAktor" }
                val dropStatement =
                    TransactionManager.current().connection.prepareStatement("DROP TABLE kjedetoaktor", false)
                dropStatement.executeUpdate()
                log.info { "Drop performed KjedeToAktor" }
            }

            log.info { "Creating table KjedeToAktor" }
            SchemaUtils.create(KjedeToAktor)
        }
    }

    fun upsertHenvendelse(kjedeId: String, aktorId: String, fnr: String, json: String, updateBySF: Boolean = false): HenvendelseRecord? {
        return transaction {
            Henvendelser.upsert(
                keys = arrayOf(Henvendelser.kjedeId) // Perform update if there is a conflict here
            ) {
                it[Henvendelser.kjedeId] = kjedeId
                it[Henvendelser.aktorId] = aktorId
                it[Henvendelser.fnr] = fnr
                it[Henvendelser.json] = json
                it[lastModified] = LocalDateTime.now()
                it[lastModifiedBySF] = updateBySF
            }
        }.resultedValues?.firstOrNull()?.toHenvendelseRecord()
    }

    fun henteHenvendelse(kjedeId: String): List<HenvendelseRecord> = transaction {
        Henvendelser.selectAll().andWhere { Henvendelser.kjedeId eq kjedeId }
            .toList()
            .map { it.toHenvendelseRecord() }
    }

    fun henteHenvendelserByAktorId(aktorId: String): List<HenvendelseRecord> =
        transaction {
            Henvendelser.selectAll().andWhere { Henvendelser.aktorId eq aktorId }
                .toList()
                .map { it.toHenvendelseRecord() }
        }

    fun view(page: Long, pageSize: Int): List<HenvendelseRecord> = transaction {
        Henvendelser.selectAll().limit(pageSize, (page - 1) * pageSize)
            .toList()
            .map { it.toHenvendelseRecord() }
    }

    fun count(): Long = transaction {
        Henvendelser.selectAll().count()
    }

    fun cacheGet(aktorId: String): String? = transaction {
        Henvendelseliste
            .selectAll().where { Henvendelseliste.aktorId eq aktorId }
            .filter { it[Henvendelseliste.expiresAt]?.isAfter(LocalDateTime.now()) ?: true } // Ignore expired records
            .map { it[Henvendelseliste.json].toString() }
            .firstOrNull()
    }

    fun cachePut(aktorId: String, value: String, ttlInSeconds: Int?): Boolean {
        try {
            val expiresAt = ttlInSeconds?.let { LocalDateTime.now().plusSeconds(it.toLong()) }

            transaction {
                Henvendelseliste.upsert(
                    keys = arrayOf(Henvendelseliste.aktorId) // Perform update if there is a conflict here
                ) {
                    it[Henvendelseliste.aktorId] = aktorId
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

    fun deleteCache(aktorId: String) = transaction {
        Henvendelseliste.deleteWhere { Henvendelseliste.aktorId eq aktorId }
    }

    fun deleteAllRows(): Int = transaction {
        Henvendelseliste.deleteAll()
    }

    fun deleteExpiredRows(): Int = transaction {
        Henvendelseliste.deleteWhere {
            Henvendelseliste.expiresAt lessEq LocalDateTime.now()
        }
    }

    fun cacheCountRows(): Long = transaction {
        Henvendelseliste.selectAll().count()
    }

    var initialCheckPassed = false

    fun cacheReady(): Boolean {
        return if (initialCheckPassed) {
            true
        } else {
            try {
                val queryTime = measureTimeMillis {
                    cacheGet("dummy")
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

    fun kjedeToAktorIdPut(kjedeId: String, aktorId: String, ttlInSeconds: Int?): Boolean {
        try {
            val expiresAt = ttlInSeconds?.let { LocalDateTime.now().plusSeconds(it.toLong()) }

            transaction {
                Henvendelseliste.upsert(
                    keys = arrayOf(KjedeToAktor.kjedeId) // Perform update if there is a conflict here
                ) {
                    it[KjedeToAktor.kjedeId] = kjedeId
                    it[KjedeToAktor.aktorId] = aktorId
                }
            }
            return true
        } catch (e: Exception) {
            log.error { e.stackTraceToString() }
            return false
        }
    }

    fun bulkKjedeToAktorIdPut(associations: Set<Pair<String, String>>, ttlInSeconds: Int?): Boolean {
        return try {
            val expiresAt = ttlInSeconds?.let { LocalDateTime.now().plusSeconds(it.toLong()) }

            transaction {
                // Bulk insert with upsert behavior
                Henvendelseliste.batchUpsert(associations, keys = arrayOf(KjedeToAktor.kjedeId)) { (kjedeId, aktorId) ->
                    this[KjedeToAktor.kjedeId] = kjedeId
                    this[KjedeToAktor.aktorId] = aktorId
                }
            }
            true
        } catch (e: Exception) {
            log.error { e.stackTraceToString() }
            false
        }
    }

    fun kjedeToAktorIdGet(kjedeId: String): String? = transaction {
        Henvendelseliste
            .selectAll().where { KjedeToAktor.kjedeId eq kjedeId }
            .filter { it[KjedeToAktor.expiresAt]?.isAfter(LocalDateTime.now()) ?: true } // Ignore expired records
            .map { it[KjedeToAktor.aktorId].toString() }
            .firstOrNull()
    }

    fun kjedeToAktorIdDeleteAllRows(): Int = transaction {
        KjedeToAktor.deleteAll()
    }

    fun kjedeToAktorIdDeleteExpiredRows(): Int = transaction {
        KjedeToAktor.deleteWhere {
            KjedeToAktor.expiresAt lessEq LocalDateTime.now()
        }
    }
}
