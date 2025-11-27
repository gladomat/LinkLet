package com.gladomat.linklet.data.index

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gladomat.linklet.data.sync.SyncStateEntity
import com.gladomat.linklet.data.sync.SyncStateTypeConverters

@Database(
    entities = [NoteEntity::class, LinkEntity::class, SyncStateEntity::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(SyncStateTypeConverters::class)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Destructive migration for SyncState to ensure fresh-install logic kicks in
                // with the new schema (lifecycle, hashes, etc.)
                database.execSQL("DROP TABLE IF EXISTS `sync_state`")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_state` (
                        `path` TEXT NOT NULL,
                        `lifecycle` TEXT NOT NULL DEFAULT 'ACTIVE',
                        `remoteId` TEXT,
                        `remoteFingerprint` TEXT,
                        `localContentHash` TEXT,
                        `lastKnownHash` TEXT,
                        `remoteETag` TEXT,
                        `lastSyncedAtEpochMillis` INTEGER,
                        `deletedAt` INTEGER,
                        `pendingAction` TEXT NOT NULL DEFAULT 'NONE',
                        `lastError` TEXT,
                        PRIMARY KEY(`path`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `sync_state` ADD COLUMN `base_content` TEXT")
            }
        }
    }
}
