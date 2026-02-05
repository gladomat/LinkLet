package com.gladomat.linklet.data.sync.worker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundServiceTypeConfigTests {

    @Test
    fun `sync worker sets foreground service type`() {
        val source = File("src/main/java/com/gladomat/linklet/data/sync/worker/SyncWorker.kt").readText()
        assertTrue(
            "SyncWorker should set a foreground service type",
            source.contains("FOREGROUND_SERVICE_TYPE_DATA_SYNC"),
        )
    }

    @Test
    fun `workmanager foreground service declares dataSync type`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "Manifest should declare SystemForegroundService",
            manifest.contains("androidx.work.impl.foreground.SystemForegroundService"),
        )
        assertTrue(
            "Manifest should declare dataSync foreground service type",
            manifest.contains("foregroundServiceType=\"dataSync\""),
        )
    }
}
