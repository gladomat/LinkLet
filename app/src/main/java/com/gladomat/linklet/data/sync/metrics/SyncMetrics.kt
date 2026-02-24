package com.gladomat.linklet.data.sync.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

interface SyncMetrics {
    fun increment(counter: String)

    fun timing(metric: String, durationMs: Long)

    fun snapshot(): SyncMetricsSnapshot

    /** Returns the current snapshot and resets all counters and timings to zero. */
    fun snapshotAndReset(): SyncMetricsSnapshot
}

object SyncMetricKeys {
    const val HTTP_GET = "http_GET"
    const val HTTP_PUT = "http_PUT"
    const val HTTP_DELETE = "http_DELETE"
    const val HTTP_PROPFIND = "http_PROPFIND"

    const val STAGE_DISCOVERY_MS = "stage_discovery_ms"
    const val STAGE_RECONCILE_MS = "stage_reconcile_ms"
    const val STAGE_EXECUTE_MS = "stage_execute_ms"
}

data class SyncMetricsSnapshot(
    val counts: Map<String, Int>,
    val timingsMs: Map<String, Long>,
)

class InMemorySyncMetrics : SyncMetrics {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val timingsMs = ConcurrentHashMap<String, AtomicLong>()

    override fun increment(counter: String) {
        counts.computeIfAbsent(counter) { AtomicInteger(0) }.incrementAndGet()
    }

    override fun timing(metric: String, durationMs: Long) {
        timingsMs.compute(metric) { _, existing ->
            (existing ?: AtomicLong(0)).apply { addAndGet(durationMs) }
        }
    }

    override fun snapshot(): SyncMetricsSnapshot {
        val countSnapshot = counts.entries.associate { (name, value) -> name to value.get() }
        val timingSnapshot = timingsMs.entries.associate { (name, value) -> name to value.get() }
        return SyncMetricsSnapshot(
            counts = countSnapshot,
            timingsMs = timingSnapshot,
        )
    }

    override fun snapshotAndReset(): SyncMetricsSnapshot {
        val result = snapshot()
        counts.clear()
        timingsMs.clear()
        return result
    }
}

object NoOpSyncMetrics : SyncMetrics {
    override fun increment(counter: String) = Unit

    override fun timing(metric: String, durationMs: Long) = Unit

    override fun snapshot(): SyncMetricsSnapshot = SyncMetricsSnapshot(
        counts = emptyMap(),
        timingsMs = emptyMap(),
    )

    override fun snapshotAndReset(): SyncMetricsSnapshot = snapshot()
}
