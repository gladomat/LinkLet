package com.gladomat.linklet.data.index

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gladomat.linklet.data.sync.db.CapabilitiesCacheDao
import com.gladomat.linklet.data.sync.db.CapabilitiesCacheEntity
import com.gladomat.linklet.data.sync.db.ServerSnapshotDao
import com.gladomat.linklet.data.sync.db.ServerSnapshotEntity
import com.gladomat.linklet.data.sync.db.SyncWatermarkDao
import com.gladomat.linklet.data.sync.db.SyncWatermarkEntity
import com.gladomat.linklet.data.sync.SyncStateEntity
import com.gladomat.linklet.data.sync.SyncStateTypeConverters

@Database(
    entities = [
        NoteEntity::class,
        LinkEntity::class,
        SyncStateEntity::class,
        IndexQueueEntity::class,
        IndexingStateEntity::class,
        ServerSnapshotEntity::class,
        SyncWatermarkEntity::class,
        CapabilitiesCacheEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
@TypeConverters(SyncStateTypeConverters::class, IndexTypeConverters::class)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun indexQueueDao(): IndexQueueDao
    abstract fun indexingStateDao(): IndexingStateDao
    abstract fun serverSnapshotDao(): ServerSnapshotDao
    abstract fun syncWatermarkDao(): SyncWatermarkDao
    abstract fun capabilitiesCacheDao(): CapabilitiesCacheDao

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `notes` ADD COLUMN `orgId` TEXT")
                database.execSQL("ALTER TABLE `notes` ADD COLUMN `fileTags` TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE `notes` ADD COLUMN `deletedAt` INTEGER")
                database.execSQL("ALTER TABLE `notes` ADD COLUMN `fingerprintMtime` INTEGER")
                database.execSQL("ALTER TABLE `notes` ADD COLUMN `fingerprintSize` INTEGER")
                database.execSQL("ALTER TABLE `notes` ADD COLUMN `linksReady` INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `index_queue` (
                        `path` TEXT NOT NULL,
                        `pass` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `attempts` INTEGER NOT NULL,
                        `lastError` TEXT,
                        `updatedAtEpochMillis` INTEGER,
                        `expectedMtime` INTEGER,
                        `expectedSize` INTEGER,
                        PRIMARY KEY(`path`, `pass`)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `links` ADD COLUMN `sourceOrgId` TEXT")
                database.execSQL("ALTER TABLE `links` ADD COLUMN `targetOrgId` TEXT")
                database.execSQL("ALTER TABLE `index_queue` ADD COLUMN `operation` TEXT NOT NULL DEFAULT 'UPSERT'")
                database.execSQL("ALTER TABLE `index_queue` ADD COLUMN `lockedAtEpochMillis` INTEGER")
                database.execSQL("ALTER TABLE `index_queue` ADD COLUMN `nextAttemptAtEpochMillis` INTEGER")
                database.execSQL("ALTER TABLE `index_queue` ADD COLUMN `expectedHash` TEXT")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `indexing_state` (
                        `id` INTEGER NOT NULL,
                        `lastScanAtEpochMillis` INTEGER,
                        `lastPass1EnqueueAtEpochMillis` INTEGER,
                        `lastPass2RunAtEpochMillis` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `notes` ADD COLUMN `availability` TEXT NOT NULL DEFAULT 'AVAILABLE'")
                database.execSQL("ALTER TABLE `notes` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'LOCAL'")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `server_snapshot` (
                        `rootId` TEXT NOT NULL,
                        `path` TEXT NOT NULL,
                        `href` TEXT NOT NULL,
                        `etag` TEXT,
                        `isDir` INTEGER NOT NULL,
                        `fileId` TEXT,
                        `lastModifiedEpochMillis` INTEGER,
                        `sizeBytes` INTEGER,
                        `deletedAtEpochMillis` INTEGER,
                        `lastSeenAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`rootId`, `path`)
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_server_snapshot_rootId_isDir_etag` ON `server_snapshot` (`rootId`, `isDir`, `etag`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_server_snapshot_rootId_fileId` ON `server_snapshot` (`rootId`, `fileId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_server_snapshot_rootId_deletedAtEpochMillis` ON `server_snapshot` (`rootId`, `deletedAtEpochMillis`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_server_snapshot_rootId_lastSeenAtEpochMillis` ON `server_snapshot` (`rootId`, `lastSeenAtEpochMillis`)")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_watermark` (
                        `rootId` TEXT NOT NULL,
                        `lastFullSweepServerTimeEpochMillis` INTEGER,
                        `lastDeltaServerTimeEpochMillis` INTEGER,
                        `deltaValidityState` TEXT,
                        `deltaValidityReason` TEXT,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`rootId`)
                    )
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `capabilities_cache` (
                        `rootId` TEXT NOT NULL,
                        `supportsSearch` INTEGER NOT NULL,
                        `supportsTrashbin` INTEGER NOT NULL,
                        `supportsFileId` INTEGER NOT NULL,
                        `checkedAtEpochMillis` INTEGER NOT NULL,
                        `expiresAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`rootId`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
