package com.gladomat.linklet.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull

private const val TAG = "WebDavSettings"
private const val WEB_DAV_PREFS_NAME = "webdav_secure_prefs"
private const val MASTER_KEY_ALIAS = "linklet_webdav_master_key"
private const val MASTER_KEYSET_PREFS = "androidx.security.crypto.master_keyset"
private const val ENCRYPTED_PREFS_KEYSET_PREFS = "androidx.security.crypto.encrypted_prefs_keyset"

data class WebDavSettings(
    val baseUrl: String,
    val rootPath: String,
    val username: String,
    val password: String,
    val enabled: Boolean,
    val lastSyncedRootPath: String? = null,
) {
    val normalizedRootPath: String
        get() = rootPath.trim().ifEmpty { "/" }

    fun isConfigured(): Boolean =
        baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

@Module
@InstallIn(SingletonComponent::class)
object WebDavSettingsModule {
    @Provides
    @Singleton
    @Named("webdav_prefs")
    fun provideWebDavSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return try {
            createEncryptedPrefs(context)
        } catch (error: Exception) {
            // Encrypted prefs can fail to open for several reasons: an AEAD tag mismatch
            // (AEADBadTagException) from a corrupted value, a corrupted Tink keyset, or the
            // AndroidKeyStore provider being unavailable/broken on this device or boot state
            // (KeyStoreException / GeneralSecurityException). This provider is built eagerly during
            // Application startup, so an uncaught failure here crashes the app on launch. Recover
            // instead: reset the keyset and recreate once; if that still fails, fall back to a
            // non-persistent in-memory store so the app stays usable (credentials simply aren't
            // remembered and must be re-entered — we never write them to disk unencrypted).
            Log.e(TAG, "Encrypted WebDAV prefs unavailable (${error.javaClass.simpleName}); attempting reset", error)
            runCatching {
                resetEncryptedPreferences(context)
                createEncryptedPrefs(context).also { fresh ->
                    fresh.edit().putBoolean(WebDavSettingsRepository.Keys.RESET_DUE_TO_ERROR, true).apply()
                }
            }.getOrElse { retryError ->
                Log.e(TAG, "Encrypted WebDAV prefs unrecoverable; using in-memory fallback", retryError)
                InMemorySharedPreferences().also { mem ->
                    mem.edit().putBoolean(WebDavSettingsRepository.Keys.RESET_DUE_TO_ERROR, true).apply()
                }
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            WEB_DAV_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun resetEncryptedPreferences(context: Context) {
        // 1. Delete the encrypted prefs file
        context.deleteSharedPreferences(WEB_DAV_PREFS_NAME)

        // 2. Clear the Tink keyset storage used by EncryptedSharedPreferences
        context.getSharedPreferences(MASTER_KEYSET_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        context.getSharedPreferences(ENCRYPTED_PREFS_KEYSET_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}

@Singleton
class WebDavSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    @Named("webdav_prefs") private val sharedPreferences: SharedPreferences,
) {

    internal object Keys {
        const val BASE_URL = "webdav_base_url"
        const val ROOT_PATH = "webdav_root_path"
        const val USERNAME = "webdav_username"
        const val PASSWORD = "webdav_password"
        const val ENABLED = "webdav_enabled"
        const val LAST_SYNCED_ROOT_PATH = "webdav_last_synced_root_path"
        const val RESET_DUE_TO_ERROR = "webdav_reset_due_to_error"
        const val INITIAL_SYNC_COMPLETED_PREFIX = "webdav_initial_sync_completed_"
    }

    val settingsFlow: Flow<WebDavSettings?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readSettings())
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(readSettings())
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * Returns true if encrypted WebDAV settings were reset due to a crypto error.
     * The flag is cleared after being read once.
     */
    fun consumeResetDueToErrorFlag(): Boolean {
        val wasReset = sharedPreferences.getBoolean(Keys.RESET_DUE_TO_ERROR, false)
        if (wasReset) {
            sharedPreferences.edit()
                .putBoolean(Keys.RESET_DUE_TO_ERROR, false)
                .apply()
        }
        return wasReset
    }

    private fun readSettings(): WebDavSettings? {
        val baseUrl = sharedPreferences.getString(Keys.BASE_URL, null) ?: return null
        val username = sharedPreferences.getString(Keys.USERNAME, null) ?: return null
        val password = sharedPreferences.getString(Keys.PASSWORD, null) ?: return null
        val root = sharedPreferences.getString(Keys.ROOT_PATH, "/") ?: "/"
        val enabled = sharedPreferences.getBoolean(Keys.ENABLED, false)
        val lastSyncedPath = sharedPreferences.getString(Keys.LAST_SYNCED_ROOT_PATH, null)

        return WebDavSettings(
            baseUrl = baseUrl,
            rootPath = root,
            username = username,
            password = password,
            enabled = enabled,
            lastSyncedRootPath = lastSyncedPath,
        )
    }

    suspend fun save(settings: WebDavSettings) {
        sharedPreferences.edit()
            .putString(Keys.BASE_URL, settings.baseUrl)
            .putString(Keys.ROOT_PATH, settings.rootPath)
            .putString(Keys.USERNAME, settings.username)
            .putString(Keys.PASSWORD, settings.password)
            .putBoolean(Keys.ENABLED, settings.enabled)
            .apply()
    }

    suspend fun setEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(Keys.ENABLED, enabled)
            .apply()
    }

    suspend fun clear() {
        val editor = sharedPreferences.edit()
            .remove(Keys.BASE_URL)
            .remove(Keys.ROOT_PATH)
            .remove(Keys.USERNAME)
            .remove(Keys.PASSWORD)
            .remove(Keys.ENABLED)
            .remove(Keys.LAST_SYNCED_ROOT_PATH)
        
        // Remove all initial sync completed flags
        sharedPreferences.all.keys
            .filter { it.startsWith(Keys.INITIAL_SYNC_COMPLETED_PREFIX) }
            .forEach { editor.remove(it) }
            
        editor.apply()
    }

    suspend fun currentSettings(): WebDavSettings? = settingsFlow.firstOrNull()

    suspend fun updateLastSyncedPath(path: String) {
        sharedPreferences.edit()
            .putString(Keys.LAST_SYNCED_ROOT_PATH, path)
            .apply()
    }

    fun hasCompletedInitialSync(rootPath: String): Boolean {
        return sharedPreferences.getBoolean(Keys.INITIAL_SYNC_COMPLETED_PREFIX + rootPath, false)
    }

    fun markInitialSyncCompleted(rootPath: String) {
        sharedPreferences.edit()
            .putBoolean(Keys.INITIAL_SYNC_COMPLETED_PREFIX + rootPath, true)
            .apply()
    }

    fun resetInitialSyncCompleted() {
        val editor = sharedPreferences.edit()
        sharedPreferences.all.keys
            .filter { it.startsWith(Keys.INITIAL_SYNC_COMPLETED_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
    }
}

/**
 * Minimal in-process [SharedPreferences] used only as a last-resort fallback when encrypted
 * preferences cannot be opened (see [WebDavSettingsModule.provideWebDavSharedPreferences]).
 *
 * It intentionally does NOT persist to disk: WebDAV credentials must never be written unencrypted,
 * so when AndroidKeyStore is unavailable we keep them in memory for the process lifetime only. The
 * user re-enters credentials on next launch; sync still works for the current session. It honours
 * change listeners so the settings flow keeps working.
 */
internal class InMemorySharedPreferences : SharedPreferences {
    private val values = java.util.concurrent.ConcurrentHashMap<String, Any?>()
    private val listeners =
        java.util.Collections.newSetFromMap(
            java.util.concurrent.ConcurrentHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Boolean>(),
        )

    override fun getAll(): MutableMap<String, *> = HashMap(values)

    override fun getString(key: String?, defValue: String?): String? =
        (values[key] as? String) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = (values[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (values[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (values[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (values[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listener?.let { listeners.add(it) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listener?.let { listeners.remove(it) }
    }

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    private fun notifyChange(keys: Collection<String>) {
        for (l in listeners) {
            for (k in keys) l.onSharedPreferenceChanged(this, k)
        }
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>() // value, or REMOVED sentinel
        private var clear = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = REMOVED }
        override fun clear() = apply { clear = true }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            val changed = LinkedHashSet<String>()
            if (clear) {
                changed.addAll(values.keys)
                values.clear()
            }
            for ((k, v) in pending) {
                if (v === REMOVED) {
                    if (values.remove(k) != null) changed.add(k)
                } else {
                    values[k] = v
                    changed.add(k)
                }
            }
            if (changed.isNotEmpty()) notifyChange(changed)
        }
    }

    private companion object {
        val REMOVED = Any()
    }
}