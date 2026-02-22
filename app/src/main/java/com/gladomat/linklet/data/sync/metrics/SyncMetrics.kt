package com.gladomat.linklet.data.sync.metrics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

interface SyncMetrics {
    fun increment(counter: String)

    fun timing(metric: String, durationMs: Long)

    fun snapshot(): SyncMetricsSnapshot
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
            (existing ?: AtomicLong(0)).apply { set(durationMs) }
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
}

object NoOpSyncMetrics : SyncMetrics {
    override fun increment(counter: String) = Unit

    override fun timing(metric: String, durationMs: Long) = Unit

    override fun snapshot(): SyncMetricsSnapshot = SyncMetricsSnapshot(
        counts = emptyMap(),
        timingsMs = emptyMap(),
    )
}

object SyncMetricsRegistry {
    @Volatile
    var instance: SyncMetrics = InMemorySyncMetrics()
}
