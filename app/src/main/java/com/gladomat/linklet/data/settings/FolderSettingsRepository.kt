package com.gladomat.linklet.data.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.folderSettingsDataStore by preferencesDataStore(name = "folder_settings")

@Singleton
class FolderSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dataStore = context.folderSettingsDataStore

    private object Keys {
        val FOLDER_URI: Preferences.Key<String> = stringPreferencesKey("folder_uri")
    }

    val folderUriFlow: Flow<Uri?> = dataStore.data.map { prefs ->
        prefs[Keys.FOLDER_URI]?.let(Uri::parse)
    }

    suspend fun setFolderUri(uri: Uri) {
        dataStore.edit { settings ->
            settings[Keys.FOLDER_URI] = uri.toString()
        }
    }

    suspend fun clearFolderUri() {
        dataStore.edit { settings ->
            settings.remove(Keys.FOLDER_URI)
        }
    }

    suspend fun currentFolderUri(): Uri? = folderUriFlow.firstOrNull()
}
