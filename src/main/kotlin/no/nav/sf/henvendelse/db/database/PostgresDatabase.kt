package no.nav.sf.henvendelse.db.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import no.nav.sf.henvendelse.db.env_CONTEXT
import no.nav.sf.henvendelse.db.naisDatabasePrefix
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDateTime

class PostgresDatabase {
    private val log = KotlinLogging.logger { }

    private val context = System.getenv(env_CONTEXT)

    private val dbUrl = System.getenv("${naisDatabasePrefix}_${context}_URL")
    private val dbHost = System.getenv("${naisDatabasePrefix}_${context}_HOST")
    private val dbPort = System.getenv("${naisDatabasePrefix}_${context}_PORT")
    private val dbName = System.getenv("${naisDatabasePrefix}_${context}_DATABASE")
    private val dbUsername = System.getenv("${naisDatabasePrefix}_${context}_USERNAME")
    private val dbPassword = System.getenv("${naisDatabasePrefix}_${context}_PASSWORD")

    // val dataSource = HikariDataSource(hikariConfig())
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
        idleTimeout = 10001
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
    }

    fun create(dropFirst: Boolean = false) {
        transaction {
            if (dropFirst) {
                log.info { "Dropping table Henvendelser" }
                val dropStatement =
                    TransactionManager.current().connection.prepareStatement("DROP TABLE henvendelser", false)
                dropStatement.executeUpdate()
                log.info { "Drop performed" }
            }

            log.info { "Creating table Henvendelser" }
            SchemaUtils.create(Henvendelser)
            log.info { "Create done" }
        }
    }

    fun upsertHenvendelse(kjedeId: String, aktorId: String, json: String, updateBySF: Boolean = false): HenvendelseRecord? {
        return transaction {
            Henvendelser.upsert(
                keys = arrayOf(Henvendelser.kjedeId) // Perform update if there is a conflict here
            ) {
                it[Henvendelser.kjedeId] = kjedeId
                it[Henvendelser.aktorId] = aktorId
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
}