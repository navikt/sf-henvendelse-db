package no.nav.sf.henvendelse.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Database.Companion.connect
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.io.File
import java.time.LocalDateTime

private val log = KotlinLogging.logger { }

val postgresDatabase = PostgresDatabase()

class PostgresDatabase {
    private val log = KotlinLogging.logger { }

    private val dbUrl = System.getenv("NAIS_DATABASE_SF_HENVENDELSE_DB_SF_HENVENDELSE_DEV_URL")
    private val host = System.getenv("NAIS_DATABASE_SF_HENVENDELSE_DB_SF_HENVENDELSE_DEV_HOST")
    private val port = System.getenv("NAIS_DATABASE_SF_HENVENDELSE_DB_SF_HENVENDELSE_DEV_PORT")
    private val name = System.getenv("NAIS_DATABASE_SF_HENVENDELSE_DB_SF_HENVENDELSE_DEV_DATABASE")
    private val user = System.getenv("NAIS_DATABASE_SF_HENVENDELSE_DB_SF_HENVENDELSE_DEV_USERNAME")
    private val userpassword = System.getenv("NAIS_DATABASE_SF_HENVENDELSE_DB_SF_HENVENDELSE_DEV_PASSWORD")

    // val dataSource = HikariDataSource(hikariConfig())
    // Note: exposed Database connect prepares for connections but does not actually open connections
    // That is handled via transaction {} ensuring connections are opened and closed properly
    val database = Database.connect(HikariDataSource(hikariConfig()))

    private fun hikariConfig(): HikariConfig {
        return HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://localhost:$port/$name"
            driverClassName = "org.postgresql.Driver"
            addDataSourceProperty("serverName", host)
            addDataSourceProperty("port", port)
            addDataSourceProperty("databaseName", name)
            addDataSourceProperty("user", user)
            addDataSourceProperty("password", userpassword)
            minimumIdle = 1
            maxLifetime = 26000
            maximumPoolSize = 10
            connectionTimeout = 250
            idleTimeout = 10001
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }
    }

    fun create(dropFirst: Boolean = false) {
        transaction {
            if (dropFirst) {
                log.info { "Dropping table Henvendelser" }
                val dropStatement =
                    TransactionManager.current().connection.prepareStatement("DROP TABLE henvendelser", false)
                dropStatement.executeUpdate()
            }

            log.info { "Creating table Henvendelser" }
            SchemaUtils.create(Henvendelser)
        }
        log.info { "drop and create done" }
    }

    fun upsertHenvendelse(id: String, aktorid: String, json: String, updateBySF: Boolean = true): HenvendelseRecord? {
        return transaction {
            Henvendelser.upsert(
                keys = arrayOf(Henvendelser.id) // Perform update if there is a conflict here
            ) {
                it[Henvendelser.id] = id
                it[Henvendelser.aktorid] = aktorid
                it[Henvendelser.json] = json
                it[Henvendelser.lastModified] = LocalDateTime.now()
                it[Henvendelser.lastModifiedBySF] = updateBySF
            }
        }.resultedValues?.firstOrNull()?.toHenvendelseRecord()
    }

    fun henteHenvendelse(id: String) {
        transaction {
            val query = Henvendelser.selectAll().andWhere { Henvendelser.id eq id }

            val resultRow = query.toList().map { ResultRow::toHenvendelseRecord }

            log.info { "Latest hente result: $resultRow" }
            File("/tmp/latesthenteresult").writeText(resultRow.toString())
            log.info { "hente by id returns ${resultRow.size} entries" }
        }
    }

    fun henteHenvendelserByAktorid(aktorid: String) {
        transaction {
            val query = Henvendelser.selectAll().andWhere { Henvendelser.aktorid eq aktorid }

            val resultRow = query.toList().map(ResultRow::toHenvendelseRecord)

            log.info { "Latest hente aktorid result: $resultRow" }
            File("/tmp/latesthenteaktoridresult").writeText(resultRow.toString())
            log.info { "hente by aktorid returns ${resultRow.size} entries" }
        }
    }
}
