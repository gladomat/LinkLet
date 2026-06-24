package com.gladomat.linklet.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gladomat.linklet.data.settings.WebDavSettingsRepository.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.gladomat.linklet.testing.Aarch64RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(Aarch64RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WebDavSettingsRepositoryTests {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `provider does not crash and returns usable prefs when AndroidKeyStore is unavailable`() {
        // Under Robolectric the AndroidKeyStore provider is absent, so EncryptedSharedPreferences
        // creation throws KeyStoreException. Before the hardening this propagated out of the Hilt
        // provider and crashed the app on launch (DEF-003). Now it must degrade to a usable store.
        val prefs = WebDavSettingsModule.provideWebDavSharedPreferences(context)

        assertNotNull(prefs)
        // The fallback flags that encrypted settings were reset/degraded so the UI can warn the user.
        assertTrue(prefs.getBoolean(Keys.RESET_DUE_TO_ERROR, false))

        // And it must behave as a working SharedPreferences for the repository.
        val repo = WebDavSettingsRepository(context, prefs)
        repo.markInitialSyncCompleted("/Roam")
        assertTrue(repo.hasCompletedInitialSync("/Roam"))
        assertFalse(repo.hasCompletedInitialSync("/Other"))
        assertEquals(true, prefs.getBoolean(Keys.INITIAL_SYNC_COMPLETED_PREFIX + "/Roam", false))
    }

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
