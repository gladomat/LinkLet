package com.gladomat.linklet.data.sync.metrics

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
