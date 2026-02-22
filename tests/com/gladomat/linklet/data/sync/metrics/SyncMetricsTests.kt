package com.gladomat.linklet.data.sync.metrics

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMetricsTests {

    @Test
    fun `metrics aggregates counts and durations`() {
        val metrics = InMemorySyncMetrics()

        metrics.increment("http_PROPFIND")
        metrics.timing("stage_discovery_ms", 123)

        val snapshot = metrics.snapshot()
        assertEquals(1, snapshot.counts["http_PROPFIND"])
        assertEquals(123L, snapshot.timingsMs["stage_discovery_ms"])
    }
}
