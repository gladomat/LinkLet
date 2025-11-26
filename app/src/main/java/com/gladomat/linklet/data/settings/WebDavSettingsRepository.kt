package com.gladomat.linklet.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

data class WebDavSettings(
    val baseUrl: String,
    val rootPath: String,
    val username: String,
    val password: String,
    val enabled: Boolean,
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
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "webdav_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}

@Singleton
class WebDavSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    @Named("webdav_prefs") private val sharedPreferences: SharedPreferences,
) {

    private object Keys {
        const val BASE_URL = "webdav_base_url"
        const val ROOT_PATH = "webdav_root_path"
        const val USERNAME = "webdav_username"
        const val PASSWORD = "webdav_password"
        const val ENABLED = "webdav_enabled"
    }

    val settingsFlow: Flow<WebDavSettings?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readSettings())
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(readSettings())
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun readSettings(): WebDavSettings? {
        val baseUrl = sharedPreferences.getString(Keys.BASE_URL, null) ?: return null
        val username = sharedPreferences.getString(Keys.USERNAME, null) ?: return null
        val password = sharedPreferences.getString(Keys.PASSWORD, null) ?: return null
        val root = sharedPreferences.getString(Keys.ROOT_PATH, "/") ?: "/"
        val enabled = sharedPreferences.getBoolean(Keys.ENABLED, false)

        return WebDavSettings(
            baseUrl = baseUrl,
            rootPath = root,
            username = username,
            password = password,
            enabled = enabled,
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
        sharedPreferences.edit()
            .remove(Keys.BASE_URL)
            .remove(Keys.ROOT_PATH)
            .remove(Keys.USERNAME)
            .remove(Keys.PASSWORD)
            .remove(Keys.ENABLED)
            .apply()
    }

    suspend fun currentSettings(): WebDavSettings? = settingsFlow.firstOrNull()
}
