package com.gladomat.linklet.data.index

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        migrated.query("PRAGMA table_info('links')").use { cursor ->
            val names = cursor.columnNames.toSet()
            assertTrue(names.contains("sourceOrgId"))
            assertTrue(names.contains("targetOrgId"))
        }
        migrated.query("PRAGMA table_info('index_queue')").use { cursor ->
            val names = cursor.columnNames.toSet()
            assertTrue(names.contains("operation"))
            assertTrue(names.contains("lockedAtEpochMillis"))
            assertTrue(names.contains("nextAttemptAtEpochMillis"))
            assertTrue(names.contains("expectedHash"))
        }
    }
}
