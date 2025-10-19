package dev.jellystack.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DatabaseMigrationTest {
    private lateinit var driver: JdbcSqliteDriver

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun createIncludesSeriesColumns() {
        JellystackDatabase.Schema.create(driver)
        val columns = columnsFor("jellyfin_items")
        assertTrue(
            columns.containsAll(EXPECTED_SERIES_COLUMNS),
            "Schema.create should include latest series columns.",
        )
    }

    @Test
    fun migrateFromVersion1AddsSeriesColumns() {
        driver.execute(null, LEGACY_CREATE_ITEMS, 0)
        driver.execute(null, "PRAGMA user_version = 1", 0)

        JellystackDatabase.Schema.migrate(driver, 1, JellystackDatabase.Schema.version)

        val columns = columnsFor("jellyfin_items")
        assertTrue(
            columns.containsAll(EXPECTED_SERIES_COLUMNS),
            "Migration should add missing series-related columns.",
        )
    }

    private fun columnsFor(table: String): Set<String> =
        driver
            .executeQuery(
                identifier = null,
                sql = "PRAGMA table_info($table)",
                mapper = { cursor: SqlCursor ->
                    QueryResult.Value(
                        buildSet<String> {
                            while (cursor.next().value) {
                                add(requireNotNull(cursor.getString(1)))
                            }
                        },
                    )
                },
                parameters = 0,
            ).value

    private companion object {
        private val EXPECTED_SERIES_COLUMNS =
            setOf(
                "series_id",
                "series_primary_image_tag",
                "series_thumb_image_tag",
                "series_backdrop_image_tag",
                "parent_logo_image_tag",
            )

        private const val LEGACY_CREATE_ITEMS =
            """
            CREATE TABLE jellyfin_items (
                id TEXT NOT NULL PRIMARY KEY,
                server_id TEXT NOT NULL,
                library_id TEXT,
                name TEXT NOT NULL,
                sort_name TEXT,
                overview TEXT,
                type TEXT NOT NULL,
                media_type TEXT,
                taglines TEXT,
                parent_id TEXT,
                primary_image_tag TEXT,
                thumb_image_tag TEXT,
                backdrop_image_tag TEXT,
                run_time_ticks INTEGER,
                position_ticks INTEGER,
                played_percentage REAL,
                production_year INTEGER,
                premiere_date TEXT,
                community_rating REAL,
                official_rating TEXT,
                index_number INTEGER,
                parent_index_number INTEGER,
                series_name TEXT,
                season_id TEXT,
                episode_title TEXT,
                last_played TEXT,
                updated_at INTEGER NOT NULL
            );
            """
    }
}
