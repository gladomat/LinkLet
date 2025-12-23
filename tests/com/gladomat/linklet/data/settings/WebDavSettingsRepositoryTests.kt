package com.gladomat.linklet.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.settings.WebDavSettingsRepository.Keys
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WebDavSettingsRepositoryTests {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `consumeResetDueToErrorFlag returns true once then clears flag`() {
        val prefs = context.getSharedPreferences("webdav_test_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Keys.RESET_DUE_TO_ERROR, true).apply()

        val repository = WebDavSettingsRepository(context, prefs)

        assertTrue(repository.consumeResetDueToErrorFlag())
        assertFalse(repository.consumeResetDueToErrorFlag())
    }

    @Test
    fun `initial sync completion is tracked per root path`() {
        val prefs = context.getSharedPreferences("webdav_test_prefs_initial_sync", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        val repository = WebDavSettingsRepository(context, prefs)

        assertFalse(repository.hasCompletedInitialSync(rootPath = "/Roam"))

        repository.markInitialSyncCompleted(rootPath = "/Roam")

        assertTrue(repository.hasCompletedInitialSync(rootPath = "/Roam"))
        assertFalse(repository.hasCompletedInitialSync(rootPath = "/Other"))

        repository.resetInitialSyncCompleted()

        assertFalse(repository.hasCompletedInitialSync(rootPath = "/Roam"))
    }
}
