package navikt.appsec.securitychampionapp.testsupport

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * One PostgreSQL container per test JVM. The schema is migrated once; [reset] restores the
 * freshly migrated state (including rows seeded by migrations) in a single round trip.
 * Tests that change the schema or need older migrations use [isolated] instead.
 */
object PostgresTestDatabase {
    private const val USER = "test"
    private const val PASSWORD = "test"
    private const val SHARED = "shared"
    private const val PRISTINE = "pristine"
    private val isolatedCounter = AtomicInteger()

    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("postgres")
            withUsername(USER)
            withPassword(PASSWORD)
            start()
        }
    }

    private val initialized: Boolean by lazy {
        admin("CREATE DATABASE $PRISTINE")
        migrate(urlFor(PRISTINE))
        admin("CREATE DATABASE $SHARED TEMPLATE $PRISTINE")
        connection(urlFor(SHARED)) { it.createStatement().execute(RESET_FUNCTION) }
        true
    }

    fun jdbcUrl(database: String): String = urlFor(database)

    fun dataSource(maxPoolSize: Int = 4): HikariDataSource {
        check(initialized)
        return pool(urlFor(SHARED), maxPoolSize)
    }

    fun reset() {
        check(initialized)
        connection(urlFor(SHARED)) { it.createStatement().execute("SELECT seed_snapshot.reset()") }
    }

    /** A private database cloned from the migrated schema; run Flyway clean to start from scratch. */
    fun isolated(maxPoolSize: Int = 4): IsolatedDatabase {
        check(initialized)
        val name = "isolated_${isolatedCounter.incrementAndGet()}"
        admin("CREATE DATABASE $name TEMPLATE $PRISTINE")
        return IsolatedDatabase(name, urlFor(name), pool(urlFor(name), maxPoolSize))
    }

    class IsolatedDatabase(
        private val name: String,
        val jdbcUrl: String,
        val dataSource: HikariDataSource,
    ) : AutoCloseable {
        val username: String = USER
        val password: String = PASSWORD

        fun flyway(): Flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load()

        override fun close() {
            dataSource.close()
            admin("DROP DATABASE IF EXISTS $name WITH (FORCE)")
        }
    }

    private fun urlFor(database: String): String = container.jdbcUrl.replaceAfterLast('/', database)

    private fun pool(url: String, maxPoolSize: Int) = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = url
            username = USER
            password = PASSWORD
            maximumPoolSize = maxPoolSize
            minimumIdle = 1
        },
    )

    private fun migrate(url: String) {
        Flyway.configure()
            .dataSource(url, USER, PASSWORD)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    private fun admin(sql: String) = connection(urlFor("postgres")) { it.createStatement().execute(sql) }

    private fun <T> connection(url: String, block: (Connection) -> T): T =
        DriverManager.getConnection(url, USER, PASSWORD).use(block)

    private val RESET_FUNCTION = """
        CREATE SCHEMA seed_snapshot;
        DO ${'$'}${'$'}
        DECLARE t text;
        BEGIN
            FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history' LOOP
                IF (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM public.%I', t), false, true, '')))[1]::text::int > 0 THEN
                    EXECUTE format('CREATE TABLE seed_snapshot.%I AS TABLE public.%I', t, t);
                END IF;
            END LOOP;
        END ${'$'}${'$'};
        CREATE FUNCTION seed_snapshot.reset() RETURNS void LANGUAGE plpgsql AS ${'$'}${'$'}
        DECLARE
            tables text;
            seeded record;
            col record;
        BEGIN
            SELECT string_agg(format('public.%I', tablename), ', ') INTO tables
            FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history';
            EXECUTE 'TRUNCATE ' || tables || ' RESTART IDENTITY CASCADE';
            SET LOCAL session_replication_role = replica;
            FOR seeded IN SELECT tablename FROM pg_tables WHERE schemaname = 'seed_snapshot' LOOP
                EXECUTE format('INSERT INTO public.%I OVERRIDING SYSTEM VALUE SELECT * FROM seed_snapshot.%I',
                    seeded.tablename, seeded.tablename);
                FOR col IN
                    SELECT column_name, pg_get_serial_sequence(format('public.%I', seeded.tablename), column_name) AS sequence
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = seeded.tablename
                LOOP
                    IF col.sequence IS NOT NULL THEN
                        EXECUTE format('SELECT setval(%L, COALESCE((SELECT max(%I) FROM public.%I), 0) + 1, false)',
                            col.sequence, col.column_name, seeded.tablename);
                    END IF;
                END LOOP;
            END LOOP;
            SET LOCAL session_replication_role = origin;
        END ${'$'}${'$'};
    """.trimIndent()
}
