package com.gladomat.linklet.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.syncSettingsDataStore by preferencesDataStore(name = "sync_settings")

@Singleton
class SyncSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dataStore = context.syncSettingsDataStore

    private object Keys {
        val PERIODIC_SYNC_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("periodic_sync_enabled")
        val SYNC_INTERVAL_MINUTES: Preferences.Key<Long> = longPreferencesKey("sync_interval_minutes")
    }

    val periodicSyncEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.PERIODIC_SYNC_ENABLED] ?: true
    }

    val syncIntervalMinutesFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.SYNC_INTERVAL_MINUTES] ?: 60L
    }

    suspend fun setPeriodicSyncEnabled(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[Keys.PERIODIC_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setSyncIntervalMinutes(minutes: Long) {
        dataStore.edit { settings ->
            settings[Keys.SYNC_INTERVAL_MINUTES] = minutes
        }
    }

    suspend fun currentPeriodicSyncEnabled(): Boolean = periodicSyncEnabledFlow.firstOrNull() ?: true

    suspend fun currentSyncIntervalMinutes(): Long = syncIntervalMinutesFlow.firstOrNull() ?: 60L
}
