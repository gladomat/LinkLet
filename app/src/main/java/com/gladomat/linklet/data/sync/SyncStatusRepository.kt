package com.gladomat.linklet.data.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.syncStatusDataStore by preferencesDataStore(name = "sync_status")

/**
 * Stores the most recent actionable sync status for notifications and UI.
 */
@Singleton
class SyncStatusRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dataStore = context.syncStatusDataStore

    private object Keys {
        val TYPE = stringPreferencesKey("type")
        val TITLE = stringPreferencesKey("title")
        val MESSAGE = stringPreferencesKey("message")
        val OLD_PATH = stringPreferencesKey("old_path")
        val NEW_PATH = stringPreferencesKey("new_path")
        val REQUIRES_ACTION = booleanPreferencesKey("requires_action")
        val UPDATED_AT = longPreferencesKey("updated_at")
    }

    val statusFlow: Flow<SyncStatus?> = dataStore.data.map { prefs ->
        val typeRaw = prefs[Keys.TYPE] ?: return@map null
        val type = runCatching { SyncStatusType.valueOf(typeRaw) }.getOrNull() ?: return@map null
        SyncStatus(
            type = type,
            title = prefs[Keys.TITLE] ?: "",
            message = prefs[Keys.MESSAGE] ?: "",
            oldPath = prefs[Keys.OLD_PATH],
            newPath = prefs[Keys.NEW_PATH],
            requiresAction = prefs[Keys.REQUIRES_ACTION] ?: false,
            updatedAtEpochMillis = prefs[Keys.UPDATED_AT] ?: 0L,
        )
    }

    /**
     * Persist the latest sync status for UI and notifications.
     */
    suspend fun setStatus(status: SyncStatus) {
        dataStore.edit { prefs ->
            prefs[Keys.TYPE] = status.type.name
            prefs[Keys.TITLE] = status.title
            prefs[Keys.MESSAGE] = status.message
            status.oldPath?.let { prefs[Keys.OLD_PATH] = it } ?: prefs.remove(Keys.OLD_PATH)
            status.newPath?.let { prefs[Keys.NEW_PATH] = it } ?: prefs.remove(Keys.NEW_PATH)
            prefs[Keys.REQUIRES_ACTION] = status.requiresAction
            prefs[Keys.UPDATED_AT] = status.updatedAtEpochMillis
        }
    }

    /**
     * Clears any stored sync status.
     */
    suspend fun clearStatus() {
        dataStore.edit { prefs -> prefs.clear() }
    }
}
