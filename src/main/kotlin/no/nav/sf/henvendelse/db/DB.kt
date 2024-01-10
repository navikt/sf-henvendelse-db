package no.nav.sf.henvendelse.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database.Companion.connect
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.javatime.datetime
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

    // val dbName = System.getenv("DB_NAME")
    // val dbUrl = System.getenv("DB_URL")
    // private val vaultMountPath = System.getenv("MOUNT_PATH")
    // private val adminUsername = "dbName-admin"
    // private val username = "dbName-user"
    private val dbUrl = System.getenv("NAIS_DATABASE_SF_HENVENDELSE_DB_SF_HENVENDELSE_DEV_URL")!!

    private val host = System.getenv("NAIS_DATABASE_SF_HENVENDELSE_DB_SF_HENVENDELSE_DEV_HOST")!!
    private val port = System.getenv("NAIS_DATABASE_SF_HENVENDELSE_DB_SF_HENVENDELSE_DEV_PORT")!!
    private val name = System.getenv("NAIS_DATABASE_SF_HENVENDELSE_DB_SF_HENVENDELSE_DEV_DATABASE")!!
    private val user = System.getenv("NAIS_DATABASE_SF_HENVENDELSE_DB_SF_HENVENDELSE_DEV_USERNAME")!!
    private val userpassword = System.getenv("NAIS_DATABASE_SF_HENVENDELSE_DB_SF_HENVENDELSE_DEV_PASSWORD")!!

    /**
     * hostname	NAIS_DATABASE_MYAPP_MYDB_HOST	127.0.0.1
     port	NAIS_DATABASE_MYAPP_MYDB_PORT	5432
     database name	NAIS_DATABASE_MYAPP_MYDB_DATABASE	.spec.gcp.sqlInstances[].databases[].name
     database user	NAIS_DATABASE_MYAPP_MYDB_USERNAME	.spec.gcp.sqlInstances[].name
     database password	NAIS_DATABASE_MYAPP_MYDB_PASSWORD	(randomly generated)
     database url with credentials	NAIS_DATABASE_MYAPP_MYDB_URL	postgres://username:password@127.0.0.1:5432/mydb
     */

    // val dataSource: HikariDataSource = dataSource()
    // val connection: Connection get() = dataSource.connection.apply { autoCommit = false }

    // private fun initSql(role: String) = """SET ROLE "$role""""

    // HikariCPVaultUtil fetches and refreshed credentials
    /*
    private fun dataSource(admin: Boolean = false): HikariDataSource =
        HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
            hikariConfig(),
            vaultMountPath,
            if (admin) adminUsername else username
        )

     */

    private val dataSource = HikariDataSource(hikariConfig())

    private fun hikariConfig(): HikariConfig {
        return HikariConfig().apply {
            // jdbcUrl = dbUrl.replace("postgres", "jdbc:postgresql")
            jdbcUrl = "jdbc:postgresql://"
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
        log.info { "Attempting connect to datasource" }
        connect(dataSource)
        log.info { "Connection established" }
        transaction {
            // val statement = TransactionManager.current().connection.prepareStatement(initSql(adminUsername), false)
            // statement.executeUpdate()

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
}

/**
 * Max limit of postgres varchar of 1 GB (1073741824 chars)
 * Note that postgres do not pre-allocate space, so a large varchar will not affect footprint in db
 */
const val MAX_LIMIT_VARCHAR = 1073741824

object Henvendelser : Table() {
    val id = varchar("id", 18).uniqueIndex()
    val aktorid = varchar("aktorid", 20).index()
    val json = varchar("json", MAX_LIMIT_VARCHAR)

    // Record metadata
    val lastModified = datetime("last_modified").index()
    val lastModifiedBySF = bool("last_modified_by_sf")
}

fun upsertHenvendelse(id: String, aktorid: String, json: String): Int {
    return transaction {
        Henvendelser.upsert(
            keys = arrayOf(Henvendelser.id) // Perform update if there is a conflict here
        ) {
            it[Henvendelser.id] = id
            it[Henvendelser.aktorid] = aktorid
            it[Henvendelser.json] = json
            it[Henvendelser.lastModified] = LocalDateTime.now()
            it[Henvendelser.lastModifiedBySF] = true
        }
    }.insertedCount
}

fun henteHenvendelse(id: String) {
    transaction {
        val query = Henvendelser.selectAll().andWhere { Henvendelser.id eq id }

        val resultRow = query.toList()
        File("/tmp/latesthenteresult").writeText(resultRow.toString())
        log.info { "henteArchive returns ${resultRow.size} entries" }
    }
}
