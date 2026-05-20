package com.gladomat.linklet.data.sync.gc

import com.gladomat.linklet.data.sync.db.ServerSnapshotDao
import com.gladomat.linklet.data.sync.db.ServerSnapshotEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * In-memory fake of [ServerSnapshotDao] for unit testing tombstone GC logic.
 */
private class FakeServerSnapshotDao : ServerSnapshotDao {
    val rows = mutableListOf<ServerSnapshotEntity>()

    override suspend fun upsertAll(items: List<ServerSnapshotEntity>) {
        for (item in items) upsert(item)
    }

    override suspend fun upsert(item: ServerSnapshotEntity) {
        rows.removeAll { it.rootId == item.rootId && it.path == item.path }
        rows.add(item)
    }

    override suspend fun getByPath(rootId: String, path: String): ServerSnapshotEntity? =
        rows.find { it.rootId == rootId && it.path == path }

    override suspend fun clearRoot(rootId: String) {
        rows.removeAll { it.rootId == rootId }
    }

    override suspend fun getAllFiles(rootId: String): List<ServerSnapshotEntity> =
        rows.filter { it.rootId == rootId && !it.isDir }

    override suspend fun purgeTombstones(rootId: String, cutoffEpochMillis: Long): Int {
        val before = rows.size
        rows.removeAll {
            it.rootId == rootId &&
                it.deletedAtEpochMillis != null &&
                it.deletedAtEpochMillis!! < cutoffEpochMillis
        }
        return before - rows.size
    }

    override suspend fun findExpiredTombstones(
        rootId: String,
        cutoffEpochMillis: Long,
    ): List<ServerSnapshotEntity> = rows.filter {
        it.rootId == rootId &&
            it.deletedAtEpochMillis != null &&
            it.deletedAtEpochMillis!! < cutoffEpochMillis
    }
}

class TombstoneGcTests {

    private val rootId = "root-1"

    private fun entity(
        path: String,
        deletedAt: Long? = null,
        lastSeen: Long = 1000L,
    ) = ServerSnapshotEntity(
        rootId = rootId,
        path = path,
        href = "/$path",
        etag = "etag-$path",
        isDir = false,
        fileId = null,
        deletedAtEpochMillis = deletedAt,
        lastSeenAtEpochMillis = lastSeen,
    )

    @Test
    fun `tombstones older than TTL are purged`() = runTest {
        val dao = FakeServerSnapshotDao()
        val now = 100_000L
        val ttl = 10_000L
        // deleted at 80_000 => cutoff = 90_000, 80_000 < 90_000 => purged
        dao.upsert(entity("old.org", deletedAt = 80_000L))

        val gc = TombstoneGc(dao)
        val purged = gc.purge(rootId, now, ttl)

        assertEquals(1, purged)
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun `tombstones newer than TTL are kept`() = runTest {
        val dao = FakeServerSnapshotDao()
        val now = 100_000L
        val ttl = 10_000L
        // deleted at 95_000 => cutoff = 90_000, 95_000 >= 90_000 => kept
        dao.upsert(entity("recent.org", deletedAt = 95_000L))

        val gc = TombstoneGc(dao)
        val purged = gc.purge(rootId, now, ttl)

        assertEquals(0, purged)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `non-tombstones are not affected`() = runTest {
        val dao = FakeServerSnapshotDao()
        val now = 100_000L
        val ttl = 10_000L
        dao.upsert(entity("alive.org", deletedAt = null))

        val gc = TombstoneGc(dao)
        val purged = gc.purge(rootId, now, ttl)

        assertEquals(0, purged)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `returns correct count of purged entries`() = runTest {
        val dao = FakeServerSnapshotDao()
        val now = 100_000L
        val ttl = 10_000L
        // Two old tombstones, one recent tombstone, one alive
        dao.upsert(entity("old1.org", deletedAt = 70_000L))
        dao.upsert(entity("old2.org", deletedAt = 80_000L))
        dao.upsert(entity("recent.org", deletedAt = 95_000L))
        dao.upsert(entity("alive.org", deletedAt = null))

        val gc = TombstoneGc(dao)
        val purged = gc.purge(rootId, now, ttl)

        assertEquals(2, purged)
        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `empty table returns 0`() = runTest {
        val dao = FakeServerSnapshotDao()

        val gc = TombstoneGc(dao)
        val purged = gc.purge(rootId, 100_000L, 10_000L)

        assertEquals(0, purged)
    }
}
