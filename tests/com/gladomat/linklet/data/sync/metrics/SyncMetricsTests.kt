package com.gladomat.linklet.data.sync.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SyncMetricsTests {

    @Test
    fun `metrics aggregates counts and durations`() {
        val metrics = InMemorySyncMetrics()

        metrics.increment(SyncMetricKeys.HTTP_PROPFIND)
        metrics.timing(SyncMetricKeys.STAGE_DISCOVERY_MS, 123)

        val snapshot = metrics.snapshot()
        assertEquals(1, snapshot.counts[SyncMetricKeys.HTTP_PROPFIND])
        assertEquals(123L, snapshot.timingsMs[SyncMetricKeys.STAGE_DISCOVERY_MS])
    }

    @Test
    fun `timing accumulates across multiple recordings`() {
        val metrics = InMemorySyncMetrics()

        metrics.timing(SyncMetricKeys.STAGE_DISCOVERY_MS, 50)
        metrics.timing(SyncMetricKeys.STAGE_DISCOVERY_MS, 75)

        val snapshot = metrics.snapshot()
        assertEquals(125L, snapshot.timingsMs[SyncMetricKeys.STAGE_DISCOVERY_MS])
    }

    @Test
    fun `snapshotAndReset returns current state and clears metrics`() {
        val metrics = InMemorySyncMetrics()

        metrics.increment(SyncMetricKeys.HTTP_PROPFIND)
        metrics.timing(SyncMetricKeys.STAGE_DISCOVERY_MS, 200)

        val first = metrics.snapshotAndReset()
        assertEquals(1, first.counts[SyncMetricKeys.HTTP_PROPFIND])
        assertEquals(200L, first.timingsMs[SyncMetricKeys.STAGE_DISCOVERY_MS])

        val second = metrics.snapshot()
        assertTrue(second.counts.isEmpty())
        assertTrue(second.timingsMs.isEmpty())
    }

    @Test
    fun `snapshotAndReset does not drop increments under concurrency`() {
        val metrics = InMemorySyncMetrics()
        val executor = Executors.newFixedThreadPool(6)
        val snapshots = ConcurrentLinkedQueue<SyncMetricsSnapshot>()
        val counter = SyncMetricKeys.HTTP_GET
        val increments = 10_000
        val resets = 300

        repeat(increments) {
            executor.submit { metrics.increment(counter) }
        }
        repeat(resets) {
            executor.submit { snapshots.add(metrics.snapshotAndReset()) }
        }

        executor.shutdown()
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))
        snapshots.add(metrics.snapshotAndReset())

        val total = snapshots.sumOf { it.counts[counter] ?: 0 }
        assertEquals(increments, total)
    }
}
