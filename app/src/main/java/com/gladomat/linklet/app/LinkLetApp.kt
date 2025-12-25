package com.gladomat.linklet.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.gladomat.linklet.data.settings.SyncSettingsRepository
import com.gladomat.linklet.data.settings.WebDavSettingsRepository
import com.gladomat.linklet.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LinkLetApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var webDavSettingsRepository: WebDavSettingsRepository

    @Inject
    lateinit var syncSettingsRepository: SyncSettingsRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Log.i("LinkLetApp", "Application onCreate() - workerFactory will be injected by Hilt")
        setupPeriodicSync()
    }

    private fun setupPeriodicSync() {
        applicationScope.launch {
            // Observe both WebDAV and sync settings
            combine(
                webDavSettingsRepository.settingsFlow,
                syncSettingsRepository.periodicSyncEnabledFlow,
                syncSettingsRepository.syncIntervalMinutesFlow
            ) { webDavSettings, periodicSyncEnabled, syncIntervalMinutes ->
                Triple(webDavSettings, periodicSyncEnabled, syncIntervalMinutes)
            }.collect { (webDavSettings, periodicSyncEnabled, syncIntervalMinutes) ->
                val webDavReady = webDavSettings?.enabled == true && webDavSettings.isConfigured()
                
                if (webDavReady && periodicSyncEnabled) {
                    Log.i("LinkLetApp", "Scheduling periodic sync with interval: $syncIntervalMinutes minutes")
                    syncScheduler.schedulePeriodic(syncIntervalMinutes)
                } else {
                    Log.i("LinkLetApp", "Cancelling periodic sync (webDavReady=$webDavReady, periodicSyncEnabled=$periodicSyncEnabled)")
                    syncScheduler.cancelPeriodic()
                }
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }

    override val workManagerConfiguration: Configuration
        get() {
            Log.i("LinkLetApp", "getWorkManagerConfiguration() called - providing HiltWorkerFactory: $workerFactory")
            return Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setWorkerFactory(workerFactory)
                .build()
        }
}
