package com.gladomat.linklet.data.sync.worker

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.sync.SyncStatusNavigation
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import android.app.PendingIntent
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SyncWorkerNotificationsTests {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `build status notification includes review intent and big text`() {
        val notification = SyncWorkerNotifications.buildStatus(
            context = context,
            title = "Sync blocked",
            text = "Directory changed",
            navTarget = SyncStatusNavigation.NAV_TARGET_SYNC_STATUS,
        )

        val contentIntent = notification.contentIntent
        assertNotNull(contentIntent)

        val savedIntent = Shadows.shadowOf(contentIntent as PendingIntent).savedIntent
        assertEquals(
            SyncStatusNavigation.NAV_TARGET_SYNC_STATUS,
            savedIntent.getStringExtra(SyncStatusNavigation.EXTRA_NAV_TARGET),
        )

        val bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        assertEquals("Directory changed", bigText?.toString())
    }
}
