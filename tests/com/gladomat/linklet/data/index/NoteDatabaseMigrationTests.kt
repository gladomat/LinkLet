package com.gladomat.linklet.data.index

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NoteDatabaseMigrationTests {

    @Test
    fun `migration 5 to 6 adds queue leasing and link orgId columns`() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            NoteDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )
        val db = helper.createDatabase("migration-test", 5)
        db.execSQL("INSERT INTO links(source, target, alias) VALUES('a.org', 'b.org', NULL)")
        db.execSQL("INSERT INTO index_queue(path, pass, status, attempts) VALUES('a.org', 1, 'PENDING', 0)")
        db.close()

        val migrated = helper.runMigrationsAndValidate("migration-test", 6, true, NoteDatabase.MIGRATION_5_6)
        val linkColumns = tableColumns(migrated, "links")
        assertTrue(linkColumns.contains("sourceOrgId"))
        assertTrue(linkColumns.contains("targetOrgId"))

        val indexQueueColumns = tableColumns(migrated, "index_queue")
        assertTrue(indexQueueColumns.contains("operation"))
        assertTrue(indexQueueColumns.contains("lockedAtEpochMillis"))
        assertTrue(indexQueueColumns.contains("nextAttemptAtEpochMillis"))
        assertTrue(indexQueueColumns.contains("expectedHash"))
    }

    @Test
    fun `migration 6 to 7 adds availability and source columns to notes`() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            NoteDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )
        val db = helper.createDatabase("migration-test-6-7", 6)
        db.execSQL("INSERT INTO notes(path, title, fileTags, linksReady) VALUES('a.org', 'A', '', 0)")
        db.close()

        val migrated = helper.runMigrationsAndValidate("migration-test-6-7", 7, true, NoteDatabase.MIGRATION_6_7)
        val noteColumns = tableColumns(migrated, "notes")
        assertTrue(noteColumns.contains("availability"))
        assertTrue(noteColumns.contains("source"))

        migrated.query("SELECT availability, source FROM notes WHERE path='a.org'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("AVAILABLE", cursor.getString(0))
            assertEquals("LOCAL", cursor.getString(1))
        }
    }

    @Test
    fun `migration 7 to 8 creates sync snapshot and cache tables`() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            NoteDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )
        val db = helper.createDatabase("migration-test-7-8", 7)
        db.close()

        val migrated = helper.runMigrationsAndValidate("migration-test-7-8", 8, true, NoteDatabase.MIGRATION_7_8)
        val serverSnapshotColumns = tableColumns(migrated, "server_snapshot")
        assertTrue(serverSnapshotColumns.contains("rootId"))
        assertTrue(serverSnapshotColumns.contains("path"))
        assertTrue(serverSnapshotColumns.contains("isDir"))

        val syncWatermarkColumns = tableColumns(migrated, "sync_watermark")
        assertTrue(syncWatermarkColumns.contains("rootId"))
        assertTrue(syncWatermarkColumns.contains("lastDeltaServerTimeEpochMillis"))

        val capabilitiesColumns = tableColumns(migrated, "capabilities_cache")
        assertTrue(capabilitiesColumns.contains("rootId"))
        assertTrue(capabilitiesColumns.contains("supportsSearch"))
    }

    private fun tableColumns(database: SupportSQLiteDatabase, tableName: String): Set<String> {
        return database.query("PRAGMA table_info('$tableName')").use { cursor ->
            val nameColumnIndex = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(nameColumnIndex))
                }
            }
        }
    }
}
